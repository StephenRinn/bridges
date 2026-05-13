ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.18"

val http4sVersion = "0.23.34"
val catsVersion = "3.7.0"

lazy val root = (project in file("."))
  .settings(
    name := "bridges",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-effect" % "3.7.0",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.6",
      "ch.qos.logback" % "logback-classic" % "1.5.32",
      "io.circe" %% "circe-core" % "0.14.15"
    )
  )
