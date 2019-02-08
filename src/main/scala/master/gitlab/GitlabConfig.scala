package master.gitlab

import scala.beans.BeanProperty

class GitlabConfig {
  @BeanProperty var privateToken = ""
  @BeanProperty var url = ""
  @BeanProperty var repoAddress = ""
}
