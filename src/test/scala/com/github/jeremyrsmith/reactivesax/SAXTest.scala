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

import org.scalamock.MockParameter
import org.scalamock.scalatest.MockFactory
import org.xml.sax.{Attributes, ContentHandler}

import scala.xml.{Text, Node, Elem, PCData}


trait SAXTest { self: MockFactory =>

  implicit class ContentHandlerMock(val handler: ContentHandler) {
    def expectsXML(doc: Node) = {

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

      inSequence {
        (handler.startDocument _).expects()
        recurse(doc, {
          case el: Elem =>
            handler.startElement _ expects anElement(el.label, el.attributes.asAttrMap.toSet: Set[(String, String)])
          case Text(str) if str.trim() != "" =>
            handler.characters _ expects aTextNode(str)
          case Text(str) =>
            (handler.characters _).expects(aTextNode("")).noMoreThanOnce()
          case PCData(str) =>
            (handler.characters _).expects(aTextNode(str))
        }, { el =>
          handler.endElement _ expects elementEnd(el.label)
        })
        (handler.endDocument _).expects()
      }
    }
  }

  val NoAttributes = Set.empty[(String, String)]

  def anElement(name: String, attrs: MockParameter[Set[(String, String)]]) = where[String, String, String, Attributes] {
    case (uri, localName, qName, atts) =>
      val inputAttributes = (0 to atts.getLength - 1).map(i => atts.getQName(i) -> atts.getValue(i)).toMap.toSet

      val (regularAttributes, namespaceAttributes) = inputAttributes.partition(!_._1.startsWith("xmlns"))

      qName == name &&
      attrs.equals(regularAttributes)
  }

  def aTextNode(content: String) = where[Array[Char], Int, Int] {
    case (arr, start, len) =>
      new String(arr, start, len).trim() == content.trim()
  }

  def elementEnd(name: String) = where[String, String, String] {
    case (uri, localName, qName) => qName == name
  }
}