package master.mongodb.diff

object DiffModel {
  def apply(projectId: String, identifier: String, old_path: String, new_path: String, diff: String,
            new_file: Boolean, renamed_file: Boolean, deleted_file: Boolean): DiffModel = new DiffModel(projectId, identifier, old_path, new_path, diff, new_file, renamed_file, deleted_file)

  def filterNonJavaDiffs(diffModel: DiffModel): Boolean = {
    diffModel.new_path.split('.').last == "java"
  }
}

case class DiffModel(projectId: String, identifier: String, old_path: String, new_path: String, diff: String,
                     new_file: Boolean, renamed_file: Boolean, deleted_file: Boolean) {

}
