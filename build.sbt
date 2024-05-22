ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.14"

Compile / unmanagedSourceDirectories += baseDirectory.value / "src" / "main" / "java"

lazy val root = (project in file("."))
  .settings(
    name := "task"
  )

enablePlugins(ScoverageSbtPlugin)

coverageHighlighting := true

libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-blaze-server" % "0.23.12",
  "org.http4s" %% "http4s-circe" % "0.23.12",
  "org.http4s" %% "http4s-dsl" % "0.23.12",
  "io.circe" %% "circe-generic" % "0.14.1",
  "io.circe" %% "circe-literal" % "0.14.1",
  "org.typelevel" %% "cats-effect" % "3.3.12",
  "org.scalatest" %% "scalatest" % "3.2.9" % Test
)
