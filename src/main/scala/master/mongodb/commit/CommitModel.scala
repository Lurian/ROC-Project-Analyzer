package master.mongodb.commit

import java.util.Date


object CommitModel {

  def apply(projectId: String,
            author_email: String,
            id: String,
            title: String,
            created_at: Date,
            message: String,
            isBugFix: Boolean,
            parentIds: List[String]) = {
    // If the commit have more than one parent commit it is treated as a merge commit between the parent commits
    val isMerge: Boolean = parentIds.size > 1
    new CommitModel(
      projectId,
      author_email,
      id,
      title,
      created_at,
      message,
      isBugFix,
      isMerge)
  }

  def apply(oldCommitModel: CommitModel, isBugFix: Boolean, bugIds: Option[List[String]]) =
    new CommitModel(
      oldCommitModel.projectId,
      oldCommitModel.author_email,
      oldCommitModel.id,
      oldCommitModel.title,
      oldCommitModel.created_at,
      oldCommitModel.message,
      isBugFix,
      oldCommitModel.isMerge,
      bugIds
    )
}

case class CommitModel(projectId: String,
                       author_email: String,
                       id: String,
                       title: String,
                       created_at: Date,
                       message: String,
                       isBugFix: Boolean,
                       isMerge: Boolean,
                       bugIds: Option[List[String]] = None
                      )