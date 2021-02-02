package main.scala.qup.struct

import scala.collection.mutable


object PacketMap {
  val PACKET_DELIM    = "\t"

  // Serialization
  def toSocketStr(in:Map[String, String]):String = {
    val os = new StringBuilder
    for (key <- in.keySet) {
      val sanitizedKey = key.replaceAll("[=\t]", "")
      val sanitizedValue = in(key).replaceAll("\t", "")

      os.append(sanitizedKey + "=" + sanitizedValue + "\t")
    }

    os.toString()
  }

  // Deserialization
  def parseSockeStr(inStr:String):Map[String, String] = {
    val out = mutable.Map[String, String]()
    val fields = inStr.split(PACKET_DELIM)
    for (field <- fields) {
      val elems = field.split("=", 2)
      if (elems.length == 2) {
        val key = elems(0)
        val value = elems(1)
        out(key) = value
      }
    }
    // Return
    out.toMap
  }

}
