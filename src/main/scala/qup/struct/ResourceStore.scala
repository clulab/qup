package main.scala.qup.struct

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class ResourceStore {
  // Base level of resources
  val baseResourcesContinuous = new ResourceCounter()

  // Available resources
  val resourcesContinuous = new ResourceCounter()
  val resourcesDiscrete = new ResourceDiscreteCounter()

  /*
   * Adding resources
   */

  def addContinuousResource(name:String, quantityAvailable:Double): Unit = {
    baseResourcesContinuous.setCount(name, quantityAvailable)
    resourcesContinuous.setCount(name, quantityAvailable)
  }

  def addDiscreteResource(name:String, deviceNames:Array[String]): Unit = {
    resourcesDiscrete.addResource( new ResourceDiscrete(name, deviceNames) )
  }

  /*
   * Checking
   */

  // Check to see if the resources requested by a job exceed the possible resources of the node
  def exceedsPossibleResources(manifest:ResourceCounter):Boolean = {
    for (resourceName <- manifest.keySet()) {
      val quantityRequested = manifest.getCount(resourceName)
      if (resourcesContinuous.contains(resourceName)) {
        if (quantityRequested > baseResourcesContinuous.getCount(resourceName)) {
          return true
        }
      } else if (resourcesDiscrete.contains(resourceName)) {
        if (quantityRequested > resourcesDiscrete.getBase(resourceName)) {
          return true
        }
      } else {
        // Unknown resource
        println ("Unknown resource (" + resourceName + ")")
        return true
      }
    }

    // If we reach here, the manifest does not exceed the node's possible resources.
    return false
  }

  /*
   * Request/Allocation
   */
  def request(manifest:ResourceCounter):Map[String, Array[String]] = {
    // Convert from ResourceCounter to Map manifest
    val manifestOut = mutable.Map[String, Int]()
    for (key <- manifest.keySet()) {
      manifestOut(key) = manifest.getCount(key).toInt
    }

    // Call
    return request(manifestOut.toMap)
  }

  def request(manifest:Map[String, Int]):Map[String, Array[String]] = {
    //println ("Requesting: " + manifest.toString)

    // Check if available
    for (resourceName <- manifest.keySet) {
      val quantityRequested = manifest(resourceName)

      if (resourcesContinuous.contains(resourceName)) {
        if (quantityRequested > resourcesContinuous.getCount(resourceName)) {
          // Requested resource unavailable
          //print(resourceName + " Unavailable")
          return Map.empty[String, Array[String]]
        }
      } else if (resourcesDiscrete.contains(resourceName)) {
        if (!resourcesDiscrete.available(Map(resourceName -> quantityRequested))) {
          // Requested resource unavailable
          //print(resourceName + " Unavailable")
          return Map.empty[String, Array[String]]
        }
      } else {
        println("ERROR: Requested unknown resource (" + resourceName + ")")
      }
    }

    // If we reach here, requested resources are available -- assign them
    // TODO: Add checks to make sure that the resources are still available, in case something changes, and release the allocated resources if they're not.
    val out = mutable.HashMap[String, Array[String]]()
    for (resourceName <- manifest.keySet) {
      val quantityRequested = manifest(resourceName)

      if (resourcesContinuous.contains(resourceName)) {
        resourcesContinuous.decResource(resourceName, quantityRequested)
        out(resourceName) = Array(quantityRequested.toString())
      } else if (resourcesDiscrete.contains(resourceName)) {
        val requested = resourcesDiscrete.request(Map(resourceName -> quantityRequested))
        out(resourceName) = requested(resourceName)
      }
    }

    // Return
    out.toMap
  }


  def release(in:Map[String, Array[String]]): Unit = {
    for (resourceName <- in.keySet) {
      if (resourcesContinuous.contains(resourceName)) {
        val quantityToReturn = in(resourceName)(0).toInt
        resourcesContinuous.incResource(resourceName, quantityToReturn)
      } else if (resourcesDiscrete.contains(resourceName)) {
        val toRelease = Map(resourceName -> in(resourceName))
        resourcesDiscrete.release(toRelease)
      } else {
        println("ERROR: Requested unknown resource (" + resourceName + ")")
      }
    }
  }


  /*
   * String methods
   */
  def getBaseResourceStr():String = {
    val os = new StringBuilder
    val outTuples = new ArrayBuffer[(String, String)]()

    // Continuous
    for (key <- resourcesContinuous.keySet().toArray.sorted) {
      val base = baseResourcesContinuous.getCount(key)
      outTuples.append( (key, base.toInt.toString) )
    }

    // Discrete
    for (key <- resourcesDiscrete.keySet().toArray.sorted) {
      val base = resourcesDiscrete.getBase(key)
      outTuples.append( (key, base.toInt.toString) )
    }

    // Create string
    for (tuple <- outTuples.sortBy(_._1)) {
      os.append(tuple._1 + ": " + tuple._2.padTo(5, ' ') + " ")
    }

    // Return
    os.toString().trim()
  }

  def getUtilizationStr():String = {
    val os = new StringBuilder

    // Continuous
    for (key <- resourcesContinuous.keySet().toArray.sorted) {
      val base = baseResourcesContinuous.getCount(key)
      val available = resourcesContinuous.getCount(key)
      os.append(key + ": " + available.toInt + "/" + base.toInt + "  ")
    }

    // Discrete
    for (key <- resourcesDiscrete.keySet().toArray.sorted) {
      val base = resourcesDiscrete.getBase(key)
      val available = resourcesDiscrete.getAvailable(key)
      os.append(key + ": " + available.toInt + "/" + base.toInt + "  ")
    }

    // Return
    os.toString().trim()
  }

}
