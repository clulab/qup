package main.scala.qup.authentication

import main.scala.qup.scheduler.Configuration

import scala.collection.mutable

class UserAuthentication() {
  val username = System.getProperty("user.name")
  val filenameUserPassword = UserAuthentication.getUserPasswordFilename(username)

  /*
   * Load a user's password from their own file
   */
  // Returns (password, priority)
  def loadPasswordAndPriority():(String, Int) = {
    var password:String  = ""
    var priority:Int = -1

    try {
      for (line <- io.Source.fromFile(filenameUserPassword, "UTF-8").getLines()) {
        val fields = line.split("\t")
        if (fields.length == 3) {
          val username = fields(0)
          password = fields(1)
          priority = fields(2).toInt
        }
      }
    } catch {
      case e:Throwable => println("ERROR Loading user password file: " + e.toString)
    }

    // Return
    return (password, priority)
  }


  /*
   * Helper
   */

}

object UserAuthentication {

  def getUserPasswordFilename(username:String):String = {
    var filenameUserPassword = Configuration.userPasswordFilename
    filenameUserPassword = filenameUserPassword.replaceAll("\\{USERNAME\\}", username)
    return filenameUserPassword
  }

}