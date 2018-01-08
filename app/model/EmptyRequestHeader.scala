/*
 * Copyright 2018 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package model

import java.security.cert.X509Certificate

import play.api.mvc.{ Headers, RequestHeader }

object EmptyRequestHeader extends EmptyRequestHeader

trait EmptyRequestHeader extends RequestHeader {
  override def id: Long = 1L

  override def tags: Map[String, String] = Map.empty

  override def uri: String = ""

  override def path: String = ""

  override def method: String = ""

  override def version: String = ""

  override def queryString: Map[String, Seq[String]] = Map.empty

  override def clientCertificateChain: Option[Seq[X509Certificate]] = None

  override def headers: Headers = new Headers(_headers = Nil)

  override def remoteAddress: String = ""

  override def secure: Boolean = false
}
