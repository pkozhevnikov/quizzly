ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.1.2"

ThisBuild / scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.6.0"

Test / parallelExecution := true

val AkkaVersion = "2.7.0"
val AkkaHttpVersion = "10.4.0"
val AkkaProjectionVersion = "1.3.1"
val SlickVersion = "3.4.1"

val commonDependencies = Seq(
  "ch.qos.logback" % "logback-classic" % "1.3.0-alpha16",
  "org.scalatest" %% "scalatest" % "3.2.15" % Test,
  "org.scalatest" %% "scalatest-featurespec" % "3.2.15" % Test,
  "org.scalatestplus" %% "mockito-4-6" % "3.2.15.0" % Test,
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
  //("com.lightbend.akka.grpc" %% "akka-grpc-runtime" % "2.2.1").cross(CrossVersion.for3Use2_13),
  //("com.thesamet.scalapb" %% "lenses" % "0.11.11").cross(CrossVersion.for3Use2_13),
  //("org.scala-lang.modules" %% "scala-collection-compat" % "2.7.0").cross(CrossVersion.for3Use2_13),
  //("com.thesamet.scalapb" %% "scalapb-runtime" % "0.11.11").cross(CrossVersion.for3Use2_13),
  ("com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf")
    .cross(CrossVersion.for3Use2_13),
  ("ch.megard" %% "akka-http-cors" % "1.1.3").cross(CrossVersion.for3Use2_13),
  "com.h2database" % "h2" % "2.1.212",
  "com.zaxxer" % "HikariCP" % "5.0.1",
  "com.github.pjfanning" %% "jackson-module-scala3-enum" % "2.13.+",
  "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % "2.13.+",
  "org.flywaydb" % "flyway-core" % "9.7.0"
)

lazy val author = project
  .enablePlugins(PackPlugin)
  .settings(
    name := "author",
    packMain := Map("author" -> "quizzly.author.run"),
    libraryDependencies ++= commonDependencies
  )

lazy val authorClient = (project in file("clients/author"))
  .settings(
    name := "author-client",
    packMain := Map("author-client" -> "author.Main"),
    fork := true,
    testOptions += Tests.Argument(jupiterTestFramework, "--display-mode=tree"),
    javacOptions ++= Seq("-Xlint"),
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.3.0-alpha16",
      "org.slf4j" % "slf4j-api" % "2.0.0-alpha7",
      "org.openjfx" % "javafx-base" % "19",
      "org.openjfx" % "javafx-controls" % "19",
      "com.fasterxml.jackson.core" % "jackson-databind" % "2.14.1",
      "org.pdfsam.rxjava3" % "rxjavafx" % "3.+",
      "org.projectlombok" % "lombok" % "1.18.24" % Provided,
      "org.commonmark" % "commonmark" % "0.21.0",

      "net.aichler" % "jupiter-interface" % JupiterKeys.jupiterVersion.value % Test,
      "org.testfx" % "testfx-junit5" % "4.0.16-alpha" % Test,
      "org.mockito" % "mockito-core" % "3.+" % Test,
      "org.hamcrest" % "hamcrest" % "2.1" % Test,
      "org.assertj" % "assertj-core" % "3.23.1" % Test,
      "org.mock-server" % "mockserver-junit-jupiter-no-dependencies" % "5.14.0" % Test,
      "org.jsoup" % "jsoup" % "1.15.3" % Test,
      "org.junit.jupiter" % "junit-jupiter-params" % "5.9.0" % Test
    )
  )

lazy val school = project
  .enablePlugins(PackPlugin, AkkaGrpcPlugin)
  .settings(
    name := "school",
    packMain := Map("school" -> "quizzly.school.run"),
    excludeDependencies ++= Seq(
      "com.thesamet.scalapb" % "scalapb-runtime_3"
    ),
    libraryDependencies ++= commonDependencies
  )

lazy val trial = project
  .enablePlugins(PackPlugin, AkkaGrpcPlugin)
  .settings(
    name := "trial",
    packMain := Map("trial" -> "quizzly.trial.run"),
    excludeDependencies ++= Seq(
      "com.thesamet.scalapb" % "scalapb-runtime_3"
    ),
    libraryDependencies ++= commonDependencies
  )

