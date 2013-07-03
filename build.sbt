name := "shade"

organization in ThisBuild := "com.bionicspirit"

version in ThisBuild := "1.1"

scalaVersion in ThisBuild := "2.10.2"

compileOrder in ThisBuild := CompileOrder.JavaThenScala

scalacOptions in ThisBuild ++= Seq(
  "-unchecked", "-deprecation", "-feature",
  "-target:jvm-1.6"
)

licenses in ThisBuild := Seq("MIT" -> url("http://opensource.org/licenses/MIT"))

homepage in ThisBuild := Some(url("http://github.com/alexandru/shade"))

resolvers ++= Seq(
  "BionicSpirit Releases" at "http://maven.bionicspirit.com/releases/",
  "BionicSpirit Snapshots" at "http://maven.bionicspirit.com/snapshots/",
  "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases",
  "Spray Releases" at "http://repo.spray.io",
  "Spy" at "http://files.couchbase.com/maven2/"
)

libraryDependencies ++= Seq(
  "spy" % "spymemcached" % "2.8.4",
  "com.typesafe.akka" %% "akka-actor" % "2.1.4",
  "org.slf4j" % "slf4j-api" % "1.7.4",
  "ch.qos.logback" % "logback-classic" % "1.0.6" % "test",
  "org.scalatest" %% "scalatest" % "1.9.1" % "test",
  "junit" % "junit" % "4.10" % "test"
)

pomExtra in ThisBuild := (
  <scm>
    <url>git@github.com:alexandru/shade.git</url>
    <connection>scm:git:git@github.com:alexandru/shade.git</connection>
  </scm>
  <developers>
    <developer>
      <id>alex_ndc</id>
      <name>Alexandru Nedelcu</name>
      <url>http://bionicspirit.com</url>
    </developer>
  </developers>)
