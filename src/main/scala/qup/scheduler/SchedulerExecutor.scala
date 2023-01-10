package main.scala.qup.scheduler

import java.util.concurrent.TimeUnit

import main.scala.qup.authentication.ServerAuthentication
import main.scala.qup.statistics.Statistics
import main.scala.qup.struct.{NodeResource, ResourceCounter, ResourceDiscrete, ResourceStore, SMCreateJob, SMDeleteJob, SMJobInfo, SMQueueStatus, SMUsageInfo, ScheduledJob}

import scala.collection.mutable.ArrayBuffer
import scala.util.control.Breaks.{break, breakable}


/*
 * The main background process for the scheduler.  Stores the queue, executes jobs as available, etc.
 */
class SchedulerExecutor(resourcesAvailable:ResourceStore) extends Runnable {
  var curJobID: Int = Statistics.getNextUnusedJobID()     // Get next unused jobID from statistics file
  var jobQueue = new ArrayBuffer[ScheduledJob]
  var runningJobs = new ArrayBuffer[ScheduledJob]
  val completedJobs = new ArrayBuffer[ScheduledJob]

  val AUTHENTICATION_FAILED_MESSAGE = "User authentication failed."

  // Constructor
  this.constructor()

  def constructor(): Unit = {
    val executor = new Thread(this, "executor")
    executor.start()
  }


  /*
   * Parsing packets (from server)
   */

  // Returns (success, maxPriority)
  def authenticate(username:String, password:String):(Boolean, Int) = {
    val sa = new ServerAuthentication()

    // Step 1: Check if the user has priority 1 (i.e. admin access)
    val maxPriority = sa.getMaxPriority(username)
    val autenticated = sa.authenticatePassword(username, password)

    // Return
    (autenticated, maxPriority)
  }

  // Add a job to the queue
  // Returns (success, message)
  def addJob(in: SMCreateJob): (Boolean, String) = synchronized {
    // Step 0: Authenticate
    val (authenticated, maxPriority) = authenticate(in.username, in.authentication)
    if (!authenticated) {
      println ("Authentication for user (" + in.username +") failed.")
      return (false, AUTHENTICATION_FAILED_MESSAGE)
    }

    // Authenticate the priority level is valid
    if (in.priority < maxPriority) {
      return (false, "Priority authentication failure -- requested priority (" + in.priority + ") exceeds server priority (" + maxPriority + ") for user (" + in.username + "). ")
    }

    // Step 1: Check that job has non-zero values on minimum required resources that all jobs must specify (e.g. cpu cores, memory)
    val missingResources = new ArrayBuffer[String]
    for (resourceName <- NodeResource.REQUIRED_RESOURCES) {
      if ((!in.resourcesRequired.contains(resourceName)) || (in.resourcesRequired.getCount(resourceName) == 0.0)) {
        missingResources.append(resourceName)
      }
    }
    if (missingResources.length > 0) {
      var msg = "Unable to queue job -- missing or zero values on required resources (" + NodeResource.REQUIRED_RESOURCES.mkString(", ") + ")\n"
      msg += "  Requested Resources: " + in.resourcesRequired.toString() + "\n"
      msg += "    Missing Resources: " + missingResources.mkString(", ")

      return (false, msg)
    }

    // Step 2: Check if it's possible for job to run
    if (resourcesAvailable.exceedsPossibleResources(in.resourcesRequired)) {
      // Exceeds possible resources -- show error message and return
      var msg = "Unable to queue job -- requested resources exceeds possible node resources.\n"
      msg += "       Node Resources: " + resourcesAvailable.getBaseResourceStr() + "\n"
      msg += "  Requested Resources: " + in.resourcesRequired.toString() + "\n"

      return (false, msg)
    }

    // Step 3: Add job to queue
    curJobID += 1
    val scheduledJob = ScheduledJob.createFromSMCreateJob(in, curJobID.toString())
    scheduledJob.setStatus(ScheduledJob.STATUS_QUEUED)
    jobQueue.append(scheduledJob)

    // Step 4: Generate success message for server
    var msg:String = scheduledJob.mkStringVerbose() + "\n\n"
    msg += "Submitted job " + curJobID.toString()

    (true, msg)
  }


  // Delete a job in the queue (or, kill a running job)
  // Returns (success, message)
  def deleteJob(sm:SMDeleteJob):(Boolean, String) = {
    // Step 0: Authenticate
    val (authenticated, maxPriority) = authenticate(sm.username, sm.authentication)
    if (!authenticated) {
      println ("Authentication for user (" + sm.username +") failed.")
      return (false, AUTHENTICATION_FAILED_MESSAGE)
    }

    // Find a reference to the job (to check the username for authentication)
    val requestedJob = findJob(sm.jobId)
    if (requestedJob.isEmpty) {
      return (false, "Unknown job (" + sm.jobId + ")")
    }

    // Step 1: Verify the username on the job is the same as the user making the request (or, the requester is an administrator)
    if ((sm.username == requestedJob.get.username) || (maxPriority == 1)) {
      // Step 2: Delete job
      return removeJob(sm.jobId)
    }

    // If we reach here, authentication failed.
    return (false, "Job (" + sm.jobId + ") can not be deleted because it is owned by a different user (" + requestedJob.get.username + "). ")
  }

  // Show queue status
  // Returns (success, message)
  def qStatus(sm:SMQueueStatus):(Boolean, String) = {
    val msg = getQueueStr(sm.numShowCompleted)
    (true, msg)
  }

  def jobInfo(sm:SMJobInfo):(Boolean, String) = {
    // Find job with ID
    var message = ""
    val jobs = completedJobs.filter(_.id == sm.jobId) ++ runningJobs.filter(_.id == sm.jobId) ++ jobQueue.filter(_.id == sm.jobId)
    if (jobs.size == 0) {
      message = "Unknown job (" + sm.jobId + ")"
      return (false, message)

    } else {
      for (job <- jobs) {
        message += job.mkStringVerbose() + "\n"
      }
    }
    // Return
    (true, message)
  }

  def usageInfo(sm:SMUsageInfo):(Boolean, String) = {
    // Get usage info

    // Update statistics
    this.updateStatistics()

    // Load statistics
    val stats = Statistics.loadStatistics()

    // Calculate statistics
    val msg = Statistics.mkSummaryString(stats, specificUser = sm.usernameQuery)

    // Return
    (true, msg)
  }


  // Update external job statistics file
  def updateStatistics(): Unit = {
    val jobsToUpdate = jobQueue ++ completedJobs ++ runningJobs
    Statistics.updateToFile(jobsToUpdate.toArray)
  }

  /*
   * Adding/removing jobs
   */

  private def findJob(jobID:String):Option[ScheduledJob] = {
    // Step 1: Search queue
    if (jobQueue.filter(_.id == jobID).length != 0) {
      return Some( jobQueue.filter(_.id == jobID)(0) )
    }

    // Step 2: Search running jobs
    if (runningJobs.filter(_.id == jobID).length != 0) {
      return Some( runningJobs.filter(_.id == jobID)(0) )
    }

    // Step 3: Search completed list
    if (completedJobs.filter(_.id == jobID).length != 0) {
      return Some( completedJobs.filter(_.id == jobID)(0) )
    }

    // Not found
    None
  }

  // Remove a job from the queue
  def removeJob(jobID:String): (Boolean, String) = synchronized {
    var message = "Unknown job (" + jobID + ")"

    // Step 1: Remove job if it's in the queue
    if (jobQueue.filter(_.id == jobID).length != 0) {
      jobQueue = jobQueue.filter(_.id != jobID)
      message = "Job removed from queue (" + jobID + ")"
    }

    // Step 2: Kill job if it's running
    val runningJobsToKill = runningJobs.filter(_.id == jobID)
    for (runningJob <- runningJobsToKill) {
      runningJob.killJob()
      message = "Job killed (" + jobID + ")"
    }

    // Step 3: (Optional) Check if job is already in the completed list
    if (completedJobs.filter(_.id == jobID).length != 0) {
      message = "Job already completed (" + jobID + ")"
    }

    (true, message)
  }


  /*
   * Running jobs
   */

  private def executeJob(jobID:String): Unit = synchronized {
    // println ("* executeJob(): ")

    // Find job in queue
    var jobToRun:Option[ScheduledJob] = None
    breakable {
      for (i <- 0 until jobQueue.length) {
        if (jobQueue(i).id == jobID) {
          jobToRun = Some(jobQueue(i))
          jobQueue.remove(i)
          break()
        }
      }
    }

    // Run job
    if (jobToRun.isDefined) {
      runJob(jobToRun.get)
    }

  }

  private def runJob(job:ScheduledJob): Unit = synchronized {
    if (SchedulerExecutor.DEBUG_OUTPUT_ENABLED) println ("* runJob(): " + job.id + "\t" + job.username + "\t" + job.path + "\t" + job.filenameToRun)

    synchronized {
      // Remove required resources (now happens elsewhere)
      //resourcesAvailable -= job.resourcesRequired

      // Add job to run list
      job.setStatus(ScheduledJob.STATUS_RUNNING)
      runningJobs.append(job)

      // Spawn external process
      val runner = new ProcessRunner(job, this)
      var thread = new Thread(runner)
      thread.start()
    }

  }


  def finishedJobCallback(job:ScheduledJob, exitCode:Int): Unit = synchronized {
    if (SchedulerExecutor.DEBUG_OUTPUT_ENABLED) println (" * finishedJobCallback(Job " + job.id + ") " + job.getRuntimeHours() + " hours")

    // If the job wasn't killed, mark it as completed.
    if ((job.status != ScheduledJob.STATUS_KILLED) && (job.status != ScheduledJob.STATUS_TIMEDOUT)) {
      job.setStatus(ScheduledJob.STATUS_COMPLETED)
    }
    job.setExitCode(exitCode.toString)

    // Just in case there's an issue, wrap it in a try/catch, so we still release the resources and mark it completed.
    try {
      //println (" * remove job: ")
      //println ("runnings jobs: \n" + runningJobs.mkString("\n"))
      // Remove running job
      runningJobs = runningJobs.filter(_.id != job.id)
    } catch {
      case e:Throwable => println ("ERROR REMOVING JOB " + job.id)
    }

    // Restore resources
    resourcesAvailable.release( job.resourcesAllocated )

    // Add to list of jobs that have been completed
    completedJobs.append(job)

  }



  /*
   * Runner
   */

  def run(): Unit = {
    if (SchedulerExecutor.DEBUG_OUTPUT_ENABLED) println(" * SchedulerExecutor(): Started... ")
    var lastStatisticsUpdate = System.currentTimeMillis()

    while (true) {
      // Step 0: Sort Queue by priority
      jobQueue = jobQueue.sortBy(_.priority)

      // Step 1: Show queue
      if (SchedulerExecutor.DEBUG_OUTPUT_ENABLED) println( this.getQueueStr() )

      // Step 2: Check to see if any jobs have completed

      // Step 3: Check if there are any waiting jobs to run
      if (jobQueue.length > 0) {
        val nextJob = jobQueue(0)
        val nextJobID = jobQueue(0).id

        // Check if job can run
        val resourceAllocation = resourcesAvailable.request(nextJob.resourcesRequired)
        if (resourceAllocation.size != 0) {
          // Allocate resources
          nextJob.resourcesAllocated = resourceAllocation
          executeJob(nextJobID)
        }

      }

      // Step 4: Update external statistics file once per minute
      val curTime = System.currentTimeMillis()
      if ((curTime - lastStatisticsUpdate) > 60*1000) {
        this.updateStatistics()
        lastStatisticsUpdate = System.currentTimeMillis()
      }

      // Step 5: Sleep
      TimeUnit.MILLISECONDS.sleep(2000)
    }

  }


  /*
   * String methods
   */
  def getQueueStr(maxCompletedJobs:Int=5):String = synchronized {
    val os = new StringBuilder()

    os.append("Current Queue (" + jobQueue.length + " jobs)\n")
    os.append("Available Resources: ")
    os.append(resourcesAvailable.getUtilizationStr() + "\n")
    os.append(ScheduledJob.mkHeaderStr() + "\n")
    os.append("-" * (ScheduledJob.mkHeaderStr().length + 2) + "\n")

    var cStartIdx = 0
    if (maxCompletedJobs != -1) {
      cStartIdx = completedJobs.length-maxCompletedJobs
    }
    for (i <- math.max(0, cStartIdx) until completedJobs.length) {
      val job = completedJobs(i)
      os.append( job.mkStringSummary() + "\n" )
      //os.append("c"+ i + "\t" + job.id + "\t" + job.username + "\t" + job.path + "\t" + job.filenameToRun + "\t" + job.resourcesRequired.toString() + "\n")
    }

    for (i <- 0 until runningJobs.length) {
      val job = runningJobs(i)
      os.append( job.mkStringSummary() + "\n" )
      //os.append("r"+ i + "\t" + job.id + "\t" + job.username + "\t" + job.path + "\t" + job.filenameToRun + "\t" + job.resourcesRequired.toString() + "\n")
    }

    for (i <- 0 until jobQueue.length) {
      val job = jobQueue(i)
      os.append( job.mkStringSummary() + "\n" )
      //os.append(i + "\t" + job.id + "\t" + job.username + "\t" + job.path + "\t" + job.filenameToRun + "\t" + job.resourcesRequired.toString() + "\n")
    }

    return os.toString()
  }

}

object SchedulerExecutor {
  val DEBUG_OUTPUT_ENABLED = false
}