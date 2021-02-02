package main.scala.qup.scheduler

import java.io.{BufferedReader, File, FileOutputStream, IOException, InputStreamReader, OutputStreamWriter, PrintWriter}
import java.net.{ServerSocket, Socket}

import main.scala.qup.struct.{ResourceStore, SMCreateJob, SMDeleteJob, SMJobInfo, SMQueueStatus, SMUsageInfo, ScheduledJob, SchedulerMessageParser}

/*
 * Accepts connections from clients to the scheduler, interprets requests, and sends them to the SchedulerExecutor.
 */
class SchedulerServer(socket:Socket, num:Int, executor:SchedulerExecutor) extends Runnable {

  // Constructor
  this.constructor()

  def constructor(): Unit = {
    val handler = new Thread(this, "handler-" + num)
    handler.start()
  }


  def run(): Unit = {

    try {

      try {
        if (SchedulerExecutor.DEBUG_OUTPUT_ENABLED) println(num + " Connected.")
        val in = new BufferedReader(new InputStreamReader( socket.getInputStream() ))
        val out = new OutputStreamWriter( socket.getOutputStream() )

        while (true) {

          val line = in.readLine()
          if (line == null) {
            if (SchedulerExecutor.DEBUG_OUTPUT_ENABLED) println(num + " Closed.")
            return
          } else {
            if (SchedulerExecutor.DEBUG_OUTPUT_ENABLED) println (num + " Read: " + line)
            if (line == "exit") {
              print(num + " Closing Connection.")
              return

            } else if (line == "crash") {
              println(num + " Simulating a crash of the Server...")
              Runtime.getRuntime().halt(0)

            } else if (line.startsWith("request")) {
              val payloadFields = line.split("\t", 2)
              if (payloadFields.length != 2) {
                if (SchedulerExecutor.DEBUG_OUTPUT_ENABLED) println("Unrecognized Packet")
              } else {
                val requestTxt = payloadFields(0)   // the word "request"
                val payload = payloadFields(1)      // the actual payload

                // Parse payload
                val schedulerMessage = SchedulerMessageParser.parseMessage(payload)

                var success:Boolean = false
                var msg:String = "Unknown scheduler message."
                schedulerMessage match {
                  case s:SMCreateJob    => { val (success_, msg_) = executor.addJob(s);     success = success_; msg = msg_ }
                  case s:SMDeleteJob    => { val (success_, msg_) = executor.deleteJob(s);  success = success_; msg = msg_ }
                  case s:SMQueueStatus  => { val (success_, msg_) = executor.qStatus(s);    success = success_; msg = msg_ }
                  case s:SMJobInfo      => { val (success_, msg_) = executor.jobInfo(s);    success = success_; msg = msg_ }
                  case s:SMUsageInfo    => { val (success_, msg_) = executor.usageInfo(s);  success = success_; msg = msg_ }

                  case _ => { print("ERROR: Unknown scheduler message") }
                }

                //out.write("SUCCESS: " + success + "\n")
                out.write(msg + "\n")
                out.write(SchedulerServer.SERVER_END_MESSAGE_MARKER + "\n")
                out.flush()
              }

            } else {
              if (SchedulerExecutor.DEBUG_OUTPUT_ENABLED) println (num + " Unknown command: " + line )
              //out.write( "echo " + line + "\n\r")
              out.flush()
            }
          }

        }

      } finally {
        // Close socket if failure
        if (SchedulerExecutor.DEBUG_OUTPUT_ENABLED) println("Socket.close()")
        socket.close()
      }


    } catch {
      case e:IOException => {
        println(num + " ERROR: " + e.toString())
      }

    }
  }


}


// Main entry point for the scheduler
object SchedulerServer {
  val SERVER_PORT                 =  Configuration.serverPort
  val SERVER_END_MESSAGE_MARKER   = "END_OF_SERVER_MESSAGE"

  def main(args:Array[String]): Unit = {
    // Step 0: Redirect stdout/stderr
    // TODO -- Redirect all output to log file
    /*
    val outputStream = new FileOutputStream(new File(Configuration.outputLogFilename))
    Console.withOut(outputStream)
    Console.withErr(outputStream)
     */

    // Step 1: Executor
    val baseNodeResources = new ResourceStore()
    for (resourceName <- Configuration.resourcesContinuous.keySet()) {
      baseNodeResources.addContinuousResource( resourceName, Configuration.resourcesContinuous.getCount(resourceName) )
    }
    for (resourceName <- Configuration.resourcesDiscrete.keySet()) {
      baseNodeResources.addDiscreteResource(resourceName, Configuration.resourcesDiscrete.get(resourceName).deviceNames)
    }

    val executor = new SchedulerExecutor(baseNodeResources)


    // Step 1: Server
    println("Accepting connections on port: " + SERVER_PORT)

    var nextNum = 1
    try {
      val serverSocket = new ServerSocket(SERVER_PORT)
      while (true) {
        val socket = serverSocket.accept()
        nextNum += 1
        val hw = new SchedulerServer(socket, nextNum, executor)
      }
    } catch {
      case e:Throwable => {
        println ("ERROR: Unable to start server. \n" + e.toString)
        sys.exit(1)
      }
    }

  }

}
