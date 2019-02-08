package master.research.report

import master.research.report.EndpointCoverageExtractor.EndpointCoverage
import master.research.report.VersionCoverageExtractor.VersionCoverage
import master.research.version.VersionResearchUnit
import master.util.SourceUtil

case class VersionReport(targetVersionEndpointCoverage: Option[EndpointCoverage], previousVersionEndpointCoverage: Option[EndpointCoverage],
                         targetVersionResearchUnit: Option[VersionResearchUnit], previousVersionResearchUnit: Option[VersionResearchUnit],
                         targetVersionCoverage: Option[VersionCoverage], previousVersionCoverage: Option[VersionCoverage]) {
  def isComplete: Boolean = targetVersionEndpointCoverage.isDefined &&
    previousVersionEndpointCoverage.isDefined &&
    targetVersionResearchUnit.isDefined &&
    previousVersionResearchUnit.isDefined &&
    targetVersionResearchUnit.isDefined &&
    previousVersionCoverage.isDefined


 def getReport: String = {
   val targetVersion = targetVersionEndpointCoverage.get.versionName
   val previousVersion = previousVersionEndpointCoverage.get.versionName

   val prettyMap = (kv: (String, Double)) => s" ${kv._1} - Coverage: ${kv._2}\n"

   val targetEndpointList = targetVersionEndpointCoverage.get.endpointCoverageMap
   val previousEndpointList = previousVersionEndpointCoverage.get.endpointCoverageMap

   def combine(x: Map[String, Double], y: Map[String, Double]): Map[String, (Double, Double, Double)] = {
     val keys = x.keys.toSet.union(y.keys.toSet)
     keys.map{ k => k -> (x.getOrElse(k, 0.0) , y.getOrElse(k, 0.0), y.getOrElse(k, 0.0) - x.getOrElse(k, 0.0)) }.toMap
   }

   val endpointString = combine(previousEndpointList, targetEndpointList).toList.map((kv) =>
     f"${kv._1} | ${kv._2._1}%1.5f | ${kv._2._2}%1.5f | ${kv._2._3}%1.5f\n").reduce(_+_)

   val currentOC = targetVersionResearchUnit.get.operationalInstructionCoverage
   val currentPC = targetVersionResearchUnit.get.newInstructionCoverage
   val currentCC = targetVersionResearchUnit.get.oldInstructionCoverage

   val previousOC = previousVersionResearchUnit.get.operationalInstructionCoverage
   val previousPC = previousVersionResearchUnit.get.newInstructionCoverage
   val previousCC = previousVersionResearchUnit.get.oldInstructionCoverage

   val currentOCp = f"$currentOC%1.5f"
   val currentPCp = f"$currentPC%1.5f"
   val currentCCp = f"$currentCC%1.5f"

   val previousOCp = f"$previousOC%1.5f"
   val previousPCp = f"$previousPC%1.5f"
   val previousCCp = f"$previousCC%1.5f"

   val OCDiff = f"${currentOC-previousOC}%1.5f"
   val PCDiff = f"${currentPC-previousPC}%1.5f"
   val CCDIff = f"${currentCC-previousCC}%1.5f"

   val currentChangeOC = targetVersionCoverage.get.operationalCoveredRatio
   val currentChangePC = targetVersionCoverage.get.proposedCoveredRatio
   val currentChangeCC = targetVersionCoverage.get.classicCoveredRatio

   val previousChangeOC = previousVersionCoverage.get.operationalCoveredRatio
   val previousChangePC = previousVersionCoverage.get.proposedCoveredRatio
   val previousChangeCC = previousVersionCoverage.get.classicCoveredRatio

   val currentChangeOCp = f"$currentChangeOC%1.5f"
   val currentChangePCp = f"$currentChangePC%1.5f"
   val currentChangeCCp = f"$currentChangeCC%1.5f"

   val previousChangeOCp = f"$previousChangeOC%1.5f"
   val previousChangePCp = f"$previousChangePC%1.5f"
   val previousChangeCCp = f"$previousChangeCC%1.5f"

   val OCChangeDiff = f"${currentChangeOC-previousChangeOC}%1.5f"
   val PCChangeDiff = f"${currentChangePC-previousChangePC}%1.5f"
   val CCChangeDiff = f"${currentChangeCC-previousChangeCC}%1.5f"

   val report = raw"""
# Coverage Report for Version $targetVersion

## System Wide Coverage

**Operational Statement Coverage**: $previousOCp -> $currentOCp = $OCDiff

**Ranking-based Operational Statement Coverage**: $previousPCp -> $currentPCp = $PCDiff

**Classical Statement Coverage**: $previousCCp -> $currentCCp = $CCDIff

##  Main Endpoints Coverage

Endpoint | Previous | Current | Difference
--- | --- | --- | ---
$endpointString

## Changes Coverage

**Operational Statement Coverage**: $previousChangeOCp -> $currentChangeOCp = $OCChangeDiff

**Ranking-based Operational Statement Coverage**: $previousChangePCp -> $currentChangePCp = $PCChangeDiff

**Classical Statement Coverage**: $previousChangeCCp -> $currentChangeCCp = $CCChangeDiff
"""

    SourceUtil.writeToFile(report, s"./target/reportTest-$targetVersion.md")
    report
 }
}