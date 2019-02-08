package master.project

import master.mock.ModelMock
import master.project.task.ElasticTaskAnalyzer
import org.scalatest.FreeSpec
import org.scalatest.Matchers._

class ElasticTaskAnalyzerTest extends FreeSpec {

  "An ElasticTaskAnalyzer Object" - {
    "when using its 'extractEndpoint' function" - {
      "should return an endpoint based on its verb" in {
        val endpoint = "/mock/"
        val getEndpoint = ModelMock.endpointMock(verb = "GET", endpoint = endpoint)
        val putEndpoint = ModelMock.endpointMock(verb = "PUT", endpoint = endpoint)
        val postEndpoint = ModelMock.endpointMock(verb = "POST", endpoint = endpoint)

        val endpointKey = "GET|/mock/"
        val endpointList = List(getEndpoint, putEndpoint, postEndpoint)
        val extractedEndpoints = ElasticTaskAnalyzer.extractEndpoints(endpointList)(endpointKey)

        extractedEndpoints should equal (List(getEndpoint))
      }

      "should return a REST endpoint if the key contains less than (or equal) 4 '/' characters" in {
        val endpoint = "/mock/"
        val restEndpoint = ModelMock.endpointMock(endpoint = endpoint, endpointType = "REST")
        val subRecursoEndpoint = ModelMock.endpointMock(endpoint = endpoint, endpointType = "SUBRECURSO")

        val endpointKey = "GET|/mock/"
        val endpointList = List(restEndpoint, subRecursoEndpoint)
        val extractedEndpoints = ElasticTaskAnalyzer.extractEndpoints(endpointList)(endpointKey)

        extractedEndpoints should equal (List(restEndpoint))
      }

      "should return a SUBRECURSO endpoint if the key contains more than 4 '/' characters" in {
        val endpoint = "/mock/"
        val restEndpoint = ModelMock.endpointMock(endpoint = endpoint, endpointType = "REST")
        val subRecursoEndpoint = ModelMock.endpointMock(endpoint = endpoint, endpointType = "SUBRECURSO")

        val endpointKey = "GET|/server/subEntity/:id/mock/"
        val endpointList = List(restEndpoint, subRecursoEndpoint)
        val extractedEndpoints = ElasticTaskAnalyzer.extractEndpoints(endpointList)(endpointKey)

        extractedEndpoints should equal (List(subRecursoEndpoint))
      }

      "should return an endpoint that have its string endpoint included in the endpointKey being searched " in {
        val endpoint = "/mock/:id"
        val serverEndpoint = ModelMock.endpointMock(endpoint = endpoint, endpointType = "SUBRECURSO")

        val endpointKey = "GET|/server/subEntity/:id/mock/:id"
        val endpointList = List(serverEndpoint)
        val extractedEndpoints = ElasticTaskAnalyzer.extractEndpoints(endpointList)(endpointKey)

        extractedEndpoints should equal (List(serverEndpoint))
      }
    }
  }
}