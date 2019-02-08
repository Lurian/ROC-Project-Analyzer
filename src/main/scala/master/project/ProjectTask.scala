package master.project

abstract class ProjectTask {
  def isComplete: Boolean
}
case class TagLoadTask(completed: Boolean) extends ProjectTask {
  override def isComplete: Boolean = completed
}
case class CommitLoadTask(completed: Boolean) extends ProjectTask {
  override def isComplete: Boolean = completed
}
case class CommitDiffLoadTask(completedMap: Map[String, Boolean]) extends ProjectTask {
  override def isComplete: Boolean = completedMap.values reduce {_&&_}
}
