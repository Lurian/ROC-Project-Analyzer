package master.project

import java.util.Date

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import master.mock.ModelMock
import master.mongodb.commit.CommitModel
import master.project.task.BugAnalysisTaskManager
import org.joda.time.DateTime
import org.scalatest.{BeforeAndAfterAll, FreeSpecLike, Matchers}

class BugAnalyzerTaskManagerTest() extends TestKit(ActorSystem("BugAnalyzerTaskManagerTest")) with ImplicitSender
  with FreeSpecLike with Matchers with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "A BugAnalysisTaskManager" - {
    val mongoManager = TestProbe()
    val gitlabManager = TestProbe()
    val projectManager = TestProbe()
    val actorRef = TestActorRef(new BugAnalysisTaskManager("7", mongoManager.ref, gitlabManager.ref, projectManager.ref))
    val bugAnalysisTaskManager = actorRef.underlyingActor

    "have a 'toAnalyzedCommitModel' function" - {
      "that should return a CommitModel tagged as BugFix" - {
        "when receiving a CommitModel of a bug fix" in {
          val bugId = "2223"
          val commitModel = ModelMock.commitModelMock(title = s"[Correcao] Bug $bugId", isBugFix = false)
          val bugModel = ModelMock.bugModelMock(id = bugId)

          val expectedCommitModel = CommitModel(commitModel, true, Some(List(bugId)))
          bugAnalysisTaskManager.toAnalyzedCommitModel(List(bugModel))(commitModel) should equal(expectedCommitModel)
        }
      }
    }

    "have a 'checkIfIsBugFixCommit' function" - {
      "that should return true" - {
        "when the title contains 'correção' and have an identifier" in {
          val title = "correção - 234"
          val bugIdList = List("234")
          bugAnalysisTaskManager.checkIfIsBugFixCommit(title, bugIdList) should equal(true)
        }

        "when the title contains 'correcao' and have an identifier" in {
          val title = "correcao - 234"
          val bugIdList = List("234")
          bugAnalysisTaskManager.checkIfIsBugFixCommit(title, bugIdList) should equal(true)
        }

        "when the title contains 'bug' and have an identifier" in {
          val title = "bug - 234"
          val bugIdList = List("234")
          bugAnalysisTaskManager.checkIfIsBugFixCommit(title, bugIdList) should equal(true)
        }
      }
      "that should return false" - {
        "when the title contains a valid bug tag but have no identifier" in {
          val titleWithBugKeyword = "bug - resolved"
          val titleWithCorrecaoKeyword1 = "correcao - resolved"
          val titleWithCorrecaoKeyword2 = "correção - resolved"
          val bugIdList = List("234")
          bugAnalysisTaskManager.checkIfIsBugFixCommit(titleWithBugKeyword, bugIdList) should equal (false)
          bugAnalysisTaskManager.checkIfIsBugFixCommit(titleWithCorrecaoKeyword1, bugIdList) should equal (false)
          bugAnalysisTaskManager.checkIfIsBugFixCommit(titleWithCorrecaoKeyword2, bugIdList) should equal (false)
        }
      }
    }

    "have a 'getPossibleBugIdentifiers' function" - {
      "that should not return identifiers within a string" in {
        val titleWithIdentifiers = "bugs fixed -  10, 14, a2234b"
        val expectedReturn = List("10", "14", "2234")
        bugAnalysisTaskManager.getPossibleBugIdentifiers(titleWithIdentifiers) should equal (expectedReturn)
      }

      "that should not return identifiers which are less than 10" in {
        val titleWithOnlySmallIdentifiers = "bugs fixed - 2, 7"
        bugAnalysisTaskManager.getPossibleBugIdentifiers(titleWithOnlySmallIdentifiers) should equal (List.empty)

        val titleWithSmallIdentifiers = "bugs fixed - 2, 7, 10, 14, 2234"
        val expectedReturn = List("10", "14", "2234")
        bugAnalysisTaskManager.getPossibleBugIdentifiers(titleWithSmallIdentifiers) should equal (expectedReturn)
      }
    }

    "have a 'filterBugs' function" - {
      "that should return false" - {
        "when the severity of the bug is equal to 'melhoria'" in {
          val bugModel = ModelMock.bugModelMock(severity = "melhoria", resolution = "FIXED")
          bugAnalysisTaskManager.byRelevance(bugModel) should equal (false)
        }

        "when the resolution of the bug is equal to 'INVALID'" in {
          val bugModel = ModelMock.bugModelMock(severity = "critical", resolution = "INVALID")
          bugAnalysisTaskManager.byRelevance(bugModel) should equal (false)
        }

        "when the resolution of the bug is equal to 'DUPLICATE'" in {
          val bugModel = ModelMock.bugModelMock(severity = "critical", resolution = "DUPLICATE")
          bugAnalysisTaskManager.byRelevance(bugModel) should equal (false)
        }
      }

      "that should return true otherwise" in {
        val bugModel = ModelMock.bugModelMock(severity = "critical", resolution = "FIXED")
        bugAnalysisTaskManager.byRelevance(bugModel) should equal (true)
      }
    }

    "have a 'createFromToListFromVersions' function" - {
      "that should receive a ordered version list and return a fromToList which zips the list with itself taking pair by pair" in {
        val version1 = ModelMock.versionModelMock(versionName = "1.1.0")
        val version2 = ModelMock.versionModelMock(versionName = "1.1.1")
        val version3 = ModelMock.versionModelMock(versionName = "1.2.0")
        val version4 = ModelMock.versionModelMock(versionName = "1.3.0")

        val versionList = List(version1, version2, version3, version4 )
        val expectedList = List((version1, version2), (version2, version3), (version3, version4))
        bugAnalysisTaskManager.createFromToListFromVersions(versionList) should equal(expectedList)
      }

      "that should receive a unordered version list and return a fromToList which zips the list after ordering it with itself taking pair by pair" in {
        val version1 = ModelMock.versionModelMock(versionName = "1.1.0")
        val version2 = ModelMock.versionModelMock(versionName = "1.1.1")
        val version3 = ModelMock.versionModelMock(versionName = "1.2.0")
        val version4 = ModelMock.versionModelMock(versionName = "1.3.0")

        val versionList = List(version3, version4, version1, version2)
        val expectedList = List((version1, version2), (version2, version3), (version3, version4))
        bugAnalysisTaskManager.createFromToListFromVersions(versionList) should equal(expectedList)
      }
    }

    "have a 'getVersionNameWhereBugWasCreated' function" - {
      val date1: Date = new DateTime("2018-01-01T12:22:49+00:00").toDate
      val date2: Date = new DateTime("2018-01-07T12:22:49+00:00").toDate
      val date3: Date = new DateTime("2018-01-14T12:22:49+00:00").toDate

      val version1 = ModelMock.versionModelMock(fromDate = date1, versionName = "1.0.0")
      val version2 = ModelMock.versionModelMock(fromDate = date2, versionName = "1.0.1")
      val version3 = ModelMock.versionModelMock(fromDate = date3, versionName = "1.0.2")
      val fromToList = bugAnalysisTaskManager.createFromToListFromVersions(List(version1, version2, version3))

      "that should return Some[String] containing the versionName of the version in which the bug was reported " in {
        val date1_2 = new DateTime("2018-01-05T12:22:49+00:00").toDate
        date1_2 should be < date2
        date1_2 should be > date1

        val bugModel = ModelMock.bugModelMock(creationTime = date1_2)
        val versionReturned = bugAnalysisTaskManager.getVersionNameWhereBugWasCreated(bugModel, fromToList)
        versionReturned should equal (Some(version1.versionName))
      }

      "that should return a None when the bug was reported before all the versions " in {
        val date0 = new DateTime("2017-12-31T12:22:49+00:00").toDate
        date0 should be < date1

        val bugModel = ModelMock.bugModelMock(creationTime = date0)
        val versionReturned = bugAnalysisTaskManager.getVersionNameWhereBugWasCreated(bugModel, fromToList)
        versionReturned should equal (None)
      }

      "that should return Some[String] containing the last versionName when the bug was reported after all versions " in {
        val date4 = new DateTime("2018-02-01T12:22:49+00:00").toDate
        date4 should be > date3
        date4 should be > date2
        date4 should be > date1

        val bugModel = ModelMock.bugModelMock(creationTime = date4)
        val versionReturned = bugAnalysisTaskManager.getVersionNameWhereBugWasCreated(bugModel, fromToList)
        versionReturned should equal (Some(version3.versionName))
      }
    }

    "have a 'toAnalyzedVersionModel' function" - {
      "that should return the analyzed version model " - {
        val bugFixCommitId1 = "1"; val bugFixCommit1 = ModelMock.commitModelMock(id = bugFixCommitId1, isBugFix = true)
        val bugFixCommitId2 = "2"; val bugFixCommit2 = ModelMock.commitModelMock(id = bugFixCommitId2, isBugFix = true)
        val notBugFixCommitId = "3"; val notBugFixCommit = ModelMock.commitModelMock(id = notBugFixCommitId, isBugFix = false)
        val bugAnalyzedCommitList = List(bugFixCommit1, bugFixCommit2, notBugFixCommit)

        val versionName1 = "1.30.0"
        val versionName2 = "1.31.0"
        val bugVersionTag1 = ModelMock.bugModelMock(versionName = Some(versionName1))
        val bugVersionTag2 = ModelMock.bugModelMock(versionName = Some(versionName2))
        val bugListWithVersionTag = List(bugVersionTag1, bugVersionTag2)

        "with 0 bugCount when there is no bug with the versionName of the version being analyzed" in {
          val versionTag = "1.20.0"
          versionTag should not equal versionName1
          versionTag should not equal versionName2

          val versionModel = ModelMock.versionModelMock(versionName = versionTag)
          val analyzedVersion = bugAnalysisTaskManager.toAnalyzedVersionModel(bugAnalyzedCommitList, bugListWithVersionTag)(versionModel)

          analyzedVersion.bugCount should equal (0)
        }

        "with bugCount equal to the count of bugs that have the same versionName of the version being analyzed" in {
          val versionModel = ModelMock.versionModelMock(versionName = versionName1)
          val analyzedVersion = bugAnalysisTaskManager.toAnalyzedVersionModel(bugAnalyzedCommitList, bugListWithVersionTag)(versionModel)

          val bugCount = bugListWithVersionTag count {_.versionName.getOrElse("") == versionName1}
          analyzedVersion.bugCount should equal (bugCount)

          val newBugListWithVersionTag = bugListWithVersionTag ++ Some(ModelMock.bugModelMock(versionName = Some(versionName1)))
          val analyzedVersionAfterAddingANewBug = bugAnalysisTaskManager.toAnalyzedVersionModel(bugAnalyzedCommitList, newBugListWithVersionTag)(versionModel)

          analyzedVersionAfterAddingANewBug.bugCount should equal (bugCount + 1)
        }

        "with bugFixCommitCount equal to the count of commits that are contained in the version and are bug fix commits" in {
          val versionModel = ModelMock.versionModelMock(idCommitList = List(bugFixCommitId1))
          val analyzedVersion1 = bugAnalysisTaskManager.toAnalyzedVersionModel(bugAnalyzedCommitList, bugListWithVersionTag)(versionModel)
          analyzedVersion1.bugFixCommitCount should equal (1)

          val versionModelWithNonBugFixCommit = ModelMock.versionModelMock(idCommitList = List(notBugFixCommitId))
          val analyzedVersion2 = bugAnalysisTaskManager.toAnalyzedVersionModel(bugAnalyzedCommitList, bugListWithVersionTag)(versionModelWithNonBugFixCommit)
          analyzedVersion2.bugFixCommitCount should equal (0)

          val versionModelWithTwoBugFixCommit = ModelMock.versionModelMock(idCommitList = List(bugFixCommitId1, bugFixCommitId2))
          val analyzedVersion3 = bugAnalysisTaskManager.toAnalyzedVersionModel(bugAnalyzedCommitList, bugListWithVersionTag)(versionModelWithTwoBugFixCommit)
          analyzedVersion3.bugFixCommitCount should equal (2)
        }
      }
    }
  }
}