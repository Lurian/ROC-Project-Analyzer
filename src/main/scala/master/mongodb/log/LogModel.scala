package master.mongodb.log

import master.log.LogLine

case class LogModel(projectId: String, versionName: String, logLine: LogLine)
