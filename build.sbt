name := "shade"

organization := "com.bionicspirit"

version := "1.7.0-SNAPSHOT"

scalaVersion := "2.10.4"

crossScalaVersions := Seq("2.10.4", "2.11.0")

compileOrder in ThisBuild := CompileOrder.JavaThenScala

scalacOptions in ThisBuild ++= Seq(
  "-unchecked", "-deprecation", "-feature", "-Xlint", "-target:jvm-1.6", "-Yinline-warnings",
  "-optimise", "-Ywarn-adapted-args", "-Ywarn-dead-code", "-Ywarn-inaccessible",
  "-Ywarn-nullary-override", "-Ywarn-nullary-unit"
)

resolvers ++= Seq(
  "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases",
  "Spy" at "http://files.couchbase.com/maven2/"
)

libraryDependencies ++= Seq(
  "net.spy" % "spymemcached" % "2.11.1",
  "org.monifu" %% "monifu-core" % "0.7.0",
  "org.slf4j" % "slf4j-api" % "1.7.7",
  "ch.qos.logback" % "logback-classic" % "1.0.6" % "test",
  "org.scalatest" %% "scalatest" % "2.1.3" % "test",
  "junit" % "junit" % "4.10" % "test"
)

publishMavenStyle := true

publishArtifact in Test := false

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

publishArtifact in Test := false

pomIncludeRepository := { _ => false } // removes optional dependencies

pomExtra in ThisBuild :=
  <url>https://github.com/alexandru/shade</url>
  <licenses>
    <license>
      <name>The MIT License</name>
      <url>http://opensource.org/licenses/MIT</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <scm>
    <url>git@github.com:bionicspirit/shade.git</url>
    <connection>scm:git:git@github.com:bionicspirit/shade.git</connection>
  </scm>
  <developers>
    <developer>
      <id>alex_ndc</id>
      <name>Alexandru Nedelcu</name>
      <url>https://www.bionicspirit.com/</url>
    </developer>
  </developers>
