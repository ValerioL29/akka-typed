name := "akka-typed"

version := "0.1.0-SNAPSHOT"

scalaVersion := "2.13.8"

val akkaVersion = "2.6.19"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed"         % akkaVersion,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion,
  "org.scalatest"     %% "scalatest"                % "3.2.12",
  "ch.qos.logback"    % "logback-classic"           % "1.2.11"
)
