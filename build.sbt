ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.1.2"

val akkaVersion = "2.7.0"

val commonDependencies = Seq(
  "org.scalatest" %% "scalatest" % "3.2.14" % Test,
  "org.scalatest" %% "scalatest-featurespec" % "3.2.14" % Test,
  "com.typesafe.akka" %% "akka-persistence-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence-testkit" % akkaVersion % Test
)

lazy val author = project.settings(
  libraryDependencies ++= commonDependencies
)

lazy val school = project.settings(
  libraryDependencies ++= commonDependencies
)

lazy val exam = project.settings(
)


