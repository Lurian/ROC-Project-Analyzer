package master.gitlab.model

import java.util.Date

import master.mongodb.commit.CommitModel

case class CommitDTO( id: String,
                      short_id: String,
                      title: String,
                      author_email: String,
                      author_name: String,
                      authored_date: Date,
                      committer_name: String,
                      committer_email: String,
                      committed_date: Date,
                      created_at: Date,
                      message: String,
                      parent_ids: List[String]
                    ) extends GitlabModel {

  def asModel(projectId: String): CommitModel = {
    val notBugFixUntilAnalyzed = false
    CommitModel(
      projectId,
      this.author_email,
      this.id,
      this.title,
      this.created_at,
      this.message,
      notBugFixUntilAnalyzed,
      this.parent_ids)
  }
}
