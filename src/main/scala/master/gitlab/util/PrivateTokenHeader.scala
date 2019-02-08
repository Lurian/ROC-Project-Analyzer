package master.gitlab.util

import akka.http.scaladsl.model.headers.CustomHeader

case class PrivateTokenHeader(value: String) extends CustomHeader() {
  override def name(): String = "PRIVATE-TOKEN"

  override def renderInRequests(): Boolean = true

  override def renderInResponses(): Boolean = false
}