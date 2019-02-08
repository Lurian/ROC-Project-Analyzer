package master.bugzilla

import java.util.Date

import master.mongodb.bug.BugModel

case class BugDTO(
                   assigned_to: String,
                   assigned_to_detail: AssignedToDetail,
                   classification: String,
                   component: String,
                   creation_time: Date,
                   id: String,
                   is_open: Boolean,
                   last_change_time: Date,
                   priority: String,
                   resolution: String,
                   severity: String,
                   status: String,
                   summary: String
                   ) {
  def asModel(): BugModel = {
    BugModel(
      assigned_to,
      classification,
      component,
      creation_time,
      last_change_time,
      id,
      priority,
      resolution,
      severity,
      status,
      summary
    )
  }
}

case class AssignedToDetail(
                             email : String,
                             id: String,
                             name: String,
                             real_name: String
                           )
