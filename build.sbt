name := """damgard-jurik-secure-computations"""

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.8"

val akkaVersion = "2.4.11"

libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % "1.1.7",
  "com.github.nscala-time" %% "nscala-time" % "2.14.0",
  "com.typesafe" % "config" % "1.3.0",
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-contrib" % akkaVersion,
  "com.typesafe.akka" %% "akka-remote" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
  "org.scalacheck" %% "scalacheck" % "1.13.2" % "test",
  "org.scalatest" %% "scalatest" % "3.0.0" % "test"
).map(_ withSources() withJavadoc())

// fork := true

// javaOptions := Seq("-Djava.library.path=jni/")

// resolvers ++= Seq(
//   "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
//   "Sonatype OSS Releases" at "https://oss.sonatype.org/content/repositories/releases"
// )

// scalacOptions ++= Seq(
//   "-deprecation",
//   "-encoding", "UTF-8",
//   "-feature",
//   "-language:existentials",
//   "-language:higherKinds",
//   "-language:implicitConversions",
//   "-language:experimental.macros",
//   "-unchecked",
//   "-Xfatal-warnings",
//   "-Xlint",
//   "-Yinline-warnings",
//   "-Ywarn-dead-code",
//   "-Xfuture")
