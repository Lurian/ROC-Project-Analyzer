package master.gitlab.request.util

import akka.http.scaladsl.model.Uri

object GitlabGetRequest {
  def apply(uri: Uri): GitlabGetRequest = new GitlabGetRequest(uri)

  def unapply(value: GitlabGetRequest): Option[Uri] = Some(value.uri)
}

class GitlabGetRequest(_uri: Uri) {
  def uri: Uri = _uri
}