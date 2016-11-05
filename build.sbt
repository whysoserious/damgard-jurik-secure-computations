name := """damgard-jurik-secure-computations"""

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.8"

val akkaVersion = "2.4.12"

libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % "1.1.7",
  "com.github.nscala-time" %% "nscala-time" % "2.14.0",
  "com.typesafe" % "config" % "1.3.0",
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-contrib" % akkaVersion,
  "com.typesafe.akka" %% "akka-remote" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
  "org.scalacheck" %% "scalacheck" % "1.13.4" % "test",
  "org.scalatest" %% "scalatest" % "3.0.0" % "test"
).map(_ withSources() withJavadoc())
