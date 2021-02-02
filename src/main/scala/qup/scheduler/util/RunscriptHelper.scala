package main.scala.qup.scheduler.util

import java.io.{File, PrintWriter}
import java.nio.file.{Files, LinkOption}
import java.nio.file.attribute.PosixFileAttributeView

import main.scala.qup.scheduler.SchedulerExecutor
import main.scala.qup.struct.ScheduledJob

import sys.process._


object RunscriptHelper {

  // Make a runscript, and set it's owner/permissions to be runnable by the user specified in the job
  def mkRunScript(filenameOut:String, job:ScheduledJob):Boolean = {
    if (SchedulerExecutor.DEBUG_OUTPUT_ENABLED) println ("Writing " + filenameOut)
    val pw = new PrintWriter(filenameOut)
    pw.println("#/bin/bash")

    // Step 1: Environment variables
    var cudaVisibleDevicesStr = ""
    if (job.resourcesAllocated.contains("gpu")) {
      val gpusAllocated = job.resourcesAllocated("gpu")
      cudaVisibleDevicesStr = gpusAllocated.mkString(",")
    }

    pw.println("export CUDA_VISIBLE_DEVICES=" + cudaVisibleDevicesStr)

    // Step 2: Change to working directory
    pw.println("cd " + job.path + "/")

    // Step 3: Run main script
    pw.println("sh " + job.filenameToRun)

    pw.flush()
    pw.close()


    // Make executable
    val chmodCmd = "chmod 700 " + filenameOut
    val result1 = chmodCmd.!
    if (result1 != 0) return false

    // Set owner to user
    val result2 = FileUtils.changeOwner(filenameOut, job.username)
    if (result2 != 0) return false

    // Default
    return true
  }


}
