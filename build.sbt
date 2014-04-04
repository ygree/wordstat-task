import play.Project._

name := """wordstat-task"""

version := "1.0-SNAPSHOT"

libraryDependencies ++= Seq(
  "org.webjars" %% "webjars-play" % "2.2.0", 
  "org.webjars" % "bootstrap" % "2.3.1",
  "com.typesafe.akka" %% "akka-testkit" % "2.2.0"
)

playScalaSettings
