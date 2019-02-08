package master.endpoint

object AppMethod {
  def apply(classDeclaration: String, methodDeclaration: String): AppMethod = {
    val SECOND_INDEX = 1
    val FIRST_INDEX = 0

    val methodName: String = methodDeclaration.split('(')(FIRST_INDEX)
    val parametersStringWOParenthesis: String = methodDeclaration.split('(')(SECOND_INDEX).replace(")", "")
    val parameterList: List[String] = parametersStringWOParenthesis.split(',') map {_.trim} toList

    // Service interface should be replaced with their respective Bean, by convention the bean associated
    // with a service have the same name but with 'Bean' instead of 'Service' on their name
    val myClassDeclaration = classDeclaration.replace("Service", "Bean")
    new AppMethod(myClassDeclaration, methodName, parameterList)
  }
}

case class AppMethod(classDeclaration: String, methodName: String, parameters: List[String] ) {
  def className: String = classDeclaration.split('.').last
  def classPackage: String = classDeclaration.reverse.dropWhile(_ != '.').tail.reverse
  def signature: String = s"$classDeclaration.$methodName"

  def methodNameEqual(other: AppMethod): Boolean = other.methodName.equals(this.methodName)
  def classNameEqual(other: AppMethod): Boolean = other.className.equals(this.className)
  def signatureEqual(other: AppMethod): Boolean = other.signature.equals(this.signature)
  def classMethodEqual(other: AppMethod): Boolean = this.methodNameEqual(other) && this.classNameEqual(other)
  def parameterEqual(other: AppMethod): Boolean = {
    (this.parameters forall {other.parameters.contains(_)}) && (other.parameters forall {this.parameters.contains(_)})
  }

  override def equals(obj: scala.Any): Boolean = {
    obj match {
      case appMethod@AppMethod(_, _, _) => appMethod.signatureEqual(this)
      case _ => false
    }
  }

  override def toString: String = {
    this.signature
  }
}
