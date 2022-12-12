ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.1.2"

Test / parallelExecution := true

val AkkaVersion = "2.7.0"
val AkkaHttpVersion = "10.4.0"
val AkkaProjectionVersion = "1.3.1"
val SlickVersion = "3.4.1"

val commonDependencies = Seq(
  "ch.qos.logback" % "logback-classic" % "1.3.0-alpha16",
  "org.scalatest" %% "scalatest" % "3.2.14" % Test,
  "org.scalatest" %% "scalatest-featurespec" % "3.2.14" % Test,
  ("com.typesafe.akka" %% "akka-stream" % AkkaVersion)
        .cross(CrossVersion.for3Use2_13),
  ("com.typesafe.akka" %% "akka-http" % AkkaHttpVersion)
        .cross(CrossVersion.for3Use2_13),
  ("com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion)
        .cross(CrossVersion.for3Use2_13),
  ("com.typesafe.akka" %% "akka-persistence-typed" % AkkaVersion)
        .cross(CrossVersion.for3Use2_13),
  ("com.lightbend.akka" %% "akka-persistence-jdbc" % "5.2.0")
        .cross(CrossVersion.for3Use2_13),
  ("com.typesafe.akka" %% "akka-persistence-testkit" % AkkaVersion % Test)
        .cross(CrossVersion.for3Use2_13),
  ("com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion)
        .cross(CrossVersion.for3Use2_13),
  ("com.typesafe.akka" %% "akka-cluster-sharding-typed" % AkkaVersion)
        .cross(CrossVersion.for3Use2_13),
  ("com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion)
        .cross(CrossVersion.for3Use2_13),
  ("com.typesafe.akka" %% "akka-http-testkit" % AkkaHttpVersion)
        .cross(CrossVersion.for3Use2_13),
  ("com.lightbend.akka" %% "akka-projection-core" % AkkaProjectionVersion)
        .cross(CrossVersion.for3Use2_13),
  ("com.lightbend.akka" %% "akka-projection-jdbc" % AkkaProjectionVersion)
        .cross(CrossVersion.for3Use2_13),
  ("com.lightbend.akka" %% "akka-projection-testkit" % AkkaProjectionVersion % Test)
        .cross(CrossVersion.for3Use2_13),
  ("com.lightbend.akka" %% "akka-projection-eventsourced" % AkkaProjectionVersion)
        .cross(CrossVersion.for3Use2_13),
  ("org.scalikejdbc" %% "scalikejdbc" % "3.5.0")
        .cross(CrossVersion.for3Use2_13),
  "com.h2database" % "h2" % "2.1.212",
  "com.zaxxer" % "HikariCP" % "5.0.1"
)

lazy val author = project.settings(
  libraryDependencies ++= commonDependencies
)

lazy val school = project.settings(
  libraryDependencies ++= commonDependencies
)

lazy val exam = project.settings(
)


