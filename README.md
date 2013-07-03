# Shade - a Reactive Memcached Client for Scala

## Overview 

Shade is a Memcached client based on the de-facto Java library
[SpyMemcached](https://code.google.com/p/spymemcached/).

The interface exposed is very Scala-like, as you have a choice between
making asynchronous calls, with results wrapped as Scala
[Futures](http://docs.scala-lang.org/sips/pending/futures-promises.html),
or blocking calls.

The performance is stellar as it benefits from the
[optimizations that went into SpyMemcached](https://code.google.com/p/spymemcached/wiki/Optimizations)
over the years. Shade also fixes some problems with SpyMemcached's
architecture, choices that made sense in the context of Java, but
don't make so much sense in the context of Scala (TODO: add details).

The client is production quality, being in usage at Epigrams, Inc. for
serving thousands of requests per second per instance of real
traffic. It doesn't leak, it doesn't break, it works well under pressure.

[![Build Status](https://travis-ci.org/alexandru/shade.png?branch=master)](https://travis-ci.org/alexandru/shade)

## Usage From SBT

Add these resolvers:

```
resolvers ++= Seq(
  "BionicSpirit Releases" at "http://maven.bionicspirit.com/releases/",
  "BionicSpirit Snapshots at "http://maven.bionicspirit.com/snapshots/"
)
```

You may need other resolvers, depending on what subprojects you want,
but right now you can get away with these:

```scala
resolvers ++= Seq(
  // just in case you don't have it already
  "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
  // for SpyMemcached (shifter-cache dependency)
  "Spy" at "http://files.couchbase.com/maven2/"
)
```

Specify the dependency for individual subprojects:

```scala
dependencies += "com.bionicspirit" %% "shifter" % "1.1"
```

## Documentation

TODO!