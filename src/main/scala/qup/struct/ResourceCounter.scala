package main.scala.qup.struct

import scala.collection.mutable

// Storage class for a set of node resources (e.g. CPU core, RAM, GPU, etc).
class ResourceCounter() {
  private val lut = new mutable.HashMap[String, Double]()

  /*
   * Accessors
   */
  def keySet() = lut.keySet

  def contains(key:String) = lut.contains(key)

  def getCount(name:String):Double = synchronized {
    if (lut.contains(name)) {
      return lut(name)
    }

    // Default return
    return 0.0
  }

  def setCount(name:String, value:Double): Unit = synchronized {
    lut(name) = value
  }

  // Increment/Decrement
  def incResource(name:String, value:Double): Unit = synchronized {
    setCount(name, getCount(name) + value)
  }

  def decResource(name:String, value:Double): Unit = synchronized {
    setCount(name, getCount(name) - value)
  }


  /*
   * Resource checking
   */

  // Check to see if the resources required for 'toCheck' can be met by the current counter
  def hasEnoughResourcesLeft(toCheck:ResourceCounter):Boolean = synchronized {
    for (key <- toCheck.keySet()) {
      val available = this.getCount(key)
      val required = toCheck.getCount(key)
      if (available < required) {
        return false
      }
    }

    // If we reach here, all required resources are available
    return true
  }

  /*
   * Arithmetic
   */
  def += (that:ResourceCounter): Unit = synchronized {
    for (key <- that.keySet()) {
      this.incResource(key, that.getCount(key))
    }
  }

  def -= (that:ResourceCounter): Unit = synchronized {
    for (key <- that.keySet()) {
      this.decResource(key, that.getCount(key))
    }
  }


  /*
   * String methods
   */
  def serialize():String = {
    val os = new StringBuilder
    for (key <- this.keySet().toArray.sorted) {
      os.append(key + "=" + this.getCount(key) + ResourceCounter.SERIALIZE_DELIM)
    }
    os.toString()
  }

  override def toString():String = synchronized {
    val os = new StringBuilder

    for (key <- this.keySet().toArray.sorted) {
      os.append(key + ": " + this.getCount(key).toInt.toString.padTo(5, ' ') + " ")
    }

    // Return
    os.toString().trim()
  }

}

object ResourceCounter {
  val SERIALIZE_DELIM = "-DELIM-"

  def deserialize(inStr:String):ResourceCounter = {
    val out = new ResourceCounter

    val fields = inStr.split(SERIALIZE_DELIM)


    for (field <- fields) {
      val elems = field.split("=", 2)
      if (elems.length == 2) {
        val key = elems(0)
        val value = elems(1).toDouble
        out.setCount(key, value)
      }
    }

    // Return
    out
  }
}


object NodeResource {
  val RESOURCE_CPUCORE    = "cpucore"
  val RESOURCE_MEMORY_GB  = "memory"
  val RESOURCE_GPU        = "gpu"

  val KNOWN_RESOURCES = Array(RESOURCE_CPUCORE, RESOURCE_MEMORY_GB, RESOURCE_GPU)
  val REQUIRED_RESOURCES = Array(RESOURCE_CPUCORE, RESOURCE_MEMORY_GB)
}


