package master.gitlab.model

case class CompareDTO(
                       commit: CommitDTO,
                       commits: List[CommitDTO],
                       diffs: List[DiffDTO],
                       compare_same_ref: Boolean,
                       compare_timeout: Boolean
                     ) extends GitlabModel
