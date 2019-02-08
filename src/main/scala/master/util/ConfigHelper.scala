package master.util

import java.io.{File, FileInputStream}

import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor

object ConfigHelper {

  def getYamlConfigFile[T](filepath: String, clazz: Class[_]) = {
    val input = new FileInputStream(new File(filepath))
    val yaml = new Yaml(new Constructor(clazz))
    yaml.load(input).asInstanceOf[T]
  }

}
