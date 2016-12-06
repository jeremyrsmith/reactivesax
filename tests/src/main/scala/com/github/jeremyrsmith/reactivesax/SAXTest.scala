package com.github.jeremyrsmith.reactivesax

import org.scalamock.{FunctionAdapter3, MatchAny, MockParameter}
import org.scalamock.scalatest.MockFactory
import org.xml.sax.{Attributes, ContentHandler}

import scala.xml._

trait SAXTest { self: MockFactory =>

  implicit class MockAttributes(attrs: Map[String, String]) extends Attributes {
    override def getType(index: Int): String = ???

    override def getType(uri: String, localName: String): String = ???

    override def getType(qName: String): String = ???

    override def getLength: Int = ???

    override def getValue(index: Int): String = ???

    override def getValue(uri: String, localName: String): String = ???

    override def getValue(qName: String): String = ???

    override def getIndex(uri: String, localName: String): Int = ???

    override def getIndex(qName: String): Int = ???

    override def getURI(index: Int): String = ???

    override def getLocalName(index: Int): String = ???

    override def getQName(index: Int): String = ???

    override def equals(that: Any) = that match {
      case other: Attributes =>
        val inputAttributes = (0 to other.getLength - 1).map(i => other.getQName(i) -> other.getValue(i)).toMap
        val (namespaceAttributes, regularAttributes) = inputAttributes.partition { case (key, value) => key == "xmlns" || key.startsWith( "xmlns:") }
        regularAttributes == attrs
      case _ => false
    }
  }

  case class NullableMatcher[A](value: A)(implicit manifest: Manifest[A]) extends MatchAny {
    override def canEqual(that: Any) = that match {
      case _: A => true
      case _ => false
    }

    override def equals(that: Any) = that match {
      case null => value == null
      case something: A => Option(something) == Option(value)
      case _ => false
    }

    override def toString() = value match {
      case null => "null"
      case a => a.toString
    }
  }

  implicit class ContentHandlerMock(val handler: ContentHandler) {

    def recurse(el: Node, f1: PartialFunction[Node, Unit], f2: (Elem) => Unit): Unit = {
      if(f1.isDefinedAt(el))
        f1(el)
      else {
        println(s"SAX Test issue: does not define a case for $el")
      }
      el.child.foreach {
        case e: Elem =>
          recurse(e, f1, f2)
        case node =>
          recurse(node, f1, f2)
      }
      el match {
        case e: Elem => f2(e)
        case _ =>
      }
    }

    val nodeHandler: PartialFunction[Node, Unit] = {
      case el: Elem =>
        val uri = el.getNamespace(el.prefix)
        val qName = Option(el.prefix).map(_ + ":" + el.label).getOrElse(el.label)
        (handler.startElement _).expects(
          NullableMatcher(uri),
          el.label,
          qName,
          new MockAttributes(el.attributes.asAttrMap)) //(el.scope.uri, el.label, el.attributes.asAttrMap.toSet: Set[(String, String)])

      case Text(str) if str.trim() != "" =>
        handler.characters _ expects aTextNode(str)
      case Text(str) =>
        (handler.characters _).expects(aTextNode("")).noMoreThanOnce()
      case PCData(str) =>
        (handler.characters _).expects(aTextNode(str))
      case Comment(text) =>
      case ProcInstr(target, text) =>
        (handler.processingInstruction _).expects(target, text)
    }

    val endElementHandler: PartialFunction[Node, Unit] = {
      case el: Elem =>
        val uri = el.getNamespace(el.prefix)
        val qName = Option(el.prefix).map(_ + ":" + el.label).getOrElse(el.label)
        (handler.endElement _).expects(NullableMatcher(uri), el.label, qName)
    }

    def expectsXML(doc: Node) = inSequence {
        (handler.startDocument _).expects()
        recurse(doc, nodeHandler, endElementHandler)
        (handler.endDocument _).expects()
    }

    def expectsXML(doc: NodeSeq) = {

      inSequence {
        (handler.startDocument _).expects()
        doc foreach { node =>
          recurse(node, nodeHandler , endElementHandler)
        }
        (handler.endDocument _).expects()
      }
    }
  }

  val NoAttributes = Set.empty[(String, String)]

  case class aTextNode(content: String) extends FunctionAdapter3[Array[Char], Int, Int, Boolean]({
    case (arr, start, len) => new String(arr, start, len).trim() == content.trim()
  }) {
    override def toString() = s"""(string "$content" with length ${content.length})"""
  }
}