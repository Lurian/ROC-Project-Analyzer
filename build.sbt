name := "http-sandbox"

version := "0.1"

scalaVersion := "2.12.3"

lazy val akkaVersion = "2.5.8"
lazy val akkaHttpVersion = "10.0.11"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  "org.scalatest" %% "scalatest" % "3.0.1" % "test",
  "com.typesafe.akka" %% "akka-http"   % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
  "org.mongodb.scala" %% "mongo-scala-driver" % "2.2.0"
)

libraryDependencies += "com.github.javaparser" % "javaparser-core" % "3.5.10"
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3"
libraryDependencies += "com.typesafe.akka" %% "akka-slf4j" % akkaVersion
libraryDependencies += "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.9.2"
libraryDependencies += "org.scalamock" %% "scalamock" % "4.0.0"
libraryDependencies += "com.github.nscala-time" %% "nscala-time" % "2.18.0"
libraryDependencies += "org.jacoco" % "org.jacoco.core" % "0.7.9"
libraryDependencies += "org.jacoco" % "org.jacoco.agent" % "0.7.9"
libraryDependencies += "com.github.tototoshi" %% "scala-csv" % "1.3.5"
libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % "1.1.0"
libraryDependencies += "org.yaml" % "snakeyaml" % "1.8"
libraryDependencies += "org.scoverage" %% "scalac-scoverage-plugin" % "1.4.0-M3" % "provided"




