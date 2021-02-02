package main.scala.qup.usertools

import main.scala.qup.authentication.ServerAuthentication
import main.scala.qup.version.Version

object RemoveUser {
  def printUsage(): Unit = {
    println ("")
    println (Version.mkNameVersionStr())
    println ("USAGE: qremoveuser <username>")
  }

  def main(args:Array[String]): Unit = {
    val sa = new ServerAuthentication()

    //val username = "bob"
    var username = ""
    if (args.length == 1) {
      username = args(0)
    }

    if (username.length < 1) {
      println("ERROR: No username specified.")
      printUsage()
      return
    }

    if (!sa.hasUser(username)) {
      println ("User " + username + " does not exist.")
      return
    }

    val password = ""       // Blank password to remove user's authentication from server password file

    // Step 1: Add their password to the server and their home directory
    val success = sa.addOrChangePassword(username, password, -1)

    if (success) {
      println("User " + username + " removed. ")
    } else {
      println("Process proceeded with errors -- user may not be added.  Did you forget to run the process as root?")
    }

  }
}
