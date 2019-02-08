package master.mongodb.version

import java.util.Date
import master.javaParser.model.Impact

import master.mongodb.ChangeImpact

object VersionImpact {
  def apply(changeImpact: ChangeImpact): VersionImpact = new VersionImpact(changeImpact.projectId, changeImpact.identifier, changeImpact.impactList)
}

case class VersionImpact(projectId: String, tag: String, impactList: List[Impact])