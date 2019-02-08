package master.mock

import java.util.Date

import master.endpoint.{AppMethod, Endpoint, JavaEndpoint, MethodTree}
import master.gitlab.request.GitlabRequest.RequestError
import master.gitlab.util.GitlabError
import master.javaParser.model.Impact
import master.mongodb.ChangeImpact
import master.mongodb.bug.BugModel
import master.mongodb.commit.CommitModel
import master.mongodb.coverage._
import master.mongodb.diff.DiffModel
import master.mongodb.elastic.{ElasticVersionAnalysis, Percentile}
import master.mongodb.version.VersionModel
import master.research.method.MethodResearchUnit
import master.research.version.VersionResearchUnit

object ModelMock {
  def methodResearchUnitMock(methodSignature: String = "className.methodName()",
                             versionName: String = "1.0.0",
                             bugQty: Int = 0,
                             totalUsage: Int = 0,
                             timeSpentOnProduction: Long = 0,
                             instructionCoverage: Double = 0.5,
                             branchCoverage: Double = 0.5) = MethodResearchUnit(methodSignature, versionName, bugQty,
    totalUsage, timeSpentOnProduction,instructionCoverage, branchCoverage)

  def versionResearchUnitMock(versionName: String = "1.0.0",
                              bugQty: Int = 0,
                              totalUsage: Int = 0,
                              timeSpentOnProduction: Long = 0,
                              oldInstructionCoverage: Double = 0.5,
                              newInstructionCoverage: Double = 0.5,
                              operInstructionCoverage: Double = 0.5,
                              oldBranchCoverage: Double = 0.5,
                              newBranchCoverage: Double = 0.5,
                              operBranchCoverage: Double = 0.5): VersionResearchUnit =
    VersionResearchUnit(versionName, bugQty, totalUsage, timeSpentOnProduction, oldInstructionCoverage,
      newInstructionCoverage, operInstructionCoverage, oldBranchCoverage, newBranchCoverage, operBranchCoverage)

  def gitlabErrorMock(identifier: String = "id",
                      err: RequestError = RequestError("uri"),
                      errMsg: String = "errMsg"): GitlabError =
    GitlabError(identifier, err, errMsg)

  def impactMock(methods: List[String] = List.empty,
                 fileName: String = "filename") =
    Impact(methods, fileName)

  def changeImpactMock(projectId: String = "1",
                       identifier: String = "id",
                       impactList: List[Impact] = List.empty) : ChangeImpact =
    ChangeImpact(projectId, identifier, impactList)

  def diffModelMock(projectId: String = "1",
                    identifier: String = "1234567890",
                    old_path: String = "old/path",
                    new_path: String = "new/path",
                    diff: String = "diff",
                    new_file: Boolean = false,
                    renamed_file: Boolean = false,
                    deleted_file: Boolean = false): DiffModel =
    DiffModel(projectId, identifier, old_path, new_path, diff, new_file, renamed_file, deleted_file)

  def bugModelMock(assignedTo: String = "assignee",
                   classification: String = "normal",
                   component: String = "feedback",
                   creationTime: Date = new Date(),
                   lastChangeTime: Date = new Date(),
                   id: String = "1",
                   priority: String = "normal",
                   resolution: String = "",
                   severity: String = "normal",
                   status: String = "fixed",
                   summary: String = "summary",
                   versionName: Option[String] = None): BugModel =
    BugModel(assignedTo, classification, component, creationTime, lastChangeTime, id, priority,
      resolution, severity, status, summary, versionName)

  def versionModelMock(projectId: String = "1",
                       versionName: String = "1.0.0",
                       nextVersionName: Option[String] = Some("1.1.0"),
                       previousVersionName: Option[String] = Some("1.0.0"),
                       fromDate: Date = new Date(),
                       toDate: Date = new Date(),
                       idCommitList: List[String] = List(),
                       diffList: List[DiffModel] = List(),
                       bugFixCommitCount: Int = -1,
                       bugCount: Int = -1): VersionModel =
    VersionModel(projectId, versionName, nextVersionName, previousVersionName, fromDate, Some(toDate), idCommitList, diffList, bugFixCommitCount, bugCount)

  def commitModelMock(projectId: String = "1",
                      author_email: String = "author",
                      id: String = "id1",
                      title: String = "title",
                      created_at: Date = new Date(),
                      message: String = "message",
                      isBugFix: Boolean = false,
                      isMerge: Boolean = false,
                      bugIds: Option[List[String]] = None): CommitModel =
    new CommitModel(projectId, author_email, id, title, created_at, message, isBugFix, isMerge, bugIds)

  def endpointMock(restMethod: AppMethod = appMethodMock(),
                   endpointType: String = "REST",
                   endpoint: String = "/master/mock/",
                   verb: String = "GET",
                   impactedMethodsListOption: Option[List[AppMethod]] = None,
                   usageOption: Option[Int] = None): Endpoint = {
    Endpoint(restMethod,
      endpointType,
      endpoint,
      verb,
      impactedMethodsListOption,
      usage = usageOption)
  }

  def javaEndpointMock(classDeclaration: String = "org.test",
                       methodDeclaration: String = "method()",
                       methodType: String = "REST",
                       endpoint: String = "/master/mock/",
                       verb: String = "GET"): JavaEndpoint = {
    new JavaEndpoint(classDeclaration,
      methodDeclaration,
      methodType,
      endpoint,
      verb)
  }

  def appMethodMock(classDeclaration: String = "org.mock.ClassMock",
                    methodDeclaration: String = "mockMethod(mockParameter)"): AppMethod = {
    AppMethod(classDeclaration, methodDeclaration)
  }


  def methodTreeMock(method: AppMethod = appMethodMock(), leafs: Option[List[MethodTree]] = None,
                     root: Option[AppMethod] = None) =
    new MethodTree(method, leafs, root)


  def coverageModelMock(projectId: String = "1", creationTime: Date = new Date(), identifier: String = "1.0.0", bundleCoverage: BundleCoverage = bundleCoverageMock()) =
    CoverageModel(projectId, creationTime, identifier, bundleCoverage)

  def bundleCoverageMock(name: String = "bundle",
                         instructionCounter: CoverageCounter = coverageCounterMock(CoverageType.INSTRUCTION),
                         branchCounter: CoverageCounter = coverageCounterMock(CoverageType.BRANCH),
                         lineCounter: CoverageCounter = coverageCounterMock(CoverageType.LINE),
                         complexityCounter: CoverageCounter = coverageCounterMock(CoverageType.COMPLEXITY),
                         methodCounter: CoverageCounter = coverageCounterMock(CoverageType.METHOD),
                         classCounter: CoverageCounter = coverageCounterMock(CoverageType.CLASS),
                         packageList: List[PackageCoverage] = List.empty): BundleCoverage =
    new BundleCoverage(name, instructionCounter, branchCounter, lineCounter, complexityCounter, methodCounter, classCounter, packageList)

  def packageCoverageMock(name: String = "package",
                          instructionCounter: CoverageCounter = coverageCounterMock(CoverageType.INSTRUCTION),
                          branchCounter: CoverageCounter = coverageCounterMock(CoverageType.BRANCH),
                          lineCounter: CoverageCounter = coverageCounterMock(CoverageType.LINE),
                          complexityCounter: CoverageCounter = coverageCounterMock(CoverageType.COMPLEXITY),
                          methodCounter: CoverageCounter = coverageCounterMock(CoverageType.METHOD),
                          classCounter: CoverageCounter = coverageCounterMock(CoverageType.CLASS),
                          classList: List[ClassCoverage] = List.empty): PackageCoverage = {
    PackageCoverage(name, instructionCounter, branchCounter, lineCounter, complexityCounter, methodCounter, classCounter, classList)
  }

  def classCoverageMock(name: String = "class",
                        instructionCounter: CoverageCounter = coverageCounterMock(CoverageType.INSTRUCTION),
                        branchCounter: CoverageCounter = coverageCounterMock(CoverageType.BRANCH),
                        lineCounter: CoverageCounter = coverageCounterMock(CoverageType.LINE),
                        complexityCounter: CoverageCounter = coverageCounterMock(CoverageType.COMPLEXITY),
                        methodCounter: CoverageCounter = coverageCounterMock(CoverageType.METHOD),
                        classCounter: CoverageCounter = coverageCounterMock(CoverageType.CLASS),
                        id: Long = 1,
                        isNoMatch: Boolean = false,
                        signature: Option[String] = None,
                        superName: String = "superClass",
                        interfaceNames: List[String] = List.empty,
                        packageName: String = "package",
                        sourceFileName: String = "class.java",
                        methodList: List[MethodCoverage] = List.empty): ClassCoverage = {
    ClassCoverage(name, instructionCounter, branchCounter, lineCounter, complexityCounter, methodCounter, classCounter,
      id, isNoMatch, signature, superName, interfaceNames, packageName, sourceFileName, methodList)
  }

  def methodCoverageMock(name: String = "class",
                         instructionCounter: CoverageCounter = coverageCounterMock(CoverageType.INSTRUCTION),
                         branchCounter: CoverageCounter = coverageCounterMock(CoverageType.BRANCH),
                         lineCounter: CoverageCounter = coverageCounterMock(CoverageType.LINE),
                         complexityCounter: CoverageCounter = coverageCounterMock(CoverageType.COMPLEXITY),
                         methodCounter: CoverageCounter = coverageCounterMock(CoverageType.METHOD),
                         classCounter: CoverageCounter = coverageCounterMock(CoverageType.CLASS),
                         descriptor: String = "",
                         signature: Option[String] = None): MethodCoverage = {
    MethodCoverage(name, instructionCounter, branchCounter, lineCounter, complexityCounter, methodCounter, classCounter, descriptor, signature)
  }

  def coverageCounterMock(coverageType: String = CoverageType.INSTRUCTION,
                          totalCount: Int = 10,
                          coveredCount: Int = 5,
                          missedCount: Int = 5): CoverageCounter =
    CoverageCounter(coverageType, totalCount, coveredCount, missedCount)

  def elasticVersionAnalysisMock(projectId: String = "1",
                                 versionName: String = "1.0.0",
                                 usagePercentiles: List[Percentile] = List.empty,
                                 endpointUsageList: List[Endpoint] = List.empty): ElasticVersionAnalysis = {
    ElasticVersionAnalysis(projectId, versionName, usagePercentiles, endpointUsageList)
  }
}
