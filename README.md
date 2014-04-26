# Shade - Memcached Client for Scala

## Overview 

Shade is a Memcached client based on the de-facto Java library
[SpyMemcached](https://code.google.com/p/spymemcached/).

The interface exposed is very Scala-ish, as you have a choice between
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

Supported for Scala versions: 2.10.x, to be updated for 2.11 as soon
as it's final and a port for 2.9.x is in progress.

[![Build Status](https://travis-ci.org/alexandru/shade.png?branch=v1.6.0)](https://travis-ci.org/alexandru/shade)

## Release Notes

- [Version 1.6.0 - April 26, 2014](release-notes/1.6.0.md)

## Usage From SBT

Add this resolver for SpyMemcached:

```scala
resolvers += "Spy" at "http://files.couchbase.com/maven2/"
```

Specify the dependency:

```scala
dependencies += "com.bionicspirit" %% "shade" % "1.6.0"
```

### Using the snapshot (in-development) version

Add this resolver for Sonatype snapshots:

```scala
resolvers += "Sonatype snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"
```

Add the dependency:

```scala
dependencies += "com.bionicspirit" %% "shade" % "1.7.0-SNAPSHOT"
```

## Usage - Memcached

### Initializing the Memcached Client

To initialize a Memcached client, you need a configuration object.
Checkout the
[Configuration](src/main/scala/shade/memcached/Configuration.scala)
case class.

```scala
import shade.memcached._
import scala.concurrent.ExecutionContext.Implicits.{global => ec}

val memcached = 
  Memcached(Configuration("127.0.0.1:11211"), ec)
```

As you can see, you also need an
[ExecutionContext](http://www.scala-lang.org/api/current/#scala.concurrent.ExecutionContext)
passed explicitly. As an implementation detail, the execution context represents the
thread-pool in which requests get processed. 

### Simple non-blocking requests

Useful imports:

```scala
import concurrent.duration._ // for specifying timeouts
import concurrent.Future
```

Setting a key:

```scala
val op: Future[Unit] = memcached.set("username", "Alex", 1.minute)
```

Adding a key that will only set it if the key is missing (returns true
if the key was added, or false if the key was already there):

```scala
val op: Future[Boolean] = memcached.add("username", "Alex", 1.minute)
```

Deleting a key (returns true if a key was deleted, or false if the key
was missing):

```scala
val op: Future[Boolean] = memcached.delete("username")
```

Fetching a key:

```scala
val result: Future[Option[String]] = memcached.get[String]("username")
```

As you can see, for fetching a key the `get()` method needs an
explicit type parameter, otherwise it doesn't know how to deserialize
it. More on this below.

### Blocking requests

Sometimes working with Futures is painful for quick hacks, therefore
`add()`, `set()`, `delete()` and `get()` have blocking versions in the
form of `awaitXXX()`:

```scala
memcached.awaitGet("username") match {
  case Some(username) => println(s"Hello, $username")
  case None =>
    memcached.awaitSet("username", "Alex", 1.minute)
}
```

### Compare-and-set

Sometimes you want to have some sort of synchronization for modifying
values safely, like incrementing a counter. Memcached supports
[Compare-And-Swap](http://en.wikipedia.org/wiki/Compare-and-swap)
atomic operations and so does this client.

```scala
val op: Future[Boolean] = 
  memcached.compareAndSet("username", Some("Alex"), "Amalia", 1.minute)
```

This will return either true or false if the operation was a success
or not. But working with `compareAndSet` is too low level, so the
client also provides these helpers:

```scala
def incrementCounter: Future[Int] =
  memcached.transformAndGet[Int]("counter", 1.minute) { 
    case Some(existing) => existing + 1
    case None => 1
  }
```

The above returns the new, incremented value. In case you want the old
value to be returned, do this:

```scala
def incrementCounter: Future[Option[Int]] =
  memcached.getAndTransform[Int]("counter", 1.minute) { 
    case Some(existing) => existing + 1
    case None => 1
  }
```

### Serializing/Deserializing

Storing values in Memcached and retrieving values involves serializing
and deserializing those values into bytes. Methods such as `get()`,
`set()`, `add()` take an implicit parameter of type `Codec[T]` which
is a type-class that specifies how to serialize and deserialize values
of type `T`.

By default, Shade provides default implementations of `Codec[T]` for
primitives, such as Strings and numbers. Checkout
[Codec.scala](src/main/scala/shade/memcached/Codec.scala) to see those
defaults.

For more complex types, a default implementation based on Java's
[ObjectOutputStream](http://docs.oracle.com/javase/7/docs/api/java/io/ObjectOutputStream.html)
and
[ObjectInputStream](http://docs.oracle.com/javase/7/docs/api/java/io/ObjectInputStream.html)
exist (also in Codec.scala).

However, because serializing/deserializing values like this is
problematic (you can end up with lots of errors related to the
ClassLoader used), this codec is available as part of the
`MemcachedCodecs` trait (also in
[Codec.scala](src/main/scala/shade/memcached/Codec.scala)) and it
either needs to be imported or mixed-in.

The import works like so:

```scala
import shade.memcached.MemcachedCodecs._
```

But this can land you in trouble because of the ClassLoader. For
example in a Play 2.x application, in development mode the code is
recompiled when changes happen and the whole environment gets
restarted. If you do a plain import, you'll get `ClassCastException`
or other weird errors. You can solve this by mixing-in
`MemcachedCodecs` in whatever trait, class or object you want to do
requests, as in:

```scala
case class User(id: Int, name: String, age: Int)

trait HelloController extends Controller with MemcachedCodecs {
   def memcached: Memcached // to be injected
   
   // a Play 2.2 standard controller action
   def userInfo(id: Int) = Action.async {    
     for (user <-  memcached.get[User]("user-" + id)) yield
       Ok(views.showUserDetails(user))
   }
   
   // ...
}
```

Or, in case you want to optimize serialization/deserialization, you
can always implement your own `Codec[T]`, like:

```scala
// hackish example
implicit object UserCodec extends Codec[User] {
  def serialize(user: User): Array[Byte] =
    s"${user.id}|${user.name}|${user.age}".getBytes("utf-8")
    
  def deserialize(data: Array[Byte]): User = {
    val str = new String(data, "utf-8")
    val Array(id, name, age) = str.split("|")
    User(id.toInt, name, age.toInt)
  }
}
```

N.B.
[Scala's Pickles](https://github.com/scala/pickling)
are a much better alternative to Java's object input/output streams,
when they'll land in Scala 2.11.

