package main.scala.qup.authentication

import java.io.PrintWriter
import java.security.SecureRandom

import main.scala.qup.scheduler.Configuration
import main.scala.qup.scheduler.util.FileUtils

import scala.collection.mutable
import scala.sys.process.Process


class ServerAuthentication(filenameServerPassword:String = ServerAuthentication.SERVER_PASSWORD_FILENAME) {

  /*
   * Authentication
   */
  // Check that a user's password is valid
  def authenticatePassword(username:String, password:String):Boolean = {
    val (passwords, priorities) = loadPasswordsAndPriorities()
    // Check for unknown username
    if (!passwords.contains(username)) {
      return false
    }
    // Check for incorrect password
    if (passwords(username) == password) {
      return true
    }
    // Default return
    false
  }

  // Check a user's maximum priority level
  def getMaxPriority(username:String):Int = {
    val (passwords, priorities) = loadPasswordsAndPriorities()
    if (priorities.contains(username)) {
      return priorities(username)
    }
    // Default return -- we should never get here
    return -1
  }


  /*
   * Changing passwords
   */

  // Set blank password to remove
  def addOrChangePassword(username:String, password:String, priority:Int): Boolean = {
    var success:Boolean = true

    // Step 1: Load current passwords (in a mutable form)
    val (currPasswords, currPriorities) = loadPasswordsAndPriorities()
    val currentPasswords = collection.mutable.Map() ++ currPasswords
    val currentPriorities = collection.mutable.Map() ++ currPriorities

    // Step 2: Add/change password in server password file
    currentPasswords(username) = password
    currentPriorities(username) = priority

    // Step 3A: Save server password file
    try {
      val pw1 = new PrintWriter(filenameServerPassword)
      for (key <- currentPasswords.keySet) {

        val password = currentPasswords(key)
        if (password.length > 0) {
          // NOTE: If the password is empty, then do not record (i.e. remove) the user in the server passwords
          pw1.println(key + "\t" + currentPasswords(key) + "\t" + currentPriorities(key))
        }
      }
      pw1.close()
    } catch {
      case e:Throwable => {
        println("ERROR Saving server password: " + e.toString)
        println("Did you forget to run the process as root?")
        success = false
      }
    }

    // Step 3B: Set permissions on server filename
    try {
      FileUtils.changePermissions(filenameServerPassword, "600")
    } catch {
      case e:Throwable => {
        println("ERROR Setting server password file permissions: " + e.toString)
        success = false
      }
    }


    // Step 4A: Save user password file
    // User this one in testing
    val pathPlusFilenameUser = UserAuthentication.getUserPasswordFilename(username)
    // Use this one in a real setting
    //val pathPlusFilenameUser = "/home/" + username + "/" + UserAuthentication.USER_PASSWORD_FILENAME
    try {
      val pw2 = new PrintWriter(pathPlusFilenameUser)
      pw2.println(username + "\t" + password + "\t" + priority)
      pw2.close()
    } catch {
      case e:Throwable => {
        println("ERROR Saving user password: " + e.toString)
        println("Did you forget to run the process as root?")
        println("You may also wish to verify the spelling for the username (" + username + ") and that this user exists.")
        success = false
      }
    }

    // Set owner
    try {
      FileUtils.changeOwner(pathPlusFilenameUser, username)
    } catch {
      case e:Throwable => {
        println ("ERROR Changing owner: " + e.toString)
        success = false
      }
    }

    // Step 4B: Set permissions on user filename
    try {
      FileUtils.changeOwner(pathPlusFilenameUser, username)
      FileUtils.changePermissions(pathPlusFilenameUser, "600")
    } catch {
      case e:Throwable => {
        println("ERROR Setting user password file owner and permissions: " + e.toString)
        success = false
      }
    }

    // Return
    success
  }

  /*
   * List users
   */
  def hasUser(username:String):Boolean = {
    if (getUsers().contains(username)) return true
    // Default return
    false
  }

  def getUsers():Array[String] = {
    val (passwords, priority) = loadPasswordsAndPriorities()
    val usernames = passwords.keySet.toArray.sorted
    return usernames
  }

  /*
   * Create random password
   */
  def mkRandomPassword():String = {
    val r = new SecureRandom()
    var rInt = r.nextInt()
    if (rInt < 0) rInt = -rInt
    return rInt.toString
  }

  /*
   * Loading password file
   */
  private def loadPasswordsAndPriorities():(Map[String, String], Map[String, Int]) = {
    val outPass = new mutable.HashMap[String, String]()
    val outPriority = new mutable.HashMap[String, Int]()

    try {
      for (line <- io.Source.fromFile(filenameServerPassword, "UTF-8").getLines()) {
        val fields = line.split("\t")
        if (fields.length == 3) {
          val username = fields(0)
          val password = fields(1)
          val priority = fields(2).toInt

          outPass(username) = password
          outPriority(username) = priority
        }
      }
    } catch {
      case e:Throwable => { println("ERROR Loading server password file: " + e.toString) }
    }

    // Return
    (outPass.toMap, outPriority.toMap)
  }


}


object ServerAuthentication {
  val SERVER_PASSWORD_FILENAME = Configuration.serverPasswordFilename

}