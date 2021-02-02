package main.scala.qup.scheduler

import java.util.Properties

import com.sun.deploy.util.StringUtils
import main.scala.qup.struct.{NodeResource, ResourceCounter, ResourceDiscrete, ResourceDiscreteCounter}
import main.scala.qup.util.PropertiesParser

import scala.collection.JavaConversions.asScalaSet

object Configuration {
  //val DEFAULT_CONFIGURATION_FILE = "configuration.properties"
  val DEFAULT_CONFIGURATION_FILE = "/etc/qup/configuration.properties"

  // Load configuration
  var props:Properties = null
  try {
    props = PropertiesParser.loadPropertiesFile(DEFAULT_CONFIGURATION_FILE)
  } catch {
    case _:Throwable => {
      println ("ERROR: Could not load configuration file (" + DEFAULT_CONFIGURATION_FILE + ")")
      sys.exit(1)
    }
  }

  // Parse configuration

  /*
   * Section: Server
   */

  // Port
  val serverPort = PropertiesParser.getInt(props, "server.port", 0)
  if (serverPort == 0) {
    println("ERROR: server.port missing from configuration file (" + DEFAULT_CONFIGURATION_FILE + ")")
    sys.exit(1)
  }

  // Master password/authentication filename
  val serverPasswordFilename = PropertiesParser.getStr(props, "server.passwordFilename", "")
  if (serverPasswordFilename == "") {
    println ("ERROR: server.passwordFilename missing from configuration file (" + DEFAULT_CONFIGURATION_FILE + ")")
    sys.exit(1)
  }

  // Statistics filename
  val statisticsFilename = PropertiesParser.getStr(props, "server.statisticsFilename", "")
  if (statisticsFilename == "") {
    println ("ERROR: server.statisticsFilename missing from configuration file (" + DEFAULT_CONFIGURATION_FILE + ")")
    sys.exit(1)
  }

  val outputLogFilename = PropertiesParser.getStr(props, "server.outputLogFilename", "")
  if (outputLogFilename == "") {
    println ("ERROR: server.outputLogFilename missing from configuration file (" + DEFAULT_CONFIGURATION_FILE + ")")
    sys.exit(1)
  }

  val runscriptTempDir = PropertiesParser.getStr(props, "server.runscriptTempDir", "")
  if (runscriptTempDir == "") {
    println ("ERROR: server.runscriptTempDir missing from configuration file (" + DEFAULT_CONFIGURATION_FILE + ")")
    sys.exit(1)
  }

  /*
   * Section: User
   */
  val userPasswordFilename = PropertiesParser.getStr(props, "user.passwordFilename", "")
  if (userPasswordFilename == "") {
    println ("ERROR: user.passwordFilename missing from configuration file (" + DEFAULT_CONFIGURATION_FILE + ")")
    sys.exit(1)
  }


  val userEnvVarsToCopy = PropertiesParser.getStr(props, name="user.envVarsToCopy", "").split(",").map(_.trim())


  /*
   * Section: Resources
   */

  // Parse resources
  val resourcesContinuous = new ResourceCounter()
  val resourcesDiscrete = new ResourceDiscreteCounter()
  for (propName <- asScalaSet(props.stringPropertyNames())) {

    // Continuous resources (e.g. cpucores, memory)
    if (propName.startsWith("resource.continuous.")) {
      val resourceName = propName.substring(20)
      val maxAvailable = PropertiesParser.getInt(props, propName, 0)
      resourcesContinuous.setCount(resourceName, maxAvailable)

      // Discrete resources (e.g. gpus)
    } else if (propName.startsWith("resource.discrete.")) {
      val resourceName = propName.substring(18)
      val devicesAvailable = PropertiesParser.getStr(props, propName, "").split(",").map(_.trim())
      val resourceDiscrete = new ResourceDiscrete(resourceName = resourceName, deviceNames = devicesAvailable)
      resourcesDiscrete.addResource(resourceDiscrete)
    }
  }

  // Resource names
  val resourceNames = resourcesContinuous.keySet() ++ resourcesDiscrete.keySet()
  if (!resourceNames.contains(NodeResource.RESOURCE_CPUCORE)) {
    println("ERROR: resource.continuous.cpucore missing from configuration file (" + DEFAULT_CONFIGURATION_FILE + ")")
    sys.exit(1)
  }
  if (!resourceNames.contains(NodeResource.RESOURCE_MEMORY_GB)) {
    println("ERROR: resource.continuous.memory missing from configuration file (" + DEFAULT_CONFIGURATION_FILE + ")")
    sys.exit(1)
  }
  if (!resourceNames.contains(NodeResource.RESOURCE_GPU)) {
    println("ERROR: resource.discrete.gpu missing from configuration file (" + DEFAULT_CONFIGURATION_FILE + ")")
    sys.exit(1)
  }

  // Discrete resource allocation method
  val shuffleDiscreteResources = PropertiesParser.getBool(props, "resource.shuffleDiscreteResources", false)


  /*
   * Limits
   */
  val defaultWallTimeLimit = PropertiesParser.getStr(props, "limits.defaultWallTimeLimit", "")
  if (defaultWallTimeLimit == "") {
    println("ERROR: limits.defaultWallTimeLimit missing from configuration file (" + DEFAULT_CONFIGURATION_FILE + ")")
    sys.exit(1)
  }


}
