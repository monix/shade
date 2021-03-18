name := "shade"
organization := "io.monix"

addCommandAlias("ci",  ";clean ;compile ;test ;package")
addCommandAlias("release", ";+publishSigned ;sonatypeReleaseAll")

scalaVersion := "2.12.3"
crossScalaVersions := Seq("2.11.11", "2.12.3", "2.13.5")
compileOrder in ThisBuild := CompileOrder.JavaThenScala

scalacOptions ++= {
  val baseOptions = Seq(
    "-Xfatal-warnings", // turns all warnings into errors ;-)
    // warnings
    "-unchecked", // able additional warnings where generated code depends on assumptions
    "-deprecation", // emit warning for usages of deprecated APIs
    "-feature",     // emit warning usages of features that should be imported explicitly
    // possibly deprecated options
    "-Ywarn-dead-code"
  )
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, majorVersion)) if majorVersion >= 12 => baseOptions
    case _ => baseOptions :+ "-target:jvm-1.6" // generates code with the Java 6 class format
  }
}

// version specific compiler options
scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
  case Some((2, majorVersion)) if majorVersion >= 11 =>
    Seq(
      // enables linter options
      "-Xlint:adapted-args", // warn if an argument list is modified to match the receiver
      "-Xlint:nullary-unit", // warn when nullary methods return Unit
      "-Xlint:inaccessible", // warn about inaccessible types in method signatures
      "-Xlint:infer-any", // warn when a type argument is inferred to be `Any`
      "-Xlint:missing-interpolator", // a string literal appears to be missing an interpolator id
      "-Xlint:doc-detached", // a ScalaDoc comment appears to be detached from its element
      "-Xlint:private-shadow", // a private field (or class parameter) shadows a superclass field
      "-Xlint:type-parameter-shadow", // a local type parameter shadows a type already in scope
      "-Xlint:poly-implicit-overload", // parameterized overloaded implicit methods are not visible as view bounds
      "-Xlint:option-implicit", // Option.apply used implicit view
      "-Xlint:delayedinit-select", // Selecting member of DelayedInit
      "-Xlint:package-object-classes", // Class or object defined in package object
    )
  case _ =>
    Seq.empty
})

scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
  case Some((2, majorVersion)) if majorVersion <= 12 =>
    Seq(
      "-Ywarn-inaccessible",
      "-Xlint:nullary-override", // warn when non-nullary `def f()' overrides nullary `def f'
      "-Xlint:by-name-right-associative", // By-name parameter of right associative operator
      "-Xlint:unsound-match" // Pattern match may not be typesafe
    )
  case _ => Seq.empty
})

// Turning off fatal warnings for ScalaDoc, otherwise we can't release.
scalacOptions in (Compile, doc) ~= (_ filterNot (_ == "-Xfatal-warnings"))

resolvers ++= Seq(
  ("Spy" at "http://files.couchbase.com/maven2/").withAllowInsecureProtocol(true)
)

libraryDependencies ++= Seq(
  "net.spy"           %  "spymemcached"       % "2.12.3",
  "org.slf4j"         %  "slf4j-api"          % "1.7.30",
  "io.monix"          %% "monix-eval"         % "3.3.0",
  "ch.qos.logback"    %  "logback-classic"    % "1.2.3"  % Test,
  "org.scalatest"     %% "scalatest"          % "3.2.6"  % Test,
  "org.scalatest"     %% "scalatest-funsuite" % "3.2.6"  % Test,
  "org.scalacheck"    %% "scalacheck"         % "1.15.2" % Test,
  "org.scalatestplus" %% "scalacheck-1-14"    % "3.2.2.0" % Test
)

libraryDependencies += ("org.scala-lang" % "scala-reflect" % scalaVersion.value % "compile")

//------------- For Release

useGpg := false
usePgpKeyHex("2673B174C4071B0E")
pgpPublicRing := baseDirectory.value / "project" / ".gnupg" / "pubring.gpg"
pgpSecretRing := baseDirectory.value / "project" / ".gnupg" / "secring.gpg"
pgpPassphrase := sys.env.get("PGP_PASS").map(_.toArray)

enablePlugins(GitVersioning)

/* The BaseVersion setting represents the in-development (upcoming) version,
 * as an alternative to SNAPSHOTS.
 */
git.baseVersion := "1.11.0"

val ReleaseTag = """^v([\d\.]+)$""".r
git.gitTagToVersionNumber := {
  case ReleaseTag(v) => Some(v)
  case _ => None
}

git.formattedShaVersion := {
  val suffix = git.makeUncommittedSignifierSuffix(git.gitUncommittedChanges.value, git.uncommittedSignifier.value)

  git.gitHeadCommit.value map { _.substring(0, 7) } map { sha =>
    git.baseVersion.value + "-" + sha + suffix
  }
}

sonatypeProfileName := organization.value

credentials += Credentials(
  "Sonatype Nexus Repository Manager",
  "oss.sonatype.org",
  sys.env.getOrElse("SONATYPE_USER", ""),
  sys.env.getOrElse("SONATYPE_PASS", "")
)

publishMavenStyle := true

isSnapshot := version.value endsWith "SNAPSHOT"

publishTo := Some(
  if (isSnapshot.value)
    Opts.resolver.sonatypeSnapshots
  else
    Opts.resolver.sonatypeStaging
)

publishArtifact in Test := false
pomIncludeRepository := { _ => false } // removes optional dependencies

scalariformSettings(true)

licenses := Seq("MIT" -> url("https://opensource.org/licenses/MIT"))
homepage := Some(url("https://github.com/monix/shade"))

scmInfo := Some(
  ScmInfo(
    url("https://github.com/monix/shade.git"),
    "scm:git@github.com:monix/shade.git"
  ))

developers := List(
  Developer(
    id="alexelcu",
    name="Alexandru Nedelcu",
    email="noreply@alexn.org",
    url=url("https://alexn.org")
  ))

// Multi-project-related

lazy val root = project in file(".")

lazy val benchmarking = (project in file("benchmarking"))
  .enablePlugins(JmhPlugin)
  .settings(libraryDependencies += "org.slf4j" % "slf4j-simple" % "1.7.21")
  .dependsOn(root)
