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
