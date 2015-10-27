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

import org.xml.sax.Attributes

package object fsm {

  implicit class AttributeSet(attributes: Set[Attribute]) extends Attributes {

    val indexed = attributes.toArray

    val qNames = indexed.zipWithIndex.map {
      case (att, index) =>
        val str = att.prefix match {
          case Some(prefix) => s"$prefix:${att.name}"
          case None => att.name
        }
        str -> (index, att)
    }.toMap

    val uris = indexed.zipWithIndex.map {
      case (att, index) =>
        (att.uri, att.name) -> (index, att)
    }.toMap

    override def getType(index: Int): String = if(index >= indexed.length)
      null
    else
      "CDATA"

    override def getType(uri: String, localName: String): String = uris.get((uri, localName)).map (_ => "CDATA").orNull

    override def getType(qName: String): String = qNames.get(qName).map(_ => "CDATA").orNull

    override def getLength: Int = indexed.length

    override def getValue(index: Int): String = indexed(index).value

    override def getValue(uri: String, localName: String): String = uris.get((uri, localName)).map(_._2.value).orNull

    override def getValue(qName: String): String = qNames.get(qName).map(_._2.value).orNull

    override def getIndex(uri: String, localName: String): Int = uris.get((uri, localName)).map(_._1).getOrElse(-1)

    override def getIndex(qName: String): Int = qNames.get(qName).map(_._1).getOrElse(-1)

    override def getURI(index: Int): String = indexed(index).uri

    override def getLocalName(index: Int): String = {
      val att = indexed(index)
      att.prefix.map (_ => att.name).orNull
    }

    override def getQName(index: Int): String = {
      val att = indexed(index)
      att.prefix.map(_ + ":" + att.name).getOrElse(att.name)
    }
  }


}
