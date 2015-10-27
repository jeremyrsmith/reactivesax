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

package com.github.jeremyrsmith.reactivesax.fsm

import java.nio.CharBuffer

import org.xml.sax.ContentHandler

import scala.collection.mutable


trait State {
  def next: PartialFunction[Char, State]
}

case class Attribute(prefix: Option[String], name: String, value: String, uri: String = "")

/**
 * Deterministic Finite State Automaton that implements some XML-like grammar.
 * The only state it keeps is:
 *  * A buffer of characters that have yet to be consumed
 *  * Two namespace stacks (a default namespace stack and a stack of namespace prefix to URI mappings)
 *
 * Other than that, it processes input one character at a time, and emits SAX events to the given receiver.
 *
 * It does no validation.
 *
 * It knows nothing about DTDs or external entities.
 *
 * It does nothing with processing instructions (other than emit them to the ContentHandler)
 *
 * It does not handle errors (nor emit them) except for a few catastrophic parse errors (in which case it will throw an exception)
 *
 * It won't issue an endDocument event until close() is called (at which point it will emit one immediately)
 *
 * @param receiver The [[ContentHandler]] which should receive SAX events
 * @param bufferSize The maximum size of the buffer.
 *                   The buffer is emptied whenever possible.  If the buffer is filled while processing a text node, the
 *                   [[ContentHandler]] will receive multiple split text nodes.  If it is filled in the middle of an element
 *                   tag, or CDATA section, I don't konw what will happen.  Probably an exception.
 *                   In general, I would avoid CDATA input.  Elements should never be longer than a few hundred characters anyway.
 */
class XmlFsm(receiver: ContentHandler, bufferSize: Int = 8192) {

  val buffer = CharBuffer.allocate(bufferSize)

  private def consumeBuffer(): String = {
    val len = buffer.position()
    buffer.rewind()
    val chars = new Array[Char](len)
    buffer.get(chars, 0, len)
    buffer.clear()
    new String(chars)
  }

  private val defaultUri = new mutable.Stack[(String, Int)]
  defaultUri.push(("", 0))
  private val namespaces = new mutable.Stack[(Map[String, String], Int)]
  namespaces.push(Map.empty[String, String] -> 0)

  private def processAttributeForNamespaces(prefix: Option[String], name: String, value: String): Unit = prefix match {
    case None if name == "xmlns" =>
      defaultUri.push((value, 0))
    case Some("xmlns") =>
      val top = namespaces.pop()
      namespaces.push(top.copy(_1 = top._1 ++ Map(name -> value)))
    case _ =>
  }

  private def findUriForPrefix(prefix: String) = {
    val found = namespaces.toList.reverse.find(_._1.contains(prefix))
    found flatMap (_._1.get(prefix))
  }

  private def elementStart(prefix: Option[String], name: String, attributes: Set[Attribute]): Unit = {
    val uri = prefix flatMap findUriForPrefix
    uri match {
      case None => receiver.startElement("", "", name, attributes)
      case Some(u) => receiver.startElement(u, name, s"$u:$name", attributes)
    }
    attributes.find(_.prefix == "xmlns") match {
      case Some(_) => namespaces.push((Map(), 0))
      case None =>
        val top = namespaces.pop()
        namespaces.push(top.copy(_2 = top._2 + 1))
    }
  }

  private def elementEnd(prefix: Option[String], name: String): Unit = {
    val uri = prefix flatMap findUriForPrefix
    uri match {
      case None => receiver.endElement("", "", name)
      case Some(u) => receiver.endElement(u, name, s"$u:$name")
    }
    namespaces.pop() match {
      case (map, count) if count > 0 =>
        namespaces.push((map, count - 1))
      case (map, count) =>
    }
  }

  private def characters(str: String) = {
    val arr = str.toCharArray
    receiver.characters(arr, 0, arr.length)
  }

  private def directive(target: String, data: Option[String]) = {
    //TODO
  }

  private def declaration(unparsed: String) = {
    //TODO
  }

  def open() = receiver.startDocument()
  def close() = if(buffer.length() > 0 && state != CharacterData && state != WhiteSpace) //character data could be one character of whitespace
    throw new Exception("Premature end of file; buffer is not empty, but writer was closed")
  else {
    //reset internal state
    buffer.clear()
    namespaces.clear()
    defaultUri.clear()
    //document is over
    receiver.endDocument()
  }

  case object Root extends State {
    def next = {
      case '<' => TagOpen
      case '&' => CharacterDataEntityOpen
      case c if c.isWhitespace =>
        buffer.put(c)
        WhiteSpace
      case c =>
        buffer.put(c)
        CharacterData
    }
  }
  case object TagOpen extends State {
    def next = {
      case '/' => ClosingTagOpen
      case '?' => DirectiveOpen
      case '!' => InstructionOpen
      case c =>
        buffer.put(c)
        OpenTagStarted
    }
  }
  case object ClosingTagOpen extends State {
    def next = {
      case '>' =>
        elementEnd(None, consumeBuffer())
        Root
      case ':' => ClosingTagPrefixed(consumeBuffer())
      case c if c.isWhitespace => this
    }
  }
  case object DirectiveOpen extends State {
    def next = {
      case c if c.isWhitespace => DirectiveNamed(consumeBuffer())
      case '?' => DirectiveClosing(consumeBuffer(), None)
    }
  }
  case class DirectiveNamed(target: String) extends State {
    def next = {
      case '?' =>
        DirectiveClosing(target, Some(consumeBuffer()))
    }
  }
  case class DirectiveClosing(target: String, data: Option[String]) extends State {
    def next = {
      case '>' =>
        directive(target, data)
        Root
      case _ => throw new Exception("Unexpected character, expected '>' during DirectiveClosing")
    }
  }
  case object InstructionOpen extends State {
    def next = {
      case '[' => CDATAExpected
      case '-' => CommentExpected
      case c =>
        buffer.put(c)
        Declaration
    }
  }
  case object CommentExpected extends State {
    def next = {
      case '-' => Comment
      case c => throw new Exception(s"Syntax error - expected <!-- to open comment, found <!-$c")
    }
  }
  case object Comment extends State {
    def next = {
      case '-' => CommentMaybeClosing(false)
      case c => this //consume and ignore
    }
  }
  case class CommentMaybeClosing(secondDash: Boolean = false) extends State {
    def next = {
      case '-' if secondDash => this
      case '-' => copy(secondDash = true)
      case '>' if secondDash => Root
      case c => Comment
    }
  }
  case object CDATAExpected extends State {
    def next = {
      case '[' =>
        if(consumeBuffer() != "CDATA")
          throw new Exception("Expected CDATA")
        CDATASection
    }
  }
  case object Declaration extends State {
    def next = {
      case '>' =>
        declaration(consumeBuffer())
        Root //TODO: parse & expose declarations
    }
  }
  case object CDATASection extends State {
    def next = {
      case ']' => CDATASectionMaybeClosing(false)
    }
  }
  case class CDATASectionMaybeClosing(secondBracket: Boolean) extends State {
    def next = {
      case ']' => if(secondBracket)
          this
        else
          this.copy(secondBracket = true)
      case '>' if secondBracket =>
        val chars = consumeBuffer().toCharArray
        receiver.characters(chars, 0, chars.length)
        Root
      case c =>
          buffer put ']'
          buffer put c
          CDATASection
    }
  }
  case object OpenTagStarted extends State {
    def next = {
      case '>' =>
        elementStart(None, consumeBuffer(), Set.empty)
        Root
      case ':' => OpenTagPrefixed(consumeBuffer())
      case '/' => TagSelfCloseStarted(OpenTag(None, consumeBuffer(), Set.empty))
      case c if c.isWhitespace => OpenTag(None, consumeBuffer(), Set.empty)
    }
  }
  case class OpenTagPrefixed(prefix: String) extends State {
    def next = {
      case '>' =>
        elementStart(Some(prefix), consumeBuffer(), Set.empty)
        Root
      case '/' => TagSelfCloseStarted(OpenTag(Some(prefix), consumeBuffer(), Set.empty))
      case c if c.isWhitespace => OpenTag(Some(prefix), consumeBuffer(), Set.empty)
    }
  }
  case class OpenTag(prefix: Option[String], name: String, attributes: Set[Attribute]) extends State {
    def next = {
      case c if c.isWhitespace => this //consume
      case '>' =>
        elementStart(prefix, name, attributes)
        Root
      case c =>
        buffer.put(c)
        AttributeOpen(this)
    }
  }
  case object ClosingTagStarted extends State {
    def next = {
      case '>' =>
        elementEnd(None, consumeBuffer())
        Root
      case ':' => ClosingTagPrefixed(consumeBuffer())
    }
  }
  case class ClosingTagPrefixed(prefix: String) extends State {
    def next = {
      case '>' =>
        elementEnd(Some(prefix), consumeBuffer())
        Root
      case c if c.isWhitespace => this //consume
    }
  }
  case class AttributeOpen(tag: OpenTag) extends State {
    def next = {
      case '=' => AttributeNeedsValue(tag, None, consumeBuffer())
      case ':' => AttributePrefixed(tag, consumeBuffer())
      case c if c.isWhitespace => AttributeNamed(tag, None, consumeBuffer())
    }
  }
  case class AttributePrefixed(tag: OpenTag, prefix: String) extends State {
    def next = {
      case '=' => AttributeNeedsValue(tag, Some(prefix), consumeBuffer())
      case c if c.isWhitespace => AttributeNamed(tag, None, consumeBuffer())
    }
  }
  case class AttributeNamed(tag: OpenTag, prefix: Option[String], name: String) extends State {
    def next = {
      case '=' => AttributeNeedsValue(tag, prefix, name)
      case c if c.isWhitespace => this
      case c =>
        buffer.put(c)
        AttributeOpen(tag.copy(attributes = tag.attributes + Attribute(prefix, name, "")))
    }
  }
  case class AttributeNeedsValue(tag: OpenTag, prefix: Option[String], name: String) extends State {
    def next = {
      case '"' => EncapsedAttributeValue(tag, prefix, name)
      case '>' =>
        val attributeValue = consumeBuffer()
        processAttributeForNamespaces(prefix, name, attributeValue)
        elementStart(tag.prefix, tag.name, tag.attributes + Attribute(prefix, name, attributeValue))
        Root
      case c if c.isWhitespace =>
        val attributeValue = consumeBuffer()
        processAttributeForNamespaces(prefix, name, attributeValue)
        tag.copy(attributes = tag.attributes + Attribute(prefix, name, attributeValue))
    }
  }
  case class EncapsedAttributeValue(tag: OpenTag, prefix: Option[String], name: String) extends State {
    def next = {
      case '"' =>
        tag.copy(attributes = tag.attributes + Attribute(prefix, name, consumeBuffer()))
    }
  }
  case class TagSelfCloseStarted(tag: OpenTag) extends State {
    def next = {
      case '>' =>
        elementStart(tag.prefix, tag.name, tag.attributes)
        elementEnd(tag.prefix, tag.name)
        Root
    }
  }
  case object WhiteSpace extends State {
    def next = {
      case c if c.isWhitespace =>
        this
      case '<' =>
        characters(consumeBuffer())
        TagOpen
      case '&' =>
        characters(consumeBuffer())
        CharacterDataEntityOpen
      case c =>
        buffer.put(c)
        CharacterData
    }
  }
  case object CharacterData extends State {
    def next = {
      case '<' =>
        characters(consumeBuffer())
        TagOpen
      case '&' =>
        characters(consumeBuffer())
        CharacterDataEntityOpen
    }
  }
  case object CharacterDataEntityOpen extends State {
    def next = {
      case ';' => throw new Exception("Empty entity")
      case '#' =>
        NumericEntityOpen
      case c =>
        buffer.put(c)
        NamedEntityOpen
    }
  }
  case object NumericEntityOpen extends State {
    def next = {
      case 'x' => HexNumericEntityOpen
      case ';' =>
        buffer.put(Integer.parseInt(consumeBuffer()).toChar)
        CharacterData
    }
  }
  case object HexNumericEntityOpen extends State {
    def next = {
      case ';' =>
        buffer.put(Integer.parseInt(consumeBuffer(), 16).toChar)
        CharacterData
    }
  }
  case object NamedEntityOpen extends State {
    def next = {
      case ';' =>
        val name = consumeBuffer()
        name match {
          case "amp" => buffer.put('&')
          case "lt" => buffer.put('<')
          case "gt" => buffer.put('>')
          case "quot" => buffer.put('"')
          case _ =>
            receiver.skippedEntity(name)
        }
        CharacterData
    }
  }
  var state: State = Root

  def next(char: Char) = {
    if(state.next.isDefinedAt(char)) {
      state = state.next(char)
    } else {
      buffer.put(char)
    }
  }


}