package master.project

import scala.beans.BeanProperty

class ProjectConfig {
  @BeanProperty var projectDir = ""
  @BeanProperty var restDir = ""
  @BeanProperty var projectId = ""
  @BeanProperty var jarSubPath = ""
  @BeanProperty var rootPackage = ""
  @BeanProperty var projectRootApiCall = ""
}
