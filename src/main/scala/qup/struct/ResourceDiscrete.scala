package main.scala.qup.struct

import main.scala.qup.scheduler.Configuration

import scala.collection.mutable
import scala.util.Random

class ResourceDiscreteCounter() {
  val resources = mutable.HashMap[String, ResourceDiscrete]()

  def keySet() = resources.keySet

  def contains(key:String) = resources.contains(key)

  def getBase(key:String) = resources(key).baseNumber()

  def getAvailable(key:String) = resources(key).numAvailable()

  def addResource(in:ResourceDiscrete): Unit = {
    resources(in.resourceName) = in
  }

  def get(key:String) = resources(key)

  def available(manifest:Map[String, Int]):Boolean = {
    // Check if available
    for (resourceName <- manifest.keySet) {
      val quantityRequested = manifest(resourceName)
      if (!resources.contains(resourceName)) {
        println ("ERROR: Resource unknown (" + resourceName + ")")
        return false
      }
      //print("Resource (" + resourceName + ") available: " + resources(resourceName).numAvailable() + "  (requested: " + quantityRequested + ")")
      if (quantityRequested > resources(resourceName).numAvailable() ) {
        // Resource not available
        return false
      }
    }

    // Return
    true
  }

  def request(manifest:Map[String, Int]):Map[String, Array[String]] = {
    // Check if available
    if (!available(manifest)) return Map.empty[String, Array[String]]

    // If we reach here, the resources are available -- allocate them
    val out = mutable.HashMap[String, Array[String]]()
    for (resourceName <- manifest.keySet) {
      val quantityRequested = manifest(resourceName)
      out(resourceName) = resources(resourceName).request(quantityRequested)
    }

    // Return assignments
    return out.toMap
  }

  def release(in:Map[String, Array[String]]) {
    for (resourceName <- in.keySet) {
      val namesToRelease = in(resourceName)
      resources(resourceName).release( namesToRelease )
    }
  }


  /*
   * String methods
   */
  override def toString():String = synchronized {
    val os = new StringBuilder

    for (key <- this.keySet().toArray.sorted) {
      val deviceNames = resources(key).deviceNames
      os.append(key + ": " + deviceNames.mkString(",") + "  ")
    }

    // Return
    os.toString().trim()
  }

}

// E.g. ResourceDiscrete( "gpu", Array(0, 1, 2, 3) )
class ResourceDiscrete(val resourceName:String, val deviceNames:Array[String]) {
  // Total number of units of this resource
  private val baseNum = deviceNames.length

  private val available = mutable.Set[String]()
  private val assigned = mutable.Set[String]()

  // Constructor
  for (elem <- deviceNames) available.add(elem)


  /*
   * Accessors
   */

  // total amount of this resource available
  def numAvailable():Int = {
    available.size
  }

  // Total amount of this resource
  def baseNumber():Int = {
    baseNum
  }


  def devicesAvailable():Array[String] = {
    available.toArray.sorted
  }

  def devicesAssigned():Array[String] = {
    assigned.toArray.sorted
  }

  def devicesBase():Array[String] = {
    deviceNames
  }

  // Request resource assignment
  def request(numToRequest:Int):Array[String] = {
    if (numToRequest <= numAvailable()) {
      // Randomize, to spread out the load?

      var toAssign:Array[String] = null
      if (Configuration.shuffleDiscreteResources) {
        toAssign = Random.shuffle(available.toList).slice(0, numToRequest).toArray    // Shuffled -- randomly pick e.g. GPUs to balance the wear across them.
      } else {
        // Sequential/in-order
        toAssign = available.toArray.sorted.slice(0, numToRequest)
      }

      for (elem <- toAssign) {
        available.remove(elem)
        assigned.add(elem)
      }

      return toAssign
    }

    // Default return -- resources unavailable
    return Array.empty[String]
  }

  // Return resources back
  def release(resourcesAssigned:Array[String]): Unit = {
    for (elem <- resourcesAssigned) {
      if (deviceNames.contains(elem)) {
        assigned.remove(elem)
        available.add(elem)
      } else {
        println ("ERROR: Resource (" + elem + ") attempting to be released to (" + resourceName + ") but is unknown (" + deviceNames.mkString(", ") + ")")
      }
    }
  }

}
