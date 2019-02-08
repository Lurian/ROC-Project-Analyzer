package master.javaParser.model

case class Method(start: Int, end: Int, name: String) {

  def isImpacted(line: Lines): Boolean = {
    val L1 = line.start
    val L2 = line.end
    val M1 = start
    val M2 = end
    L1 <= M2 && M1 <= L2
  }
}
