package master.log

import java.time.{LocalDate, ZoneId}
import java.util.Date


object LogLine {
  private val logLineRegex = """(\d+\.\d+\.\d+\.\d+)\s-\s-\s\[(.*)\]\s.*\s(\d+)\s\"(.*)\"\s(\d+)\s(.+)""".r

  def apply(logLine: String): LogLine = {
    val regexMatchOption = logLineRegex.findFirstMatchIn(logLine)
    if(regexMatchOption.isEmpty){ println(logLine); throw new Exception("Error when extracting master.log information!")}
    val regexMatch = regexMatchOption.get

    val id = regexMatch.group(1)

    import java.time.format.DateTimeFormatter
    val formatter= DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z")
    val parsedDate = LocalDate.parse(regexMatch.group(2), formatter)
    val date = Date.from(parsedDate.atStartOfDay(ZoneId.systemDefault).toInstant)

    val query = regexMatch.group(3)
    val endpoint = EndpointEntry(regexMatch.group(4))
    val status = regexMatch.group(5)
    val payload = regexMatch.group(6)

    new LogLine(id, date, query, endpoint, status, payload)
  }
}

case class LogLine(ip: String, date: Date, query: String, endpoint: EndpointEntry, status: String, payload: String)