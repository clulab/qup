package main.scala.qup.usertools

import java.io.PrintWriter

import main.scala.qup.authentication.{ServerAuthentication, UserAuthentication}
import main.scala.qup.scheduler.Configuration
import main.scala.qup.struct.{NodeResource, ResourceCounter, SMCreateJob, SMDeleteJob}
import main.scala.qup.usertools.util.{ClientRequest, PBSScriptParser, PriorityInterpretation}
import main.scala.qup.version.Version

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.control.Breaks._


object SubmitJob {


  def mkSubmissionMessage(username:String, authentication:String, path:String, filenamePBSScript:String, jobParameters:Map[String, String]):(Array[String], SMCreateJob) = {
    val errors = new ArrayBuffer[String]()

    // Step N: Look for known resources (e.g. cpu, memory, gpu) in the jobParameters, and add them to the resource counter
    val resourcesRequired = new ResourceCounter()
    for (knownResource <- NodeResource.KNOWN_RESOURCES) {
      if (jobParameters.contains(knownResource)) {
        resourcesRequired.setCount(knownResource, jobParameters(knownResource).toDouble)
      }
    }

    //(val username:String, val path:String, val filenameToRun:String, val priority:Int, val resourcesRequired:ResourceCounter)
    val sm = new SMCreateJob(username = username,
      authentication = authentication,
      jobName = jobParameters("jobName"),
      projectCode = jobParameters("projectCode"),
      path = path,
      filenameToRun = filenamePBSScript,
      priority = jobParameters("priority").toInt,
      preemptable = jobParameters("preemptable").toBoolean,
      walltime = jobParameters("walltime").toDouble,
      outputMode = jobParameters("outputMode"),
      resourcesRequired = resourcesRequired
      )

    return (errors.toArray, sm)
  }


  // Handy tool to automatically add a user's environment variables to script
  def addUsersEnvToScript(filename:String): Unit = {
    val ENV_STR_BEGIN = "#### ENVIRONMENT VARIABLES BELOW AUTOMATICALLY ADDED FROM QSUB ####"
    val ENV_STR_END = "#### ENVIRONMENT VARIABLES ABOVE AUTOMATICALLY ADDED FROM QSUB ####"
    val VARS_TO_COPY = Configuration.userEnvVarsToCopy


    // Step 1: Load script (and, omit any environment variable blocks that were previously added automatically)
    val scriptLines = new ArrayBuffer[String]
    var omit:Boolean = false
    for (line <- io.Source.fromFile(filename, "UTF-8").getLines()) {
      if (line.startsWith(ENV_STR_BEGIN)) omit = true
      if (!omit) scriptLines.append( line )
      if (line.startsWith(ENV_STR_END)) omit = false
    }

    // Step 2: Find location to append environment variable block
    var startLine:Int = 0
    breakable {
      for (i <- 0 until scriptLines.length) {
        if (!scriptLines(i).startsWith("#")) {
          startLine = i
          break()
        }
      }
    }

    if (startLine == 0) {
      // If this happens, the entire script is comments.  Should not normally happen in practice.
      // We'll just add in an extra blank line, and let the environment variables populate below.
      scriptLines.append("")
      startLine = scriptLines.length-1
    }

    // Step 3: Retrieve environment variables
    val envVarLines = new ArrayBuffer[String]
    envVarLines.append(ENV_STR_BEGIN)
    val envVars = sys.env
    for (varName <- envVars.keySet) {
      val value = envVars(varName)
      if (VARS_TO_COPY.contains(varName)) {
        envVarLines.append("export " + varName + "=" + value)
      }
    }
    envVarLines.append(ENV_STR_END)

    // Step 4: Add environment variables
    scriptLines.insertAll(startLine, envVarLines)

    // Step 5: Export
    val pw = new PrintWriter(filename)
    for (line <- scriptLines) {
      pw.println(line)
    }
    pw.flush()
    pw.close()

  }


  def printUsage(): Unit = {
    println ("")
    println (Version.mkNameVersionStr())
    println ("USAGE: qsub <runscript.pbs> <opt:priority (1-9)> <opt:--thisenv>")
    println ("Example: qsub myjob.pbs 5")
    println ("Example: qsub myjob.pbs 5 --thisenv")
    println ("Example: qsub myjob.pbs --thisenv")
    println ("")
    println ("The --thisenv flag copies select environment variables from the user's current environment into the runscript.")
    println ("")
    println (PriorityInterpretation.mkPriorityInterpretationStr())
  }

  def main(args:Array[String]): Unit = {
    val username = System.getProperty("user.name")

    // User authentication
    val ua = new UserAuthentication()
    val (userPassword, priorityDefault) = ua.loadPasswordAndPriority()

    if ((userPassword == "") || (priorityDefault == -1)) {
      println ("Current user (" + username + ") does not appear to be authorized for submission queue (no authentication found).")
      return
    }

    // Step 1: Parse command line arguments
    if ((args.length < 1) || (args.length > 3)) {
      println("ERROR: Missing or unexpected command line arguments")
      printUsage()
      println ("User (" + username + ") currently authorized for priority level " + priorityDefault + "-9.")
      return
    }

    // TODO: Get user's base priority
    val filenamePBS = args(0)
    var priority = priorityDefault

    if ((args.length == 2) || (args.length == 3)) {
      if (args(1) == "--thisenv") {
        this.addUsersEnvToScript(filenamePBS)
      } else {
        try {
          val overridePriority = args(1).toInt
          if (overridePriority < priorityDefault) {
            println("ERROR: Requested priority (" + overridePriority + ") is higher than maximum priority this user is authorized for (" + priorityDefault + ").")
            println("Please choose a value between 9 and " + priorityDefault + ".")
            return
          }
        } catch {
          case _: Throwable => {
            println("ERROR: Unable to convert priority to integer. Expect integer (1-9), found (" + args(1) + ").")
            printUsage()
            return
          }
        }
      }
    }

    if (args.length == 3) {
      if (args(2) == "--thisenv") {
        this.addUsersEnvToScript(filenamePBS)
      } else {
        println("ERROR: Missing or unexpected command line arguments")
        printUsage()
        println ("User (" + username + ") currently authorized for priority level " + priorityDefault + "-9.")
        return
      }
    }


    // Step 2: Parse PBS file for any job parameters
    val (errors, warnings, jobParameters1) = PBSScriptParser.parsePBSScript(filenamePBS)
    val jobParameters = mutable.Map[String, String]() ++ jobParameters1     // Convert to mutable so we can add parameters in config

    // Step 2A: Check for errors
    if (errors.length > 0) {
      println ("ERRORS: ")
      for (i <- 0 until errors.length) {
        println (i + ": " + errors(i))
      }
      println ("Exiting...")
      return
    }

    // Step 3: Verify job parameters

    // Step N: Add job priority to parameters
    jobParameters("priority") = priority.toString

    // Pack into SMCreateJob message
    val currentPath = System.getProperty("user.dir")

    val (errors1, message) = mkSubmissionMessage(username = username,
      authentication = userPassword,
      path = currentPath,
      filenamePBSScript = filenamePBS,
      jobParameters.toMap
    )

    // Send message
    val (success, responseStr) = ClientRequest.sendMessage(message, printInConsole = true, timeoutSecs = 10)

    // Print warnings last (so they're easily visible)
    if (warnings.length > 0) {
      println ("")
      println ("Warnings: ")
      for (i <- 0 until warnings.length) {
        println (i + ": " + warnings(i))
      }

    }

  }



}
