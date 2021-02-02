package main.scala.qup.scheduler

import java.io.{BufferedReader, File, InputStreamReader, PrintWriter}
import java.nio.file.{Files, LinkOption}
import java.nio.file.attribute.PosixFileAttributeView

import main.scala.qup.scheduler.util.{FileUtils, RunscriptHelper}
import main.scala.qup.struct.ScheduledJob

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.util.control.Breaks._
import scala.collection.JavaConverters._
import sys.process


/*
 * Thread for running a given user process
 */
class ProcessRunner(job:ScheduledJob, executor:SchedulerExecutor) extends Runnable {


  def run(): Unit = {
    job.markStartTime()

    var exitCode = -1
    try {
      if (SchedulerExecutor.DEBUG_OUTPUT_ENABLED) println("Job " + job.id + ": Attempting to start...  ")

      // Run command
      val exitValue = this.runJobInterruptable()
      exitCode = exitValue

      if (SchedulerExecutor.DEBUG_OUTPUT_ENABLED) println("Job " + job.id + " Completed (Exit Code " + exitValue + "). ")
    } catch {
      case e:Throwable => {
        val errorStr = "ERROR Running Job " + job.id + ": " + e.toString
        println(errorStr)
        job.addNote(errorStr)
      }
    }

    job.markEndTime()

    // Call callback
    executor.finishedJobCallback(job, exitCode)

  }



  //## TODO: As the scheduler is run as root, the process should be run as the user who submitted it
  def runJobInterruptable():Int = {
    var exitCode = -999

    // Step N: Make runscript
    val runscriptFilename = Configuration.runscriptTempDir + "/" + "runscript-job" + job.id + ".sh"
    RunscriptHelper.mkRunScript(runscriptFilename, job)

    // Step 1: Assemble process builder
    val runScriptCmd = List[String]("runuser", "-l", "peter", "-c", "'" + runscriptFilename + "'")     // Works
    val pb = new ProcessBuilder()
    pb.command(runScriptCmd.asJava)

    // Redirect input stream to /dev/null
    pb.redirectInput(new File("/dev/null"))

    // Logging settings
    // Set stdout/stderr redirects
    pb.redirectError(job.fileLogError)
    pb.redirectOutput(job.fileLogOutput)

    // Set permissions for log files
    FileUtils.changeOwner(job.fileLogOutput.getAbsolutePath, job.username)
    FileUtils.changeOwner(job.fileLogError.getAbsolutePath, job.username)

    // Step 2: Start command asynchronously
    val process = pb.start()
    val processFuture = Future( blocking(process.waitFor()) )

    // Step 3: Wait for process to finish, or for a kill signal to occur
    var done:Boolean = false
    breakable {
      while (true) {
        // Flush output
        process.getOutputStream.flush()

        // Step 3A: Check if process kill status has been set
        if (job.status == ScheduledJob.STATUS_KILLING) {
          val killStr = "KILL: Job (" + job.id + ") has received kill signal.  Attempting to kill. "
          println(killStr)

          process.destroyForcibly()
          exitCode = process.waitFor()

          println("KILL: Job (" + job.id + ") killed.")
          job.setStatus(ScheduledJob.STATUS_KILLED)

          writeToLogs(job.fileLogError, killStr)
          writeToLogs(job.fileLogOutput, killStr)

          break()
        }

        // Step 3B: Check if process has timed out
        if ((job.walltimeLimitHours > -1) && (job.getRuntimeHours() > job.walltimeLimitHours)) {
          job.setStatus(ScheduledJob.STATUS_TIMEEXCEEDED)
          val timeExceededStr = "TIME EXCEEDED: Job (" + job.id + ") has exceeded maximum runtime (" + job.walltimeLimitHours.formatted("%3.3f") + " hours).  Attempting to kill. "
          println(timeExceededStr)

          process.destroyForcibly()
          exitCode = process.waitFor()

          println("TIME EXCEEDED: Job (" + job.id + ") killed.")
          job.setStatus(ScheduledJob.STATUS_TIMEDOUT)

          writeToLogs(job.fileLogError, timeExceededStr)
          writeToLogs(job.fileLogOutput, timeExceededStr)

          break()
        }

        // Step 3C: Normal case -- wait a bit longer for the process to execute.
        try {
          // One second heartbeat -- wait for process to finish for 1 second before checking for termination criteria
          Await.result(processFuture, duration.Duration(1, "sec"))
          // If we reach here, the process is completed.
          break()
        } catch {
          case e: TimeoutException => { } // The 1 second delay has timed out without the process completing.
        }

      }
    }

    // Delete runscript
    FileUtils.deleteFile(runscriptFilename)
    exitCode = process.waitFor()

    // Return
    return exitCode
  }


  // Helper to write atypical information (e.g. process killed, process timed-out) to a logfile.
  def writeToLogs(file:File, logStr:String): Unit = {
    val pw = new PrintWriter(file)
    pw.println(logStr)
    pw.flush()
    pw.close()
  }

}
