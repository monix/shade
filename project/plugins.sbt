resolvers += Classpaths.sbtPluginReleases

addSbtPlugin("com.jsuereth"       % "sbt-pgp"          % "1.0.0")
addSbtPlugin("org.scoverage"      % "sbt-scoverage"    % "1.5.0")
addSbtPlugin("pl.project13.scala" % "sbt-jmh"          % "0.2.18")
addSbtPlugin("org.scala-js"       % "sbt-scalajs"      % "0.6.14")
addSbtPlugin("com.typesafe"       % "sbt-mima-plugin"  % "0.1.13")
addSbtPlugin("com.github.gseitz"  % "sbt-release"      % "1.0.4")
