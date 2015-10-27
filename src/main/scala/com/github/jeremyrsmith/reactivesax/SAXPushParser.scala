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
    off to off + len foreach {
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
