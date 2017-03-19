/* scala versions and options */
scalaVersion := "2.12.1"

val akkaVersion = "2.4.17"
val akkaHttpVersion = "10.0.4"

libraryDependencies ++= Seq (
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
  "com.github.melrief" %% "pureconfig" % "0.6.0",
  "joda-time" % "joda-time" % "2.9.7",
  "org.scalactic" %% "scalactic" % "3.0.1",
  "org.scalatest" %% "scalatest" % "3.0.1" % "test"
)

