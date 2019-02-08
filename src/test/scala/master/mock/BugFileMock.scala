package master.mock

object BugFileMock {
  
  def getSimpleBugFile(): String = {
    """
       {
          "ASSIGNED": [
          {
            "assigned_to": "example@email.com",
            "assigned_to_detail": {
              "email": "example@email.com",
              "id": 40,
              "name": "Name",
              "real_name": "Real Name"
            },
            "classification": "prod",
            "component": "Feedback",
            "creation_time": "2017-12-05T18:25:50Z",
            "id": 2275,
            "is_open": true,
            "last_change_time": "2018-01-09T15:14:07Z",
            "priority": "Normal",
            "resolution": "",
            "severity": "normal",
            "status": "ASSIGNED",
            "summary": "summary"
          }]
        }
    """
  }

}
