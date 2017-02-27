import com.typesafe.sbt.pgp.PgpKeys

import scala.xml.Elem
import scala.xml.transform.{RewriteRule, RuleTransformer}

val monixVersion = "2.2.2"

lazy val sharedSettings = Seq(
  version := "1.9.2",
  organization := "io.monix",

  scalaVersion := "2.11.8",
  crossScalaVersions := Seq("2.10.6", "2.11.8", "2.12.1"),
  compileOrder in ThisBuild := CompileOrder.JavaThenScala,

  scalacOptions ++= Seq(
    // warnings
    "-unchecked", // able additional warnings where generated code depends on assumptions
    "-deprecation", // emit warning for usages of deprecated APIs
    "-feature", // emit warning usages of features that should be imported explicitly
    // Features enabled by default
    "-language:higherKinds",
    "-language:implicitConversions",
    "-language:experimental.macros",
    // possibly deprecated options
    "-Ywarn-dead-code",
    "-Ywarn-inaccessible"
  ),

  // Targeting Java 6, but only for Scala <= 2.11
  javacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, majorVersion)) if majorVersion <= 11 =>
      // generates code with the Java 6 class format
      Seq("-source", "1.6", "-target", "1.6")
    case _ =>
      // For 2.12 we are targeting the Java 8 class format
      Seq("-source", "1.8", "-target", "1.8")
  }),
  scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, majorVersion)) if majorVersion <= 11 =>
      // generates code with the Java 6 class format
      Seq("-target:jvm-1.6")
    case _ =>
      // For 2.12 we are targeting the Java 8 class format
      Seq.empty
  }),

  // Linter
  scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, majorVersion)) if majorVersion >= 11 =>
      Seq(
        // Turns all warnings into errors ;-)
        "-Xfatal-warnings",
        // Enables linter options
        "-Xlint:adapted-args", // warn if an argument list is modified to match the receiver
        "-Xlint:nullary-unit", // warn when nullary methods return Unit
        "-Xlint:inaccessible", // warn about inaccessible types in method signatures
        "-Xlint:nullary-override", // warn when non-nullary `def f()' overrides nullary `def f'
        "-Xlint:infer-any", // warn when a type argument is inferred to be `Any`
        "-Xlint:missing-interpolator", // a string literal appears to be missing an interpolator id
        "-Xlint:doc-detached", // a ScalaDoc comment appears to be detached from its element
        "-Xlint:private-shadow", // a private field (or class parameter) shadows a superclass field
        "-Xlint:type-parameter-shadow", // a local type parameter shadows a type already in scope
        "-Xlint:poly-implicit-overload", // parameterized overloaded implicit methods are not visible as view bounds
        "-Xlint:option-implicit", // Option.apply used implicit view
        "-Xlint:delayedinit-select", // Selecting member of DelayedInit
        "-Xlint:by-name-right-associative", // By-name parameter of right associative operator
        "-Xlint:package-object-classes", // Class or object defined in package object
        "-Xlint:unsound-match" // Pattern match may not be typesafe
      )
    case _ =>
      Seq.empty
  }),

  // For warning against unused imports
  scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 10)) =>
        Seq()
      case Some((2, n)) if n >= 11 =>
        Seq("-Ywarn-unused-import")
    }
  },
  scalacOptions in (Compile, console) ~= {_.filterNot("-Ywarn-unused-import" == _)},
  scalacOptions in (Test, console) ~= {_.filterNot("-Ywarn-unused-import" == _)},

  // Common dependencies

  resolvers ++= Seq(
    "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases",
    //"Spy" at "http://files.couchbase.com/maven2/",
    Resolver.sonatypeRepo("snapshots")
  ),

  testFrameworks := Seq(new TestFramework("minitest.runner.Framework")),
  libraryDependencies ++= Seq(
    "io.monix" %%% "minitest-laws" % "0.27" % Test
  ),

  // Trying to disable parallel testing
  parallelExecution in Test := false,
  parallelExecution in IntegrationTest := false,
  testForkedParallel in Test := false,
  testForkedParallel in IntegrationTest := false,
  concurrentRestrictions in Global += Tags.limit(Tags.Test, 1),

  // -- Settings meant for deployment on oss.sonatype.org

  useGpg := true,
  useGpgAgent := true,
  usePgpKeyHex("2673B174C4071B0E"),

  publishMavenStyle := true,
  releaseCrossBuild := true,
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,

  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases"  at nexus + "service/local/staging/deploy/maven2")
  },

  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false }, // removes optional dependencies

  // For evicting Scoverage out of the generated POM
  // See: https://github.com/scoverage/sbt-scoverage/issues/153
  pomPostProcess := { (node: xml.Node) =>
    new RuleTransformer(new RewriteRule {
      override def transform(node: xml.Node): Seq[xml.Node] = node match {
        case e: Elem
          if e.label == "dependency" && e.child.exists(child => child.label == "groupId" && child.text == "org.scoverage") => Nil
        case _ => Seq(node)
      }
    }).transform(node).head
  },

  pomExtra in ThisBuild :=
    <url>https://github.com/monix/shade</url>
    <licenses>
      <license>
        <name>The MIT License</name>
        <url>http://opensource.org/licenses/MIT</url>
        <distribution>repo</distribution>
      </license>
    </licenses>
    <scm>
      <url>git@github.com:monix/shade.git</url>
      <connection>scm:git:git@github.com:monix/shade.git</connection>
    </scm>
    <developers>
      <developer>
        <id>alex_ndc</id>
        <name>Alexandru Nedelcu</name>
        <url>https://alexn.org</url>
      </developer>
    </developers>
)

lazy val cmdlineProfile =
  sys.props.getOrElse("sbt.profile", default = "")

def profile: Project ⇒ Project = pr => cmdlineProfile match {
  case "coverage" => pr
  case _ => pr.disablePlugins(scoverage.ScoverageSbtPlugin)
}

lazy val doNotPublishArtifact = Seq(
  publishArtifact := false,
  publishArtifact in (Compile, packageDoc) := false,
  publishArtifact in (Compile, packageSrc) := false,
  publishArtifact in (Compile, packageBin) := false
)

lazy val crossSettings = sharedSettings ++ Seq(
  unmanagedSourceDirectories in Compile += {
    baseDirectory.value.getParentFile / "shared" / "src" / "main" / "scala"
  },
  unmanagedSourceDirectories in Test += {
    baseDirectory.value.getParentFile / "shared" / "src" / "test" / "scala"
  }
)

lazy val scalaJSSettings = Seq(
  coverageExcludedFiles := ".*"
)

// Multi-project-related

lazy val shade = project.in(file("."))
  .configure(profile)
  .aggregate(localJVM, localJS, memcached)
  .settings(sharedSettings)
  .settings(doNotPublishArtifact)
  .settings(name := "shade")

lazy val localCommon = crossSettings ++ sharedSettings ++ Seq(
  name := "shade-local",
  libraryDependencies ++= Seq(
    "io.monix" %%% "monix-eval" % monixVersion
  )
)

lazy val localJVM = project.in(file("shade-local/jvm"))
  .configure(profile)
  .settings(localCommon)

lazy val localJS = project.in(file("shade-local/js"))
  .settings(localCommon)
  .settings(scalaJSSettings)
  .enablePlugins(ScalaJSPlugin)

lazy val memcached = project.in(file("shade-memcached"))
  .configure(profile)
  .dependsOn(localJVM)
  .settings(sharedSettings)
  .settings(Seq(
    name := "shade-memcached",
    libraryDependencies ++= Seq(
      "net.spy"        %  "spymemcached"    % "2.12.2",
      "org.slf4j"      %  "slf4j-api"       % "1.7.23",
      "ch.qos.logback" %  "logback-classic" % "1.1.7"   % Test
    )
  ))
