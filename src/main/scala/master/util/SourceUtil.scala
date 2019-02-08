package master.util

import java.io.{BufferedWriter, File, FileWriter}
import java.nio.file.{Files, Path, Paths}

import com.github.tototoshi.csv.CSVWriter
import master.project.ExecUtil
import master.project.task.ResearchUnit

import scala.io.Source
import scala.xml.{Elem, Node, XML}

object SourceUtil extends SourceApi {

  /**
    * Tries to load a file form the filepath informed. Return an [[Option]] containing the [[FileSource]] if it
    * was successful or [[None]] if a exception was thrown.
    *
    * @param filepath [[String]] path to the file
    * @return [[Option]] containing or not the [[FileSource]]
    */
  override def getSource(filepath: String): Option[FileSource] = {
    try {
      Some(FileSource(Source.fromFile(ExecUtil.changeSlashesToFileSeparator(filepath))))
    }
    catch {
      case _: Exception => None
    }
  }

  /**
    * Check if the file or directory exists.
    *
    * @param filePath [[String]]path to the file
    * @return [[Boolean]] representing if it exists or not.
    */
  override def fileExists(filePath: String): Boolean = {
    Files.exists(Paths.get(ExecUtil.changeSlashesToFileSeparator(filePath)))
  }

  /**
    * Check if the file or directory exists.
    *
    * @param filePath [[Path]]path to the file
    * @return [[Boolean]] representing if it exists or not.
    */
  override def fileExists(filePath: Path): Boolean = Files.exists(filePath)

  /**
    * Export a [[List]] of [[ResearchUnit]] to CSV format.
    *
    * @param researchUnitList List of units to be exported
    * @param exportPath       [[String]] filepath to export the CSV generated.
    */
  override def exportToCsv(researchUnitList: Iterable[ResearchUnit], exportPath: String): Unit = {
    val f = new File(exportPath)
    val writer = CSVWriter.open(f)
    writer.writeRow(researchUnitList.head.headers)
    researchUnitList map(_.toRow) foreach writer.writeRow
    writer.close()
  }

  /**
    * Write some text string to a File in the system.
    *
    * @param text Text to be written out.
    */
  override def writeToFile(text: String, filePath: String): Unit = {
    val file = new File(filePath)
    val bw = new BufferedWriter(new FileWriter(file))
    bw.write(text)
    bw.close()
  }

  /**
    * Load XML element from file.
    * @param path Path to file.
    * @return [[Elem]] XML Element.
    */
  override def loadXML(path: String): Elem = XML.loadFile(path)

  /**
    * Save XML element to file.
    * @param path Path to file.
    * @param xmlElement XML element to be saved.
    */
  override def saveXML(path: String, xmlElement: Node): Unit = XML.save(path, xmlElement)
}
