package main.scala.qup.usertools

import main.scala.qup.authentication.ServerAuthentication
import main.scala.qup.version.Version

object ShowUsers {

  def printUsage(): Unit = {
    println ("")
    println (Version.mkNameVersionStr())
    println ("USAGE: qshowusers")
  }

  def main(args:Array[String]): Unit = {
    val sa = new ServerAuthentication()

    if (args.length != 0) {
      println("ERROR: Unexpected command line arguments")
      printUsage()
      return
    }

    val usernames = sa.getUsers()
    println("Current users with authentication credentials:")
    println("")
    for (username <- usernames) {
      println (username)
    }
    println("")
    println(usernames.length + " users total.")

  }

}
