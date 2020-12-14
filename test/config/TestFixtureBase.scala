/*
 * Copyright 2020 HM Revenue & Customs
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

package config

import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.RequestHeader
import play.api.test.FakeRequest
import services.AuditService

import scala.util.Random
import uk.gov.hmrc.http.HeaderCarrier

abstract class TestFixtureBase extends MockitoSugar {
  implicit val hc = HeaderCarrier()
  implicit val rh: RequestHeader = FakeRequest("GET", "some/path")

  val mockAuditService = mock[AuditService]

  def rnd(prefix: String) = s"$prefix-${Random.nextInt(100)}"
  def maybe[A](value: => A) = if (Random.nextBoolean()) Some(value) else None
  def maybeRnd(prefix: String) = maybe(rnd(prefix))
  def someRnd(prefix: String) = Some(rnd(prefix))
  def yesNoRnd = if (Random.nextBoolean()) Some("Yes") else Some("No")
}
