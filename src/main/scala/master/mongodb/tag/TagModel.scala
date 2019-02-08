package master.mongodb.tag

import java.util.Date

case class TagModel(projectId: String, name: String, commitId: String, creationTime: Date)
