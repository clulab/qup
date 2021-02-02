package main.scala.qup.usertools.util

import java.io.PrintStream
import java.net.{ConnectException, InetAddress, Socket}

import main.scala.qup.scheduler.{Configuration, SchedulerServer}
import main.scala.qup.struct.SchedulerMessage

import scala.io.BufferedSource
import scala.util.control.Breaks.{break, breakable}

object ClientRequest {

  // Send a message from the client to the server, and receive a string response
  def sendMessage(message:SchedulerMessage, printInConsole:Boolean = true, timeoutSecs:Int = 10):(Boolean, String) = {
    var inStr:String = ""

    try {
      val socket = new Socket(InetAddress.getByName("localhost"), SchedulerServer.SERVER_PORT)
      lazy val in = new BufferedSource(socket.getInputStream()).getLines()
      val out = new PrintStream(socket.getOutputStream())

      out.println("request" + "\t" + message.generateMessage())
      out.flush()

      val startTime = System.currentTimeMillis()

      var timedOut: Boolean = false
      breakable {
        while (!socket.isClosed) {
          while (in.hasNext) {
            // Read in the next line
            val nextLine = in.next()
            // Check for end-of-communication marker
            if (nextLine.startsWith(SchedulerServer.SERVER_END_MESSAGE_MARKER)) break()

            inStr += nextLine + "\n"

            // If enabled, print to console as it's read in
            if (printInConsole) {
              println(nextLine)
            }

            // Check for timeout
            val deltaTime = (System.currentTimeMillis() - startTime) / 1000
            if (deltaTime > timeoutSecs) {
              timedOut = true
              break()
            }
          }
        }
      }

      if (timedOut) {
        println("Request to server timed out after " + timeoutSecs + " seconds.")
      }

      // Close the socket
      socket.close()
    } catch {
      case e:ConnectException => {
        println ("Connection Error: " + e.toString)
        println ("Please verify the qup server is up and running on port (" + Configuration.serverPort + ").")
      }
      case e:Throwable => { return (false, e.toString) }
    }

    // Return
    (true, inStr)
  }

}
