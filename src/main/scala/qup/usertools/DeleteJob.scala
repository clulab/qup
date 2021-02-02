package main.scala.qup.usertools

import main.scala.qup.authentication.{ServerAuthentication, UserAuthentication}
import main.scala.qup.struct.{SMDeleteJob, SMQueueStatus}
import main.scala.qup.usertools.util.ClientRequest
import main.scala.qup.version.Version


// Delete a single job from the queue (or, kill a running job)
object DeleteJob {

  def printUsage(): Unit = {
    println ("")
    println (Version.mkNameVersionStr())
    println ("USAGE: qdel <jobid>")
    println ("Example: qdel 123")
  }

  def main(args:Array[String]): Unit = {
    val username = System.getProperty("user.name")

    // User authentication
    val ua = new UserAuthentication()
    val (userPassword, priorityDefault) = ua.loadPasswordAndPriority()

    // Step 2: Parse command line arguments
    if (args.length != 1) {
      println("ERROR: Missing or unexpected command line arguments")
      printUsage()
      return
    }
    val jobId = args(0)

    // Step 3: Send delete job message
    val message = new SMDeleteJob(username = username, authentication = userPassword, jobId = jobId)
    val (success, responseStr) = ClientRequest.sendMessage(message, printInConsole = true, timeoutSecs = 10)

    if (!success) {
      println(responseStr)
    }
  }

}
