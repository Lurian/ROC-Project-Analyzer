package master.mongodb.coverage

abstract class CoverageNode(_name: String,
                            _instructionCounter: CoverageCounter,
                            _branchCounter: CoverageCounter,
                            _lineCounter: CoverageCounter,
                            _complexityCounter: CoverageCounter,
                            _methodCounter: CoverageCounter,
                            _classCounter: CoverageCounter) {
  def name: String = _name
  def instructionCounter: CoverageCounter = _instructionCounter
  def branchCounter: CoverageCounter = _branchCounter
  def lineCounter: CoverageCounter = _lineCounter
  def complexityCounter: CoverageCounter = _complexityCounter
  def methodCounter: CoverageCounter = _methodCounter
  def classCounter: CoverageCounter = _classCounter


}
