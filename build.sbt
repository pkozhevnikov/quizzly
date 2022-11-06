ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.1.2"

val akkaVersion := "2.7.0"

val commonDependencies = Seq(
  "org.scalatest" %% "scalatest" % "3.2.14" % "test"
)

lazy val author = project.settings(
  libraryDependencies ++= commonDependencies
)

lazy val school = project.settings(
)

lazy val exam = project.settings(
)


