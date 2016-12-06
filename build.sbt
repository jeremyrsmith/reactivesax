name := "ReactiveSax"
description := "Reactive-friendly SAX-like XML parser"

organization in ThisBuild := "io.github.jeremyrsmith"
version in ThisBuild := "0.1.0"
scalaVersion in ThisBuild := "2.11.7"
homepage in ThisBuild := Some(url("https://github.com/jeremyrsmith/reactivesax"))
licenses in ThisBuild += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html"))

publishTo in ThisBuild := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

scmInfo in ThisBuild := Some(
  ScmInfo(
    url("https://github.com/jeremyrsmith/reactivesax"),
    "scm:git:git@github.com:jeremyrsmith/reactivesax.git"
  )
)

developers in ThisBuild := List(
  Developer("jeremyrsmith", "Jeremy Smith", "jeremyrsmith@gmail.com", url("https://github.com/jeremyrsmith"))
)

val reactivesax = project in file(".")
val tests = project.settings(
  name := "reactivesax-tests",
  description := "Tests and fixtures for reactivesax",
  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "2.2.4",
    "org.scalamock" %% "scalamock-scalatest-support" % "3.2"
  )
) dependsOn reactivesax