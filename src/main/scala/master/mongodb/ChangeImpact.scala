package master.mongodb

import java.util.Date
import master.javaParser.model.Impact

case class ChangeImpact(projectId: String, identifier: String, impactList: List[Impact])