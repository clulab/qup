package main.scala.qup.usertools

import main.scala.qup.authentication.ServerAuthentication
import main.scala.qup.struct.{SMDeleteJob, SMJobInfo}
import main.scala.qup.usertools.util.ClientRequest
import main.scala.qup.version.Version

object JobInfo {

  def printUsage(): Unit = {
    println ("")
    println (Version.mkNameVersionStr())
    println ("USAGE: qinfo <jobid>")
    println ("Example: qinfo 123")
  }

  def main(args:Array[String]): Unit = {
    val sa = new ServerAuthentication()

    if (args.length != 1) {
      println("ERROR: Missing or unexpected command line arguments")
      printUsage()
      return
    }
    val jobId = args(0)

    // TODO: Username
    val message = new SMJobInfo(username = "", jobId = jobId)
    val (success, responseStr) = ClientRequest.sendMessage(message, printInConsole = true, timeoutSecs = 10)

    if (!success) {
      println(responseStr)
    }

  }


}
