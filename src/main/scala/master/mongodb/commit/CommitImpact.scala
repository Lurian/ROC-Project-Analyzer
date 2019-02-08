package master.mongodb.commit

import master.javaParser.model.Impact

import master.mongodb.ChangeImpact

object CommitImpact {
  def apply(changeImpact: ChangeImpact): CommitImpact = new CommitImpact(changeImpact.projectId, changeImpact.identifier, changeImpact.impactList)
}

case class CommitImpact(projectId: String, commitId: String, impactList: List[Impact])