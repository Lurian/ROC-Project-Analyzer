package master.project

trait ExecApi {

  def isWindows: Boolean

  def exec(command: String): Int

  def execReturn(command: String): String

  def getOSSpecificCommand(command: String): String

  def changeSlashesToFileSeparator(path: String): String
}
