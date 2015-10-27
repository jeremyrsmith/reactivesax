# ReactiveSax

## What

ReactiveSax is a tiny, non-validating, reactive SAX parser for Scala.  It does not implement the `org.xml.sax.Parser` 
interface (as do SAX1 parsers), nor the `org.xml.sax.XMLReader` interface (as do SAX2 parsers). Instead, it extends the
`java.io.Writer` class, and provides a `write` method.  When you write character data to the parser, it will be parsed
character-by-character by the parser's state machine, and the provided `org.xml.sax.ContentHandler` will receive SAX
events as appropriate.  After you write the data, nothing else happens until you write more data, at which point parsing
will pick up where it left off.

## Why

ReactiveSax arose from a fundamental flaw with SAX that makes it incompatible with the reactive programming model.  The
idea of SAX is great for reactive programming in that it is a push model - events are pushed to the next thing in the
pipeline, which does some processing and pushes stuff to the next thing, etc.  Unfortunately, the `XMLReader` interface
defines a `parse` method, which calls `parse` up the chain until the actual parser is reached; that method takes an
`InputSource` parameter, which must provide an `InputStream`.  And therein lies the flaw - an InputStream is a blocking
I/O, which is fundamentally a blocking _pull_.  This goes against the push model of SAX and makes it unsuitable for
reactive programming using `Future` or `Task`.  If a bunch of parsers block all the threads waiting for input, and their
ability to pull input relies on other bits of your program pushing data (into, let's say, a `java.nio.Pipe` or `java.io.PipedWriter`)
then you'll have a deadlock on your hands.

Frustrated with this small piece of bad decision making (which arose from a failure to predict non-blocking I/O as a thing),
I came up with ReactiveSax.

## Cons

This package was written to address my current needs only.  There's a lot it doesn't do, and so to say it complies with
any particular standard would be completely incorrect.

 * It does no validation of any kind
 * It will happily continue trying to parse a malformed document (except in cases of certain catastrophic parse errors)
 * It does nothing with DTDs or other declarations like `<!THING>`
 * It doesn't process any entities besides `&amp;`, `&lt;`, `&gt;`, `&quot;`, and valid numeric entities (except to 
   provide a `skippedEntity(name)` event to the `ContentHandler`)
 * It produces no errors for an `ErrorHandler`.  In the catastrophic cases mentioned above, it throws an exception.
 * It does no resolution of any external anything.  It will never make a network request for any reason, nor attempt to 
   resolve anything.
 
## Pros / Features

  * It is based on a deterministic finite state automaton
    * So it's very fast
    * And it needs very little memory
  * It supports namespaces
  * It only parses while it's being pushed input, so parsing can be paused at any time
    * In other words, parsing will never block or wait for anything
    
## Usage

The companion object `SAXPushParser` has two `apply` methods.  Don't call the constructor of `SAXPushParser` directly.

To create an instance, if you already have the `ContentHandler` you'll be using:

```scala
val handler = new MyContentHandler
val sax = ReactiveSax(handler)
sax.write("<xml></xml>")
```

This makes the `ContentHandler` immutable.

To create an instance, if you're planning to set the `ContentHandler` later (this is discouraged):

```scala
val sax = ReactiveSax()
//...
sax.setContentHandler(handler)
sax.write("<xml></xml>")
```

Internally, this creates an identity `XMLFilter` and that it passes SAX events to, and `setContentHandler` calls the same
on that `XMLFilter`.  So it gives you some flexibility to set up your `ContentHandler` later on, but you *must* remember to do so,
and you have no guarantee that you won't be switching handlers around willy-nilly causing problems.
    
## FAQ

These questions aren't frequently asked.  In fact, nobody has ever asked me a question about this project.  But these are
some questions that you might be having if you're reading this README.

1. Why don't you implement the `XMLReader` interface?  How am I supposed to use this?
   
   *A*: As mentioned above, you should call `open` and then `write` in order to parse some data.  Implementing `XMLReader`
   would require implementing the `parse(InputSource)` method, which is fundamentally blocking/pull.
   
2. Is `SAXPushParser` thread safe?

  *A*: No - whenever data is written, it will try to process it.  You shouldn't write data from multiple threads simultaneously.
  If you find this happening, you should be sequencing your `Future`s more carefully.  Typically, a thread that's getting character
  data from somewhere (a computation, or an event-driven input source, etc) will be writing that data out to the parser
  in order to pipe character input into a SAX pipeline.
  
3. License?

  *A*: Currently, I'm licensing this under [Creative Commons BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/).
       
       ![CC BY-SA 4.0 Badge](https://licensebuttons.net/l/by-sa/3.0/88x31.png)
       
       What this means (or, what I intend it to mean) is that you can use this library for whatever you want, and I will
       be glad that you found it useful.  But, selfishly, if you improve the library, I want you to contribute back your
       changes in the form of a pull request.  Also, if you use this library, and publish your modified version, I want
       you to link back to the original and make it clear that you've modified it and what your changes are.  It's not
       because I want any credit; it's just because I don't want anyone to get confused.
       
       CC has no license for this specifically, but I don't care about attribution if you just use this library in your
       project (commercial or not).  You don't have to provide any attribution in that case, as far as I'm concerned.
       But if you modify the library, I want your improvements.  Deal?
       
       I've also added a clause about how I'm not providing any warranty, and if anything bad happens it's not my
       responsibility.  You can't be too careful these days.
       
4. Maven?

  *A*: I don't have this published to maven right now.  If that's a thing that just anyone can do, I'm happy to do it.
       I guess people publish things to Sonatype.  But I think you have to be special in someway to do that.