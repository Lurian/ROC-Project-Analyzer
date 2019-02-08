package master.mongodb

import scala.beans.BeanProperty

class MongoConfig {
  @BeanProperty var databaseName: String = ""
  @BeanProperty var connectionString: String = ""
}
