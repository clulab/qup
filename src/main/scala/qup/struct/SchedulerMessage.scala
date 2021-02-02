package main.scala.qup.struct
import SchedulerMessageParser.MESSAGE_DELIM

import scala.collection.mutable

trait SchedulerMessage {
  val messageID:String

  def generateMessage():String
}

class SMUnknown() extends SchedulerMessage {
  val messageID = SchedulerMessageParser.MESSAGE_UNKNOWN

  def generateMessage():String = {
    val outStr = messageID
    return outStr
  }
}

class SMCreateJob(val username:String, val authentication:String, val jobName:String, val projectCode:String, val path:String, val filenameToRun:String, val priority:Int, val preemptable:Boolean, val walltime:Double, val outputMode:String, val resourcesRequired:ResourceCounter) extends SchedulerMessage {
  val messageID = SchedulerMessageParser.MESSAGE_CREATEJOB

  def generateMessage():String = {
    val out = mutable.Map[String, String]()
    out("messageID") = messageID
    out("username") = username
    out("authentication") = authentication
    out("jobName") = jobName
    out("projectCode") = projectCode
    out("path") = path
    out("filenameToRun") = filenameToRun
    out("priority") = priority.toString
    out("preemptable") = preemptable.toString
    out("walltime") = walltime.toString()
    out("outputMode") = outputMode
    out("resourcesRequired") = resourcesRequired.serialize()

    return PacketMap.toSocketStr(out.toMap)
  }

}

object SMCreateJob {
  def deserialize(inStr:String):SMCreateJob = {
    val fields = PacketMap.parseSockeStr(inStr)
    if (fields("messageID") != SchedulerMessageParser.MESSAGE_CREATEJOB) {
      throw new RuntimeException("ERROR: deserialize() called on incorrect message (expected = " + SchedulerMessageParser.MESSAGE_CREATEJOB + ", actual = " + fields("messageID"))
    }

    return new SMCreateJob(username = fields("username"),
      authentication = fields("authentication"),
      jobName = fields("jobName"),
      projectCode = fields("projectCode"),
      path = fields("path"),
      filenameToRun = fields("filenameToRun"),
      priority = fields("priority").toInt,
      preemptable = fields("preemptable").toBoolean,
      walltime = fields("walltime").toDouble,
      outputMode = fields("outputMode"),
      resourcesRequired = ResourceCounter.deserialize( fields("resourcesRequired") ))
  }
}


class SMDeleteJob(val username:String, val authentication:String, val jobId:String) extends SchedulerMessage {
  val messageID = SchedulerMessageParser.MESSAGE_DELETEJOB

  def generateMessage():String = {
    val out = mutable.Map[String, String]()
    out("messageID") = messageID
    out("username") = username
    out("authentication") = authentication
    out("jobId") = jobId

    return PacketMap.toSocketStr(out.toMap)
  }

}

object SMDeleteJob {
  def deserialize(inStr:String):SMDeleteJob = {
    val fields = PacketMap.parseSockeStr(inStr)
    if (fields("messageID") != SchedulerMessageParser.MESSAGE_DELETEJOB) {
      throw new RuntimeException("ERROR: deserialize() called on incorrect message (expected = " + SchedulerMessageParser.MESSAGE_DELETEJOB + ", actual = " + fields("messageID"))
    }

    return new SMDeleteJob(username = fields("username"),
      authentication = fields("authentication"),
      jobId = fields("jobId") )
  }
}


class SMQueueStatus(val numShowCompleted:Int) extends SchedulerMessage {
  val messageID = SchedulerMessageParser.MESSAGE_QSTAT


  def generateMessage():String = {
    val out = mutable.Map[String, String]()
    out("messageID") = messageID
    out("numShowCompleted") = numShowCompleted.toString

    return PacketMap.toSocketStr(out.toMap)
  }
}

object SMQueueStatus {
  def deserialize(inStr:String):SMQueueStatus = {
    val fields = PacketMap.parseSockeStr(inStr)
    if (fields("messageID") != SchedulerMessageParser.MESSAGE_QSTAT) {
      throw new RuntimeException("ERROR: deserialize() called on incorrect message (expected = " + SchedulerMessageParser.MESSAGE_QSTAT + ", actual = " + fields("messageID"))
    }

    return new SMQueueStatus(numShowCompleted = fields("numShowCompleted").toInt )
  }
}


class SMJobInfo(val username:String, val jobId:String) extends SchedulerMessage {
  val messageID = SchedulerMessageParser.MESSAGE_JOBINFO

  def generateMessage():String = {
    val out = mutable.Map[String, String]()
    out("messageID") = messageID
    out("username") = username
    out("jobId") = jobId

    return PacketMap.toSocketStr(out.toMap)
  }

}

object SMJobInfo {
  def deserialize(inStr:String):SMJobInfo = {
    val fields = PacketMap.parseSockeStr(inStr)
    if (fields("messageID") != SchedulerMessageParser.MESSAGE_JOBINFO) {
      throw new RuntimeException("ERROR: deserialize() called on incorrect message (expected = " + SchedulerMessageParser.MESSAGE_JOBINFO + ", actual = " + fields("messageID"))
    }

    return new SMJobInfo(username = fields("username"),
      jobId = fields("jobId") )
  }
}


class SMUsageInfo(val usernameQuery:String = "") extends SchedulerMessage {
  val messageID = SchedulerMessageParser.MESSAGE_USAGEINFO


  def generateMessage():String = {
    val out = mutable.Map[String, String]()
    out("messageID") = messageID
    out("usernameQuery") = usernameQuery.toString

    return PacketMap.toSocketStr(out.toMap)
  }
}

object SMUsageInfo {
  def deserialize(inStr:String):SMUsageInfo = {
    val fields = PacketMap.parseSockeStr(inStr)
    if (fields("messageID") != SchedulerMessageParser.MESSAGE_USAGEINFO) {
      throw new RuntimeException("ERROR: deserialize() called on incorrect message (expected = " + SchedulerMessageParser.MESSAGE_USAGEINFO + ", actual = " + fields("messageID"))
    }

    return new SMUsageInfo(usernameQuery = fields("usernameQuery") )
  }
}


object SchedulerMessageParser {
  val MESSAGE_UNKNOWN       =     "unknown"
  val MESSAGE_CREATEJOB     =     "createjob"
  val MESSAGE_DELETEJOB     =     "deljob"
  val MESSAGE_QSTAT         =     "qstat"
  val MESSAGE_JOBINFO       =     "jobinfo"
  val MESSAGE_USAGEINFO     =     "usageinfo"

  val MESSAGE_DELIM         =     "\t"


  def parseMessage(inStr:String):SchedulerMessage = {
    // Step 1: Split input by field delimiter
    val fields = PacketMap.parseSockeStr(inStr)

    // Step 2: Determine message type
    var messageType = ""
    if (fields.contains("messageID")) {
      messageType = fields("messageID")
    }

    // Step 3: Use storage-class specific parser
    messageType match {
      case MESSAGE_CREATEJOB    => SMCreateJob.deserialize(inStr)
      case MESSAGE_DELETEJOB    => SMDeleteJob.deserialize(inStr)
      case MESSAGE_QSTAT        => SMQueueStatus.deserialize(inStr)
      case MESSAGE_JOBINFO      => SMJobInfo.deserialize(inStr)
      case MESSAGE_USAGEINFO    => SMUsageInfo.deserialize(inStr)
      case _ => new SMUnknown
    }

  }

}
