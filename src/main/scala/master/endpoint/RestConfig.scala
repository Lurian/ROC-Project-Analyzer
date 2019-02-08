package master.endpoint

import scala.beans.BeanProperty

class RestConfig {
  @BeanProperty var restMap: java.util.HashMap[String, String] = new java.util.HashMap[String, String]()
  @BeanProperty var subResourceSlashesThreshold: Integer = 3
  @BeanProperty var subResourceTag: String = ""
  @BeanProperty var resourceTag: String = ""
}
