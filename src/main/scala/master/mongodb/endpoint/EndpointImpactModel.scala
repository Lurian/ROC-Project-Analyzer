package master.mongodb.endpoint

import master.endpoint.Endpoint


case class EndpointImpactModel(projectId: String, versionName: String, endpointList: List[Endpoint])
