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

package controllers

import config.TestFixtureBase
import model.Exceptions.CannotUpdatePartnerGraduateProgrammes
import model.command.PartnerGraduateProgrammesExchangeExamples
import org.mockito.ArgumentMatchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import play.api.mvc._
import play.api.test.Helpers._
import services.partnergraduateprogrammes.PartnerGraduateProgrammesService
import testkit.UnitWithAppSpec

import scala.concurrent.Future
import scala.language.postfixOps
import uk.gov.hmrc.http.HeaderCarrier

class PartnerGraduateProgrammesControllerSpec extends UnitWithAppSpec {

  "Update" should {
    "return CREATED and update the partnet graduate programmes and audit PartnerGraduateProgrammesSaved event" in new TestFixture {
      when(mockPartnerGraduateProgrammesService.update(AppId, PartnerGraduateProgrammesExchangeExamples.InterestedNotAll)
      ).thenReturn(Future.successful(()))
      val result = controller.update(AppId)(Request)
      status(result) must be(CREATED)
      verify(mockAuditService).logEvent(eqTo("PartnerGraduateProgrammesSaved"))(any[HeaderCarrier], any[RequestHeader])
    }

    "return BAD_REQUEST when there is a CannotUpdatePartnerGraduateProgrammes exception" in new TestFixture {
      when(mockPartnerGraduateProgrammesService.update(AppId, PartnerGraduateProgrammesExchangeExamples.InterestedNotAll))
        .thenReturn(Future.failed(CannotUpdatePartnerGraduateProgrammes(UserId)))
      val result = controller.update(AppId)(Request)
      status(result) must be(BAD_REQUEST)
      verify(mockAuditService, times(0)).logEvent(eqTo("AssistanceDetailsSaved"))(any[HeaderCarrier], any[RequestHeader])
    }
  }

  trait TestFixture extends TestFixtureBase {
    val mockPartnerGraduateProgrammesService = mock[PartnerGraduateProgrammesService]
    val controller = new PartnerGraduateProgrammesController {
      val partnerGraduateProgrammesService = mockPartnerGraduateProgrammesService
      val auditService = mockAuditService
    }

    val Request = fakeRequest(PartnerGraduateProgrammesExchangeExamples.InterestedNotAll)
  }
}
