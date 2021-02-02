package main.scala.qup.statistics

import java.io.PrintWriter

import main.scala.qup.scheduler.Configuration
import main.scala.qup.struct.{PacketMap, ResourceCounter, SMCreateJob, ScheduledJob, SchedulerMessageParser}

import scala.collection.mutable
import main.scala.qup.util.StringUtils.cwidth

import scala.collection.mutable.ArrayBuffer

object Statistics {
  val filenameStatistics = Configuration.statisticsFilename

  /*
   *  File I/O
   */

  // Load from file
  def loadStatistics():mutable.Map[String, JobStatistics] = synchronized {
    val out = mutable.Map[String, JobStatistics]()

    try {
      for (line <- io.Source.fromFile(filenameStatistics, "UTF-8").getLines()) {
        if (line.length > 0) {
          val js = JobStatistics.deserialize(line)
          out(js.jobId) = js
        }
      }
    } catch {
      case _:Throwable => {
        return out
      }
    }

    return out
  }

  // Save to file
  def saveStatistics(statsIn:mutable.Map[String, JobStatistics]): Unit = synchronized {
    val pw = new PrintWriter(filenameStatistics)

    // Sort in integer order (while allowing for the possibility that some may not be strings in the future)
    val ordered = new ArrayBuffer[(Int, JobStatistics)]()
    for (jobId <- statsIn.keySet) {
      var jobIdInt = -1
      try {
        jobIdInt = jobId.toInt
      } catch {
        case _:Throwable => { }
      }

      ordered.append( (jobIdInt, statsIn(jobId)) )
    }
    val sorted = ordered.sortBy(_._1)

    // Output
    for (i <- 0 until sorted.length) {
      val js = sorted(i)._2
      pw.println( js.serialize() )
    }

    pw.close()
  }

  // Update file with current server jobs
  def updateToFile(in:Array[ScheduledJob]): Unit = synchronized {
    // Step 1: Load existing from file
    val records = loadStatistics()

    // Step 2: Update/add new jobs
    for (job <- in) {
      val js = job.toJobStatistics()
      records(js.jobId) = js
    }

    // Step 3: Save
    saveStatistics(records)
  }


  // Find the next unused job integer id (integer)
  def getNextUnusedJobID():Int = {
    var maximum:Int = 0

    def knownJobs = loadStatistics()
    for (key <- knownJobs.keySet) {
      try {
        val jobIdInt = key.toInt
        if (maximum < jobIdInt) maximum = jobIdInt
      } catch {
        case _:Throwable => { }
      }
    }

    // Return
    return (maximum+1)
  }


  /*
   * String methods
   */
  def mkUserStatistics(records:mutable.Map[String, JobStatistics]):Map[String, UserStatistics] = {
    val usernames = records.map(_._2.username).toSet
    val userStats = mutable.Map[String, UserStatistics]()

    // Initialize
    for (username <- usernames) {
      userStats(username) = new UserStatistics(username)
    }

    // Calculate
    for (jobId <- records.keySet) {
      val js = records(jobId)
      val username = js.username
      userStats(username).accumulate(js)
    }

    // Return
    userStats.toMap
  }


  // If specific user is blank (""), then usage statistics for all users are shown
  def mkSummaryString(records:mutable.Map[String, JobStatistics], specificUser:String = ""): String = {
    val os = new StringBuilder
    val userStats = mkUserStatistics(records)

    if ((specificUser != "") && (!userStats.contains(specificUser))) {
      return ("No usage statistics for user (" + specificUser + "). ")
    }

    // Sort by username?
    val sortedOut = userStats.map(_._2).toArray.sortBy(_.username)

    os.append( UserStatistics.mkHeader() + "\n")
    os.append("------------------------------------------------------------------------------------------------------------------------------------------------\n")
    for (record <- sortedOut) {
      if ((specificUser == "") || (record.username == specificUser)) {
        os.append(record.toString() + "\n")
      }

    }

    // Return
    os.toString()
  }

}


// Storage class
class UserStatistics(val username:String) {
  var jobsSubmitted:Double = 0.0
  var runtimeHours:Double = 0.0
  var resourceHours = new ResourceCounter()

  def accumulate(js:JobStatistics): Unit = {
    if (js.username == username) {
      jobsSubmitted += 1
      runtimeHours += js.runtimeHours

      for (resourceName <- js.resourcesRequested.keySet()) {
        val amountOfResource = js.resourcesRequested.getCount(resourceName)   // e.g. 10 cores, 4 GPUs
        val resourceTime = amountOfResource * js.runtimeHours
        resourceHours.incResource(resourceName, resourceTime)
      }
    }
  }


  override def toString():String = {
    val os = new StringBuilder
    val delim = " "

    os.append(cwidth(username, 20) + delim)
    os.append(cwidth(jobsSubmitted.toInt.toString, 15) + delim)
    os.append(cwidth(runtimeHours.formatted("%3.1f") + " hrs", 17) + delim)

    val resourceNames = resourceHours.keySet().toArray.sorted
    for (resourceName <- resourceNames) {
      val usageStr = resourceName + ": " + resourceHours.getCount(resourceName).formatted("%3.1f") + " hrs"
      os.append(cwidth(usageStr, 20) + delim)
    }

    // Return
    os.toString()
  }
}


object UserStatistics {

  def mkHeader():String = {
    val os = new StringBuilder
    val delim = " "

    os.append(cwidth("USERNAME", 20) + delim)
    os.append(cwidth("JOBS_SUBMITTED", 15) + delim)
    os.append(cwidth("RUNTIME_HOURS", 17) + delim)

    os.append(cwidth("RESOURCE_HOURS", 20) + delim)

    // Return
    os.toString()
  }
}



// Storage class
class JobStatistics(val jobId:String, val status:String, val jobName:String, val projectCode:String, val username:String, val dateSubmitted:String, val waittimeHours:Double, val runtimeHours:Double, val resourcesRequested:ResourceCounter) {

  def serialize():String = {
    val out = mutable.Map[String, String]()
    out("jobId") = jobId
    out("status") = status
    out("jobName") = jobName
    out("projectCode") = projectCode
    out("username") = username
    out("dateSubmitted") = dateSubmitted
    out("waitTimeHours") = waittimeHours.formatted("%.3f")
    out("runtimeHours") = runtimeHours.formatted("%.3f")
    out("resourcesRequested") = resourcesRequested.serialize()

    return PacketMap.toSocketStr(out.toMap)
  }

}

object JobStatistics {
  def deserialize(inStr:String):JobStatistics = {
    val fields = PacketMap.parseSockeStr(inStr)

    return new JobStatistics(
      jobId = fields("jobId"),
      status = fields("status"),
      jobName = fields("jobName"),
      projectCode = fields("projectCode"),
      username = fields("username"),
      dateSubmitted = fields("dateSubmitted"),
      waittimeHours = fields("waitTimeHours").toDouble,
      runtimeHours = fields("runtimeHours").toDouble,
      resourcesRequested = ResourceCounter.deserialize(fields("resourcesRequested"))
    )
  }

}
