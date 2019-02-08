package master.log

object EndpointEntry{
  private val endpointLineRegex = """(.+)\s(.+)\s(.+)""".r

  def apply(endpointLine: String): EndpointEntry = {
    val regexMatchOption = endpointLineRegex.findFirstMatchIn(endpointLine)
    if(regexMatchOption.isEmpty){ throw new Exception("Error when extracting endpoint information!")}
    val regexMatch = regexMatchOption.get

    val action = regexMatch.group(1)
    val endpoint = regexMatch.group(2)
    val httpVersion = regexMatch.group(3)
    new EndpointEntry(action, endpoint, httpVersion)
  }
}

case class EndpointEntry(action: String, endpoint: String, httpVersion: String)
