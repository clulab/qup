package main.scala.qup.struct

import java.io.File
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{LocalDateTime, ZoneId}

import main.scala.qup.statistics.JobStatistics

import scala.collection.mutable.ArrayBuffer
import main.scala.qup.util.StringUtils.cwidth


class ScheduledJob(val id:String, val username:String, val jobName:String, val projectCode:String, val path:String, val filenameToRun:String, val priority:Int, val preemptable:Boolean, val walltimeLimitHours:Double, val outputMode:String, val resourcesRequired:ResourceCounter) {

  var status:String = ScheduledJob.STATUS_UNKNOWN
  var exitCode:String = "-"
  var executionThread:Option[Thread] = None
  var resourcesAllocated:Map[String, Array[String]] = Map()

  var submissionTime = getTimestamp()
  var startTime = ""
  var endTime = ""

  val notes = new ArrayBuffer[String]

  var fileLogError = new File("/dev/null")
  var fileLogOutput = new File("/dev/null")
  this.determineOutputFiles()


  /*
   * Accessors
   */

  def setStatus(strIn:String): Unit = {
    status = strIn
  }

  def setExitCode(strIn:String): Unit = {
    exitCode = strIn
  }

  def setExecutionThread(in:Option[Thread]): Unit = {
    executionThread = in
  }

  def addNote(in:String): Unit = {
    notes.append(in)
  }


  /*
   * Determine output log files
   */
  def determineOutputFiles(): Unit = {
    if (this.outputMode == ScheduledJob.OUTPUT_MODE_NOOUTPUT) {
      // Do not save any output
      fileLogError = new File("/dev/null")
      fileLogOutput = new File("/dev/null")

    } else if (this.outputMode == ScheduledJob.OUTPUT_MODE_BOTH) {
      // Both STDOUT and STDERR
      fileLogError = new File(this.path + "/" + "job." + this.id + ".stderr.txt")
      fileLogOutput = new File(this.path + "/" + "job." + this.id + ".stdout.txt")

    } else if (this.outputMode == ScheduledJob.OUTPUT_MODE_STDOUTONLY) {
      // Only STDOUT
      fileLogError = new File("/dev/null")
      fileLogOutput = new File(this.path + "/" + "job." + this.id + ".stdout.txt")

    } else if (this.outputMode == ScheduledJob.OUTPUT_MODE_STDERRONLY) {
      // Only STDERR
      fileLogError = new File(this.path + "/" + "job." + this.id + ".stderr.txt")
      fileLogOutput = new File("/dev/null")

    }

  }


  /*
   * Kill job
   */

  def killJob(): Unit = {
    print("killJob(): TODO")
    setStatus(ScheduledJob.STATUS_KILLING)
    if (executionThread.isDefined) {
      // TODO
    }
  }

  /*
   * Run time
   */
  def markStartTime(): Unit = {
    startTime = getTimestamp()
  }

  def markEndTime() = {
    endTime = getTimestamp()
  }

  def getTimestamp():String = {
    val out = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now)
    return out
  }

  def getRuntimeHours():Double = {
    // Case 1: Currently running
    if (status == ScheduledJob.STATUS_RUNNING) {
      return getTimeDiffHours(startTime, getTimestamp())
    }
    // Case 2: Currently queued
    if ((startTime == "") || (endTime == "")) return 0

    // Case 3: Completed
    return getTimeDiffHours(startTime, endTime)
  }

  def getTimeDiffHours(start:String, end:String):Double = {
    val p = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    val t1 = LocalDateTime.parse(start, p)
    val t2 = LocalDateTime.parse(end, p)
    val i1 = t1.atZone(ZoneId.of("Europe/London"))
    val i2 = t2.atZone(ZoneId.of("Europe/London"))
    val diff = i1.until(i2, ChronoUnit.SECONDS)

    val minutes = (diff.toDouble / 60.0)   // minutes
    val hours = (minutes / 60.0)          // hours

    return hours
  }

  // Convert hours to a more easily human-readable string (days/hours/minutes/seconds)
  def hoursToDurationStr(hoursIn:Double):String = {
    var hoursLeft = hoursIn
    val days = math.floor(hoursLeft/24).toInt
    hoursLeft -= (24 * days)
    val hours = math.floor(hoursLeft/1).toInt
    hoursLeft -= (1 * hours)
    val minutes = math.floor(hoursLeft*60).toInt
    hoursLeft -= (minutes.toDouble/60.0)
    val seconds = math.floor(hoursLeft*60.0*60.0).toInt

    val strOut = days.formatted("%02d") + "d-" + hours.formatted("%02d") + ":" + minutes.formatted("%02d") + ":" + seconds.formatted("%02d") + ""
    return strOut
  }

  def getTimeSinceStartedHours():Double = {
    if (startTime == "") return 0
    return getTimeDiffHours(startTime, getTimestamp())
  }

  def getWaitTimeHours():Double = {
    if (startTime == "") {
      // Time between submission and now
      return getTimeDiffHours(submissionTime, getTimestamp())
    } else {
      // Time between submission and start
      return getTimeDiffHours(submissionTime, startTime)
    }
  }


  /*
   * Statistics
   */

  // Convert to JobStatistics storage class
  def toJobStatistics():JobStatistics = {
    new JobStatistics(jobId = id,
      status = status,
      jobName = jobName,
      projectCode = projectCode,
      username = username,
      dateSubmitted = this.submissionTime,
      waittimeHours = this.getWaitTimeHours(),
      runtimeHours = this.getRuntimeHours(),
      resourcesRequested = this.resourcesRequired
    )
  }


  /*
   * String methods
   */

  def mkStringSummary():String = {
    val os = new StringBuffer
    val delim = " "

    os.append(cwidth(status,3) + delim)
    os.append(cwidth(exitCode, 4) + delim)
    os.append(cwidth(priority.toString, 3) + delim)
    os.append(cwidth(id, 7) + delim)
    os.append(cwidth(username, 20) + delim)
    os.append(cwidth(path, 30) + delim)
    os.append(cwidth(filenameToRun, 30) + delim)
    os.append(cwidth(resourcesRequired.toString(), 50) + delim)

    var timeStr = ""
    if (status == ScheduledJob.STATUS_RUNNING) {
      timeStr = ("Started: " + startTime + " (Runtime: " + hoursToDurationStr(this.getTimeSinceStartedHours()) + " so far)")
    } else if (status == ScheduledJob.STATUS_COMPLETED) {
      timeStr = ("Ended: " + endTime + " (Runtime: " + hoursToDurationStr(this.getRuntimeHours()) + ")")
    } else if (status == ScheduledJob.STATUS_KILLED) {
      timeStr = ("Killed: " + endTime + " (Runtime: " + hoursToDurationStr(this.getRuntimeHours()) + ")")
    } else if (status == ScheduledJob.STATUS_QUEUED) {
      timeStr = ("Submitted: " + submissionTime + " (Wait time: " + hoursToDurationStr(this.getWaitTimeHours()) + " so far)")
    } else {
      timeStr = ("--")
    }
    os.append(cwidth(timeStr, 70) + delim)

    // Return
    os.toString
  }

  def rjust(in:String, minLength:Int):String = {
    if (in.length < minLength) {
      return (" " * (minLength-in.length) + in)
    }
    // Default return
    in
  }

  def mkResourcesAllocatedStr():String = {
    val os = new StringBuilder
    for (resourceName <- resourcesAllocated.keySet.toArray.sorted) {
      val value = resourcesAllocated(resourceName)

      if ((value.length == 0) || ((value.length == 1) && (value(0) == ""))) {
        // Empty
        os.append(resourceName + ": -" + "     ")
      } else {
        // Normal
        os.append(resourceName + ": " + resourcesAllocated(resourceName).mkString(",").padTo(5, ' ') + " ")
      }

    }
    // Return
    os.toString().trim()
  }

  def mkStringVerbose():String = {
    val os = new StringBuilder

    os.append(rjust("Job ID:", 20) + "  " + id + "\n")
    os.append(rjust("Status:", 20) + "  " + ScheduledJob.getVerboseStatusStr(status) + "\n")
    os.append(rjust("Exit Code:", 20) + "  " + exitCode + "\n")
    os.append(rjust("Priority:", 20) + "  " + priority + "\n")
    os.append(rjust("Preemptable:", 20) + "  " + preemptable + "\n")
    os.append(rjust("Username:", 20) + "  " + username + "\n")
    os.append(rjust("Job Name:", 20) + "  " + jobName + "\n")
    os.append(rjust("Project Code:", 20) + "  " + projectCode + "\n")
    os.append(rjust("Working path:", 20) + "  " + path + "\n")
    os.append(rjust("Filename to run:", 20) + "  " + filenameToRun + "\n")
    os.append(rjust("Resources requested:", 20) + "  " + resourcesRequired.toString() + "\n")
    os.append(rjust("Resources allocated:", 20) + "  " + mkResourcesAllocatedStr() + "\n")
    os.append(rjust("Output (stdout):", 20) + "  " + fileLogOutput.getAbsolutePath + "\n")
    os.append(rjust("Output (stderr):", 20) + "  " + fileLogError.getAbsolutePath + "\n")
    os.append(rjust("Submission Time:", 20) + "  " + submissionTime + "\n")
    os.append(rjust("Start Time:", 20) + "  " + startTime + "\n")
    os.append(rjust("Finish Time:", 20) + "  " + endTime + "\n")
    os.append(rjust("Time waited to run:", 20) + "  " + hoursToDurationStr(this.getWaitTimeHours()) + " ")
    if (endTime == "") os.append ("so far")
    os.append("\n")
    os.append(rjust("Total runtime:", 20) + "  ")
    if (startTime == "") {
      os.append("has not run yet.\n")
    } else {
      os.append(hoursToDurationStr(this.getRuntimeHours()) + " ")
      if (endTime == "") {
        os.append("so far")
      }
      os.append("(" + this.getRuntimeHours().formatted("%.3f") + " hours)")
      if ((walltimeLimitHours > -1) && (this.getRuntimeHours() >= walltimeLimitHours)) {
        os.append(" **EXCEEDED WALL TIME LIMIT**")
      }
      os.append("\n")
    }
    os.append(rjust("Wall Time Limit:", 20) + "  ")
    if (walltimeLimitHours == -1) {
      os.append("no limit")
    } else {
      os.append( hoursToDurationStr(walltimeLimitHours) + " (" + walltimeLimitHours.formatted("%.3f") + " hours)")
    }
    os.append("\n")


    if (notes.length == 0) {
      os.append("Notes: none \b")
    } else {
      os.append("Notes: \n")
      for (i <- 0 until notes.length) {
        os.append(i + ": " + notes(i) + "\n")
      }
    }


    // Return
    os.toString
  }

  override def toString():String = {
    val os = new StringBuffer
    val delim = "\t"
    os.append("id: " + id + delim)
    os.append("username: " + username + delim)
    os.append("path: " + path + delim)
    os.append("filenameToRun: " + filenameToRun + delim)
    os.append("resourcesRequired: " + resourcesRequired + delim)

    // Return
    os.toString
  }
}


object ScheduledJob {
  // Job status
  val STATUS_QUEUED     = "q"
  val STATUS_RUNNING    = "r"
  val STATUS_COMPLETED  = "c"
  val STATUS_KILLING    = "kl"
  val STATUS_KILLED     = "k"
  val STATUS_TIMEEXCEEDED = "tl"
  val STATUS_TIMEDOUT   = "t"
  val STATUS_UNKNOWN    = "-"

  val STATUS_LONG_STR   = Map(
    STATUS_QUEUED     -> "queued",
    STATUS_RUNNING    ->  "running",
    STATUS_COMPLETED  ->  "completed",
    STATUS_KILLING    ->  "killing",
    STATUS_KILLED     ->  "killed",
    STATUS_TIMEEXCEEDED -> "time limit exceeded -- killing",
    STATUS_TIMEDOUT   ->  "time limit exceeded",
    STATUS_UNKNOWN    ->  "unknown"
  )

  // Job output mode
  val OUTPUT_MODE_NOOUTPUT    = "no_output"
  val OUTPUT_MODE_BOTH        = "stdout_and_stderr"
  val OUTPUT_MODE_STDERRONLY  = "stderr_only"
  val OUTPUT_MODE_STDOUTONLY  = "stdout_only"


  val WALLTIME_LIMIT_DISABLED = -1.0


  def mkHeaderStr():String = {
    val os = new StringBuffer
    val delim = " "

    os.append(cwidth("ST",3) + delim)
    os.append(cwidth("EC", 4) + delim)
    os.append(cwidth("PR", 3) + delim)
    os.append(cwidth("JOBID", 7) + delim)
    os.append(cwidth("USERNAME", 20) + delim)
    os.append(cwidth("PATH", 30) + delim)
    os.append(cwidth("RUNSCRIPT", 30) + delim)
    os.append(cwidth("RESOURCES", 50) + delim)

    os.append(cwidth("TIME", 70) + delim)

    // Return
    os.toString

  }


  def getVerboseStatusStr(shortStatus:String):String = {
    if (!STATUS_LONG_STR.contains(shortStatus)) return shortStatus
    return STATUS_LONG_STR(shortStatus)
  }

  def createFromSMCreateJob(in:SMCreateJob, id:String):ScheduledJob = {
    return new ScheduledJob(id = id, username = in.username, jobName = in.jobName, projectCode = in.projectCode, path = in.path, filenameToRun = in.filenameToRun, priority = in.priority, preemptable = in.preemptable, walltimeLimitHours = in.walltime, outputMode = in.outputMode, resourcesRequired = in.resourcesRequired)
  }

}