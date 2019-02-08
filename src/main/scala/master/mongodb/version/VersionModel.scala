package master.mongodb.version

import java.util.Date

import master.mongodb.diff.DiffModel


object VersionModel {
  def orderVersion(firstVersion: String, secondVersion: String): Boolean = {
    val (firstMajorVersion: Int, firstMinorVersion: Int, firstPatchVersion: Int) = splitIt(firstVersion)
    val (secondMajorVersion: Int, secondMinorVersion: Int, secondPatchVersion: Int) = splitIt(secondVersion)

    if (firstMajorVersion != secondMajorVersion) firstMajorVersion < secondMajorVersion
    else if (firstMinorVersion != secondMinorVersion) firstMinorVersion < secondMinorVersion
    else firstPatchVersion < secondPatchVersion
  }

  def splitIt(version: String): (Int, Int, Int) = {
    val splitList = version.split('.').toList
    (splitList.head.toInt, splitList(1).toInt, splitList(2).toInt)
  }

  def apply(projectId: String, versionName: String, nextVersionName: Option[String], previousVersionName: Option[String],
            fromTime: Date, toTime: Option[Date], idCommitList: List[String], diffList: List[DiffModel]): VersionModel =
    new VersionModel(projectId, versionName, nextVersionName, previousVersionName, fromTime, toTime, idCommitList, diffList)

  def getLastMinorVersion(versionName: String): String = {
    val (majorVersion: Int, minorVersion: Int, patchVersion: Int) = splitIt(versionName)
    s"$majorVersion.${minorVersion-1}.$patchVersion"
  }
}

case class VersionModel(
                         projectId: String,
                         versionName: String,
                         previousVersionName: Option[String],
                         nextVersionName: Option[String],
                         fromDate: Date,
                         toDate: Option[Date],
                         idCommitList: List[String],
                         diffList: List[DiffModel],
                         bugFixCommitCount: Int = -1,
                         bugCount: Int = -1) {
  def addBugAnalysis(bugFixCommitCount: Int, bugCount: Int) =
    new VersionModel(this.projectId, this.versionName, this.nextVersionName, this.previousVersionName, this.fromDate, this.toDate,
      this.idCommitList, this.diffList, bugFixCommitCount, bugCount)
}