package main.scala.qup.usertools.util

object PriorityInterpretation {
  def mkPriorityInterpretationStr():String = {
    val os = new StringBuilder()

    val priorityLevelMeaning = Map[Int, String](
      1 -> "Administrator",
      2 -> "High Priority",
      3 -> "Elevated Priority",
      4 -> "",
      5 -> "Normal Priority",
      6 -> "",
      7 -> "Low Priority",
      8 -> "Low Priority (Preemptable)",
      9 -> "\"Spare-cycles\" Priority (Preemptable)"
    )

    os.append ("Priority level interpretation:\n")
    for (i <- 1 to 9) {
      os.append (i + ":\t" + priorityLevelMeaning(i) + "\n")
    }

    // Return
    os.toString()
  }

}
