package main.scala.qup.version

object Version {
  val PROGRAM_NAME    = "qup job scheduler"
  val PROGRAM_VERSION = "0.1.0"


  def mkNameVersionStr():String = {
    PROGRAM_NAME + " " + "v" + PROGRAM_VERSION
  }

}
