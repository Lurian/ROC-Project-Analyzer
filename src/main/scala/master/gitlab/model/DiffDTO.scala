package master.gitlab.model

import master.mongodb.diff.DiffModel

case class DiffDTO(old_path: String, new_path: String, diff: String,
                   new_file: Boolean, renamed_file: Boolean, deleted_file: Boolean) extends GitlabModel {
  def asModel(projectId: String, identifier: String): DiffModel = {
    DiffModel(projectId, identifier, this.old_path, this.new_path, this.diff, this.new_file, this.renamed_file, this.deleted_file)
  }
}
