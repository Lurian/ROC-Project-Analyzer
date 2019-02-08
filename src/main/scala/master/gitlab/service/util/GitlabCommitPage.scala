package master.gitlab.service.util

import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import master.gitlab.GitlabConfig
import master.gitlab.request.util.GitlabGetRequest

object GitlabCommitPage {
  def createUri(projectId: String,
                page: String,
                perPage: String)(implicit gitlabConfig: GitlabConfig): Uri = {
    Uri(s"${gitlabConfig.url}/api/v4/projects/$projectId/repository/commits")
          .withQuery(Query(s"page=$page&per_page=$perPage"))
  }

  def apply(projectId: String,
            page: String,
            perPage: String)(implicit gitlabConfig: GitlabConfig): GitlabGetRequest = {
    new GitlabGetRequest(createUri(projectId, page, perPage))
  }
}



