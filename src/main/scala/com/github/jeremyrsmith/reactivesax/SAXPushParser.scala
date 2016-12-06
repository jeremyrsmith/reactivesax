package com.github.jeremyrsmith.reactivesax

import java.io.Writer
import fsm.XmlFsm
import org.xml.sax.{XMLFilter, ContentHandler}
import org.xml.sax.helpers.XMLFilterImpl


sealed trait IsHandlerMutable {
  val handler: ContentHandler
}
sealed case class NotMutable(handler: ContentHandler) extends IsHandlerMutable
sealed case class Mutable() extends IsHandlerMutable {
  val handler = new XMLFilterImpl
}

/**
 * Writable push parser
 *
 * You (or a library) can push data to the parser with the [[write]] method.  All input will be immediately parsed, and
 * SAX events will be pushed to the specified [[ContentHandler]].
 *
 * You must call [[open]] to start a document
 * You must call [[close]] to end a document
 *
 * @param m Evidence as to whether or not this parser has a mutable ContentHandler (not recommended, but sometimes
 *          necessary).  See [[SAXPushParser.apply]]
 * @tparam HandlerMutability Specifies statically whether or not this parser has a mutable [[ContentHandler]]
 */
class SAXPushParser[HandlerMutability <: IsHandlerMutable](implicit val m: HandlerMutability) extends Writer {

  val fsm = new XmlFsm(m.handler)

  def setContentHandler(handler: ContentHandler)(implicit mustBeMutable: Mutable) = mustBeMutable.handler.setContentHandler(handler)

  override def flush(): Unit = {} //not needed

  override def write(cbuf: Array[Char], off: Int, len: Int): Unit = {
    off to off + len - 1 foreach {
      i => fsm.next(cbuf(i))
    }
  }

  override def close(): Unit = fsm.close()
  def open(): Unit = fsm.open()

}

object SAXPushParser {
  /**
   * Create a mutable [[SAXPushParser]]
   * You can (and must) call [[SAXPushParser.setContentHandler()]] on the resulting parser.
   * @return a [[SAXPushParser[Mutable]]]
   */
  def apply() = new SAXPushParser()(Mutable())

  /**
   * Create an immutable [[SAXPushParser]]
   * You won't be able to call [[SAXPushParser.setContentHandler()]] on the resulting parser.  Instead, all SAX events
   * throughout the lifetime of this parser (which may encompass multiple documents, as long as [[SAXPushParser.close()]]
   * and [[SAXPushParser.open()]] are called between them) will be pushed to the provided [[ContentHandler]].
   * @param handler
   * @return
   */
  def apply(handler: ContentHandler) = new SAXPushParser()(NotMutable(handler))
}
