ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.1.2"

val AkkaVersion = "2.7.0"
val AkkaHttpVersion = "10.4.0"

val commonDependencies = Seq(
  "ch.qos.logback" % "logback-classic" % "1.3.0-alpha16",
  "org.scalatest" %% "scalatest" % "3.2.14" % Test,
  "org.scalatest" %% "scalatest-featurespec" % "3.2.14" % Test,
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  ("com.typesafe.akka" %% "akka-http" % AkkaHttpVersion).cross(CrossVersion.for3Use2_13),
  ("com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion).cross(CrossVersion.for3Use2_13),
  "com.typesafe.akka" %% "akka-persistence-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-persistence-testkit" % AkkaVersion % Test,
  "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion,
  "com.typesafe.akka" %% "akka-cluster-sharding-typed" % AkkaVersion
)

lazy val author = project.settings(
  libraryDependencies ++= commonDependencies
)

lazy val school = project.settings(
  libraryDependencies ++= commonDependencies
)

lazy val exam = project.settings(
)


