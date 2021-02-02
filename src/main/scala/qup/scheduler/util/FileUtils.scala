package main.scala.qup.scheduler.util

import java.io.File
import sys.process._

object FileUtils {

  // Delete a file
  def deleteFile(filename:String):Boolean = {
    val file = new File(filename)
    if (file.exists()) {
      file.delete()
      return true
    }
    // Default
    return false
  }


  // Change the owner of a file
  def changeOwner(filename:String, owner:String):Int  = {
    // Do not change permissions of /dev/null, or the system will have large issues.
    if ((filename == "/dev/null/") || (filename == "/dev/null")) return 0

    // If the file doesn't exist, create a blank file
    val file = new File(filename)
    if (!file.exists()) file.createNewFile()

    // Change permissions of regular file
    val chownCmd = "chown " + owner + ":" + owner + " " + filename
    val result = chownCmd.!
    return result
  }


}
