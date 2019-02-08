package master.gitlab.model

import master.mongodb.tag.TagModel

case class TagDTO(name: String, commit: CommitDTO) extends GitlabModel {

  def asModel(projectId: String): TagModel = {
    TagModel(projectId, this.name, this.commit.id, this.commit.created_at)
  }
}
