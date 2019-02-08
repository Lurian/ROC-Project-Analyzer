package master.endpoint


object Endpoint {
  def apply(javaEndpoint: JavaEndpoint): Endpoint =
    new Endpoint(
      AppMethod(javaEndpoint.getClassDeclaration, javaEndpoint.getMethodDeclaration),
      javaEndpoint.getType,
      javaEndpoint.getEndpoint,
      javaEndpoint.getVerb)
}

case class Endpoint(restMethod: AppMethod,
                    endpointType: String,
                    endpoint: String,
                    verb: String,
                    impactedMethodsListOption: Option[List[AppMethod]] = None,
                    usage: Option[Int] = None){

  def addImpactedMethodsList(impactedMethodsList: List[AppMethod]): Endpoint =
    Endpoint(restMethod, endpointType, endpoint, verb, impactedMethodsListOption = Some(impactedMethodsList), usage = usage)
  def addUsage(usage: Option[Int]): Endpoint =
    Endpoint(restMethod, endpointType, endpoint, verb, usage = usage)
}
