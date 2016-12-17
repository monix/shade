/*
 * Copyright (c) 2012-2017 by its authors. Some rights reserved.
 * See the project homepage at: https://github.com/monix/shade
 *
 * Licensed under the MIT License (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy
 * of the License at:
 *
 * https://github.com/monix/shade/blob/master/LICENSE.txt
 */

package shade.testModels

sealed trait ContentPiece extends Serializable {
  import ContentPiece._

  def id: Option[Int]
  def url: String
  def creator: String
  def source: ContentSource
  def tags: Vector[String]

  val contentType = this match {
    case o: Image => "image"
    case o: Title => "title"
    case o: Article => "article"
  }

  def getTitle = this match {
    case o: Image => o.title
    case o: Title => Some(o.title)
    case o: Article => Some(o.title)
  }

  def getPhoto = this match {
    case o: Image => Some(o.photo)
    case _ => None
  }

  def getShortExcerpt = this match {
    case o: Article => Some(o.shortExcerpt)
    case _ => None
  }

  def getExcerptHtml = this match {
    case o: Article => Some(o.excerptHtml)
    case _ => None
  }

  def getContentHtml = this match {
    case o: Article => Some(o.contentHtml)
    case _ => None
  }

  def withId(id: Int) = this match {
    case o: Article => o.copy(id = Some(id))
    case o: Image => o.copy(id = Some(id))
    case o: Title => o.copy(id = Some(id))
  }

  def withTags(tags: Vector[String]) = this match {
    case o: Article => o.copy(tags = tags)
    case o: Title => o.copy(tags = tags)
    case o: Image => o.copy(tags = tags)
  }
}

object ContentPiece {
  @SerialVersionUID(23904298512054925L)
  case class Image(
    id: Option[Int],
    url: String,
    creator: String,
    photo: String,
    title: Option[String],
    source: ContentSource,
    tags: Vector[String]) extends ContentPiece

  @SerialVersionUID(9785234918758324L)
  case class Title(
    id: Option[Int],
    url: String,
    creator: String,
    title: String,
    source: ContentSource,
    tags: Vector[String]) extends ContentPiece

  @SerialVersionUID(9348538729520853L)
  case class Article(
    id: Option[Int],
    url: String,
    creator: String,
    title: String,
    shortExcerpt: String,
    excerptHtml: String,
    contentHtml: Option[String],
    source: ContentSource,
    tags: Vector[String]) extends ContentPiece
}

sealed trait ContentSource extends Serializable {
  def value: String
}

object ContentSource {
  def apply(value: String): ContentSource = value match {
    case "tumblr" => Tumblr
    case "wordpress" => WordPress
  }

  case object Tumblr extends ContentSource {
    val value = "tumblr"
  }

  case object WordPress extends ContentSource {
    val value = "wordpress"
  }
}