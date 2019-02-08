package master.gitlab.util

import master.gitlab.request.GitlabRequest.RequestError

case class GitlabError(identifier: String, err: RequestError, errMsg: String)
