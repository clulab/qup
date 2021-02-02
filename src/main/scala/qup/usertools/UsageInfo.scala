package main.scala.qup.usertools

import main.scala.qup.authentication.ServerAuthentication
import main.scala.qup.struct.SMUsageInfo
import main.scala.qup.usertools.util.ClientRequest
import main.scala.qup.version.Version

object UsageInfo {

  def printUsage(): Unit = {
    println ("")
    println (Version.mkNameVersionStr())
    println ("USAGE: qusage <optional: specific user to query>")
    println ("")
    println ("Example 1: Show usage across all users:")
    println ("  qusage")
    println ("Example 2: Show usage for a specific user: ")
    println ("  qusage bob")
  }

  def main(args:Array[String]): Unit = {
    val sa = new ServerAuthentication()

    var usernameQuery:String = ""
    if (args.length == 1) {
      usernameQuery = args(0)
    } else if (args.length > 1) {
      println("ERROR: Unexpected command line arguments")
      printUsage()
      return
    }

    val message = new SMUsageInfo(usernameQuery = usernameQuery)
    val (success, responseStr) = ClientRequest.sendMessage(message, printInConsole = true, timeoutSecs = 10)

    if (!success) {
      println(responseStr)
    }

  }

}
