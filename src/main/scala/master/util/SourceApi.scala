package master.util

import java.nio.file.Path

import master.project.task.ResearchUnit

import scala.xml.{Elem, Node}

trait SourceApi {
  /**
    * Tries to load a file form the filepath informed. Return an [[Option]] containing the [[FileSource]] if it
    * was successful or [[None]] if a exception was thrown.
    * @param filepath [[String]] path to the file
    * @return [[Option]] containing or not the [[FileSource]]
    */
  def getSource(filepath: String): Option[FileSource]

  /**
    * Check if the file or directory exists.
    * @param filePath [[String]]path to the file
    * @return [[Boolean]] representing if it exists or not.
    */
  def fileExists(filePath: String): Boolean

  /**
    * Check if the file or directory exists.
    * @param filePath [[Path]]path to the file
    * @return [[Boolean]] representing if it exists or not.
    */
  def fileExists(filePath: Path): Boolean

  /**
    * Export a [[List]] of [[ResearchUnit]] to CSV format.
    * @param researchUnitList List of units to be exported
    * @param exportPath [[String]] filepath to export the CSV generated.
    */
  def exportToCsv(researchUnitList: Iterable[ResearchUnit], exportPath: String): Unit

  /**
    * Write some text string to a File in the system.
    * @param text Text to be written out.
    */
  def writeToFile(text: String, filePath: String): Unit

  /**
    * Load XML element from file.
    * @param path Path to file.
    * @return [[Elem]] XML Element.
    */
  def loadXML(path: String): Elem

  /**
    * Save XML element to file.
    * @param path Path to file.
    * @param xmlElement XML element to be saved.
    */
  def saveXML(path: String, xmlElement: Node): Unit
}