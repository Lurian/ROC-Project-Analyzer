package master.project

import java.io.File

object ExecUtil extends ExecApi {

  import sys.process._

  def isWindows: Boolean = System.getProperty("os.name").toLowerCase.contains("windows")

  def exec(command: String): Int = {println(getOSSpecificCommand(command)); getOSSpecificCommand(command) !}

  def execReturn(command: String): String = getOSSpecificCommand(command) !!

  def getOSSpecificCommand(command: String): String =  if(isWindows) s"cmd /C $command" else s"$command"

  def changeSlashesToFileSeparator(path: String): String =  path.replace("/", File.separator)
}
