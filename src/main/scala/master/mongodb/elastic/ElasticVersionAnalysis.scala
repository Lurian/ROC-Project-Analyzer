package master.mongodb.elastic

import master.endpoint.Endpoint

case class ElasticVersionAnalysis(projectId: String, versionName: String,
                                  usagePercentiles: List[Percentile],
                                  endpointUsageList: List[Endpoint])
