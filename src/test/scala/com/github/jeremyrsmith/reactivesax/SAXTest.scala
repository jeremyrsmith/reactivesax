/**
 * Creative Commons Share-Alike 4.0
 *
 * This is a human-readable summary of (and not a substitute for) the license
 * (https://creativecommons.org/licenses/by-sa/4.0/)
 *
 * DISCLAIMER: This deed highlights only some of the key features and terms of the actual license. It is not a license
 * and has no legal value. You should carefully review all of the terms and conditions of the actual license before
 * using the licensed material.
 *
 * You are free to:
 *
 * Share — copy and redistribute the material in any medium or format
 * Adapt — remix, transform, and build upon the material for any purpose, even commercially.
 * The licensor cannot revoke these freedoms as long as you follow the license terms.
 *
 * Under the following terms:
 *
 * Attribution — You must give appropriate credit, provide a link to the license, and indicate if changes were made. You
 *   may do so in any reasonable manner, but not in any way that suggests the licensor endorses you or your use.
 * ShareAlike — If you remix, transform, or build upon the material, you must distribute your contributions under the
 *   same license as the original.
 * No additional restrictions — You may not apply legal terms or technological measures that legally restrict others
 *   from doing anything the license permits.
 *
 * Notices:
 *
 * You do not have to comply with the license for elements of the material in the public domain or where your use is
 * permitted by an applicable exception or limitation.
 *
 * No warranties are given. The license may not give you all of the permissions necessary for your intended use. For
 * example, other rights such as publicity, privacy, or moral rights may limit how you use the material.
 *
 * Additional Restrictions:
 *
 * By using this software, you acknowledge that it has been provided without warranty or support.  Any damage of any
 * kind that results from the use of this software, whether indirectly or directly, is the sole responsibility of you,
 * the user of the software.  You agree that the original developer has no liability for any damage or other problems
 * arising from your use of the software, or an end-user's use of the software via the use of your project which uses
 * the software.
 *
 */

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
      case something: A => Option(something) == Option(that)
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