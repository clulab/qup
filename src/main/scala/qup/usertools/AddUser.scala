package main.scala.qup.usertools

import main.scala.qup.authentication.ServerAuthentication
import main.scala.qup.usertools.util.PriorityInterpretation
import main.scala.qup.version.Version

// Tool to add a user to the scheduler
object AddUser {

  def printUsage(): Unit = {
    println ("")
    println (Version.mkNameVersionStr())
    println ("USAGE: qadduser <username> <maxPriority>")
    println ("<maxPriority> is an integer representing the maximum priority between 1 (highest priority) to 9 (lowest priority) that the user can submit jobs.")
    println ("")
    println (PriorityInterpretation.mkPriorityInterpretationStr())
  }

  def main(args:Array[String]): Unit = {
    val sa = new ServerAuthentication()

    //val username = "bob"
    var username = ""
    var priority:Int = 9
    if (args.length == 2) {
      username = args(0)
      try {
        priority = args(1).toInt
      } catch {
        case e:Throwable => {
          println ("ERROR: Unable to parse <maxPriority> (" + args(1) +") to integer.")
          printUsage()
          return
        }
      }
    }

    if (username.length == 0) {
      println("ERROR: No username and maximum priority specified.")
      printUsage()
      return
    }
    if (username.length == 1) {
      println("ERROR: No maximum priority (1-9) specified.")
      printUsage()
      return
    }

    val password = sa.mkRandomPassword()

    // Step 1: Add their password to the server and their home directory
    val success = sa.addOrChangePassword(username, password, priority)

    if (success) {
      if (priority != 1) {
        println("User " + username + " added with maximum priority level " + priority + ". ")
      } else {
        println("User " + username + " added with maximum priority level " + priority + " (administrator). ")
      }

    } else {
      println("Process proceeded with errors -- user may not be added.  Did you forget to run the process as root?")
    }

  }
}
