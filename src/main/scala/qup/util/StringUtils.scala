package main.scala.qup.util

object StringUtils {

  // Make a constant-width string
  def cwidth(strIn:String, width:Int, rightJustify:Boolean = false):String = {

    if (strIn.length > width) {
      // Case 1: strIn is longer
      return "..." + strIn.substring(strIn.length-width+3)
    } else {
      // Case 2: strIn is shorter
      val padding = width - strIn.length
      if (rightJustify) {
        return " "*padding + strIn
      } else {
        return strIn + " "*padding
      }
    }

  }

}


