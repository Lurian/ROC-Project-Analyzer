package master.gitlab.model

import master.mongodb.diff.DiffModel

object CommitChanges {
  def apply(commitId: String, diffs: List[DiffDTO]): CommitChanges = {
    val javaDiffs: List[DiffDTO] = diffs filter filterNonJavaDiffs
    new CommitChanges(commitId, javaDiffs)
  }

  def filterNonJavaDiffs(commitDiff: DiffDTO): Boolean = {
    commitDiff.new_path.split('.').last == "java"
  }
}

case class CommitChanges(commitId: String, diffs: List[DiffDTO]) {
  def asModel(projectId: String): List[DiffModel] = {
    for {diff <- diffs} yield DiffModel(projectId, commitId,
      diff.old_path, diff.new_path,
      diff.diff, diff.new_file,
      diff.renamed_file, diff.deleted_file)
  }
}
