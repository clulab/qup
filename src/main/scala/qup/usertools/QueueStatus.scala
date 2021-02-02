package main.scala.qup.usertools

import java.io.PrintStream
import java.net.{InetAddress, Socket}

import main.scala.qup.authentication.ServerAuthentication
import main.scala.qup.scheduler.SchedulerServer
import main.scala.qup.struct.{SMQueueStatus, SchedulerMessage}
import main.scala.qup.usertools.util.ClientRequest
import main.scala.qup.version.Version

import scala.io.BufferedSource
import scala.util.control.Breaks._


// Show the queue to the user
object QueueStatus {

  def printUsage(): Unit = {
    println ("")
    println (Version.mkNameVersionStr())
    println ("USAGE: qstat <optional: number of completed jobs to show>")
    println ("")
    println ("Example 1: Show all queued and running jobs:")
    println ("  qstat")
    println ("Example 2: Show all queued, running, and last 5 completed jobs: ")
    println ("  qstat 5")
    println ("Example 3: Show all queued, running, and all completed jobs: ")
    println ("  qstat -1")
  }

  def main(args:Array[String]): Unit = {
    val sa = new ServerAuthentication()

    var numShowCompleted:Int = 5
    if (args.length == 1) {
      try {
        numShowCompleted = args(0).toInt
      } catch {
        case _:Throwable => {
          println("ERROR: Unable to parse number of completed jobs to show (" + args(0) + ").  Integer expected")
          printUsage()
          return
        }
      }
    } else if (args.length > 1) {
      println("ERROR: Unexpected command line arguments")
      printUsage()
      return
    }

    val message = new SMQueueStatus(numShowCompleted = numShowCompleted)
    val (success, responseStr) = ClientRequest.sendMessage(message, printInConsole = true, timeoutSecs = 10)

    if (!success) {
      println(responseStr)
    }

  }

}
