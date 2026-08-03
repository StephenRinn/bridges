ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "io.github.stephenrinn"
ThisBuild / organizationName := "Stephen Rinn"
ThisBuild / organizationHomepage := Some(url("https://github.com/StephenRinn"))

ThisBuild / homepage := Some(url("https://github.com/StephenRinn/bridges"))

ThisBuild / scalaVersion := "2.13.18"

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/StephenRinn/bridges"),
    "scm:git:https://github.com/StephenRinn/bridges.git"
  )
)

val http4sVersion = "0.23.34"
val catsVersion = "3.7.0"

ThisBuild / developers := List(
  Developer(
    id = "StephenRinn",
    name = "Stephen Rinn",
    email = "",
    url = url("https://github.com/StephenRinn")
  )
)

lazy val commonSettings = Seq(
  libraryDependencies ++= Seq(
    "org.typelevel" %% "cats-effect" % catsVersion,
    "org.typelevel" %% "munit-cats-effect-3" % "1.0.7" % Test,
  ),
)

lazy val core = project
  .in(file("bridges-core"))
  .settings(commonSettings)
  .settings(
    name := "bridges-core",
    libraryDependencies ++= Seq(
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.6",
      "ch.qos.logback" % "logback-classic" % "1.5.32",
      "io.circe" %% "circe-core" % "0.14.15",
    ),
  )

lazy val http4s = project
  .in(file("bridges-http4s"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    name := "bridges-http4s",
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-core" % http4sVersion,
      "org.http4s" %% "http4s-server" % http4sVersion,
      "org.http4s" %% "http4s-dsl" % http4sVersion,
    ),
  )

lazy val root = project
  .in(file("."))
  .aggregate(core, http4s)
