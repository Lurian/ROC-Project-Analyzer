package master.mongodb.bug

import java.util.Date


object BugModel {
  def apply(
             oldBugModel: BugModel,
             versionName: Option[String]
           ): BugModel = new BugModel(
    oldBugModel.assignedTo,
    oldBugModel.classification,
    oldBugModel.component,
    oldBugModel.creationTime,
    oldBugModel.lastChangeTime,
    oldBugModel.id,
    oldBugModel.priority,
    oldBugModel.resolution,
    oldBugModel.severity,
    oldBugModel.status,
    oldBugModel.summary,
    versionName)
}

case class BugModel(
                     assignedTo: String,
                     classification: String,
                     component: String,
                     creationTime: Date,
                     lastChangeTime: Date,
                     id: String,
                     priority: String,
                     resolution: String,
                     severity: String,
                     status: String,
                     summary: String,
                     versionName: Option[String] = None
                   )