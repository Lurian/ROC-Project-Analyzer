package master.gitlab.util

case object Constants {
  def PER_PAGE: String = "100"
  def NUM_REQUEST_PARALLEL: Int = 5
  def LOG_IT: Boolean = false
  def MONGO_LOG_IT: Boolean = false
}
