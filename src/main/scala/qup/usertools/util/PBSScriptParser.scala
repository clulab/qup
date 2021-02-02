package main.scala.qup.usertools.util

import main.scala.qup.scheduler.Configuration
import main.scala.qup.struct.{NodeResource, ScheduledJob}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

object PBSScriptParser {

  // Errors, Warnings, Parsed Options
  def parsePBSScript(filename:String):(Array[String], Array[String], Map[String, String]) = {
    val out = mutable.Map[String, String]()
    val errors = new ArrayBuffer[String]
    val warnings = new ArrayBuffer[String]

    // Default values
    out("jobName") = "not specified"
    out("projectCode") = "not specified"
    out("preemptable") = "false"
    out("outputMode") = ScheduledJob.OUTPUT_MODE_BOTH
    out("walltime") = Configuration.defaultWallTimeLimit

    // Load file
    var lines:Iterator[String] = null
    try {
      lines = io.Source.fromFile(filename, "UTF-8").getLines()
    } catch {
      case e:Throwable => {
        println(e.toString)
        sys.exit(1)
      }
    }

    // Parse file line-by-line
    for (line <- lines) {
      //println(line)
      if (line.startsWith("#PBS -N ")) {
        // Job name
        out("jobName") = line.substring(7).trim()

      } else if (line.startsWith("#PBS -A ")) {
        // Project code
        out("projectCode") = line.substring(7).trim()

      } else if (line.startsWith("#PBS -l ")) {
        // Unspecified option
        val optStr = line.substring(7).trim()

        // Case 1: Check that the number of ":"+1 and "="'s match -- if they do, parse as normal
        if (optStr.count(_ == '=') == optStr.count(_ == ':')+1) {
          val fields = optStr.split(":")
          for (field <- fields) {
            try {
              val kvp = field.split("=")
              val key = kvp(0).trim()
              val value = kvp(1).trim()
              out(key) = value
            } catch {
              case _: Throwable => {
                errors.append("ERROR Parsing line: " + line)
              }
            }
          }
        } else {
          // Case 2: = and : counts do not match -- likely a single time element here on a single line. Treat the whole thing as a big argument.
          try {
            val kvp = optStr.split("=")
            val key = kvp(0).trim()
            val value = kvp(1).trim()
            out(key) = value
          } catch {
            case _: Throwable => {
              errors.append("ERROR Parsing line: " + line)
            }
          }
        }

      } else if (line.startsWith("#PBS")) {
        // Catch all for all other unsupported PBS options
        warnings.append("WARNING: Unsupported PBS option: " + line)
      }

    }

    // Checks
    if (out.contains(NodeResource.RESOURCE_MEMORY_GB)) {
      val (value, errorsConv) = parseInt(out(NodeResource.RESOURCE_MEMORY_GB), NodeResource.RESOURCE_MEMORY_GB, optSuffix = "gb")
      out(NodeResource.RESOURCE_MEMORY_GB) = value.toString
      errors.insertAll(errors.length, errorsConv)
    }

    if (out.contains(NodeResource.RESOURCE_CPUCORE)) {
      val (value, errorsConv) = parseInt(out(NodeResource.RESOURCE_CPUCORE), NodeResource.RESOURCE_CPUCORE, optSuffix = "")
      out(NodeResource.RESOURCE_CPUCORE) = value.toString
      errors.insertAll(errors.length, errorsConv)
    }

    if (out.contains(NodeResource.RESOURCE_GPU)) {
      val (value, errorsConv) = parseInt(out(NodeResource.RESOURCE_GPU), NodeResource.RESOURCE_GPU, optSuffix = "")
      out(NodeResource.RESOURCE_GPU) = value.toString
      errors.insertAll(errors.length, errorsConv)
    }

    if (out.contains("walltime")) {
      val (value, errorsConv) = parseTimeToHours(out("walltime"))
      out("walltime") = value.toString
      errors.insertAll(errors.length, errorsConv)
    } else {
      out("walltime") = ScheduledJob.WALLTIME_LIMIT_DISABLED.toString       // Disable wall-time limit
    }

    if (out.contains("timehint")) {
      val (value, errorsConv) = parseTimeToHours(out("timehint"))
      out("timehint") = value.toString
      errors.insertAll(errors.length, errorsConv)
    }

    if (out.contains("preemtable")) {
      val (value, errorsConv) = parseBool(out("preemptable"), "preemptable", false)
      out("preemptable") = value.toString
      errors.insertAll(errors.length, errorsConv)
    }

    // Return
    (errors.toArray, warnings.toArray, out.toMap)
  }


  // Returns (Int, Errors)
  def parseInt(in:String, parameterName:String, optSuffix:String = ""):(Int, Array[String]) = {
    var value:Int = 0
    val errors = new ArrayBuffer[String]()

    if (!in.toLowerCase().endsWith(optSuffix.toLowerCase)) {
      errors.append("ERROR: '" + parameterName + "' expected to be specified in " + optSuffix + " (e.g. 10" + optSuffix + ")")
    } else {
        try {
          val toInt = in.substring(0, in.length - optSuffix.length).toInt
          value = toInt
        } catch {
          case _:Throwable => { errors.append("ERROR: unable to parse parameter '" + parameterName + "' into integer (" + in + ")") }
        }
    }

    // Return
    return (value, errors.toArray)
  }

  def parseBool(in:String, parameterName:String, default:Boolean):(Boolean, Array[String]) = {
    var out:Boolean = default
    val errors = new ArrayBuffer[String]()

    try {
      out = in.toBoolean
    } catch {
      case _:Throwable => { errors.append("ERROR: unable to parse parameter '" + parameterName + "' into boolean (" + in + ")") }
    }
    // Return
    return (out, errors.toArray)
  }

  def parseTimeToHours(in:String):(Double, Array[String]) = {
    var hours:Double = 0.0
    var errors = new ArrayBuffer[String]()

    val elems = in.split(":").reverse
    if (elems.length < 3) {
      errors.append("ERROR: Expected a minimum of 3 elements (HH:MM:SS) for time (" + in + ")")
    } else if (elems.length > 4) {
      errors.append("ERROR: Expected a maximum of 4 elements (DD:HH:MM:SS) for time (" + in + ")")
    }

    try {
      hours += elems(0).toDouble * (1.0 / 3600.0) // Seconds
      hours += elems(1).toDouble * (1.0 / 60.0)   // Minutes
      hours += elems(2).toDouble * 1.0            // Hours
      if (elems.length == 4) {
        hours += elems(3).toDouble * 24.0         // Days
      }
    } catch {
      case _:Throwable => { errors.append("ERROR: Unable to convert elements to numbers (" + elems.mkString(":") + ")") }
    }

    (hours, errors.toArray)
  }



  // DEBUG: Just a test for the parser
  /*
  def main(args:Array[String]): Unit = {

    val (errors, warnings, out) = parsePBSScript("pbs-examples/example-1min.pbs" )
    println ("Errors: ")
    println (errors.mkString("\n"))
    println ("")

    println ("Warnings: ")
    println (warnings.mkString("\n"))
    println ("")

    println ("Values:")
    for (key <- out.keySet) {
      println ("\t" + key + "\t" + out(key))
    }
    println(out)


    val (value, err) = parseTimeToHours("02:05:30:10")
    println(value)
    println(err.mkString("\n"))
  }
   */
}
