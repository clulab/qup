package main.scala.qup.util

import java.io.FileInputStream
import java.util.Properties

object PropertiesParser {

  /*
   * Loading
   */
  def loadPropertiesFile(filename:String):Properties = {
    val prop = new Properties()
    prop.load(new FileInputStream(filename))
    return prop
  }

  /*
   * Parsing
   */
  def getStr(prop:Properties, name:String, defaultValue:String):String = {
    try {
      return prop.getProperty(name)
    } catch {
      case _:Throwable => { return defaultValue }
    }
  }

  def getInt(prop:Properties, name:String, defaultValue:Int):Int = {
    try {
      return prop.getProperty(name).toInt
    } catch {
      case _:Throwable => { return defaultValue }
    }
  }

  def getDouble(prop:Properties, name:String, defaultValue:Double):Double = {
    try {
      return prop.getProperty(name).toDouble
    } catch {
      case _:Throwable => { return defaultValue }
    }
  }

  def getBool(prop:Properties, name:String, defaultValue:Boolean):Boolean = {
    try {
      return prop.getProperty(name).toBoolean
    } catch {
      case _:Throwable => { return defaultValue }
    }
  }

}
