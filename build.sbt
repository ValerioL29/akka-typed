name := "akka-essentials"

version := "0.1.0-SNAPSHOT"

scalaVersion := "2.13.8"

val akkaVersion = "2.6.19"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  "org.scalatest" %% "scalatest" % "3.2.11"
)
