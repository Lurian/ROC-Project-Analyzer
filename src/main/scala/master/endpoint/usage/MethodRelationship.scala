package master.endpoint.usage

import master.endpoint.AppMethod

case class MethodRelationship(sourceMethod: AppMethod, targetMethod: AppMethod)