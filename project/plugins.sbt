resolvers += Classpaths.sbtPluginReleases

addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.0.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-scalariform" % "1.3.0")

// SBT-Scoverage version must be compatible with SBT-coveralls version below
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.0")

// Upgrade when this issue is solved https://github.com/scoverage/sbt-coveralls/issues/73
addSbtPlugin("org.scoverage" % "sbt-coveralls" % "1.1.0")