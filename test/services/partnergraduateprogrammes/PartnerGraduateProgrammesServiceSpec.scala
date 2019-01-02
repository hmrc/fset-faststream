/*
 * Copyright 2019 HM Revenue & Customs
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

package services.partnergraduateprogrammes

import model.command.PartnerGraduateProgrammesExchangeExamples
import model.persisted.PartnerGraduateProgrammesExamples
import org.mockito.ArgumentMatchers.{ eq => eqTo }
import org.mockito.Mockito._
import repositories.partnergraduateprogrammes.PartnerGraduateProgrammesRepository
import services.BaseServiceSpec

import scala.concurrent.Future

class PartnerGraduateProgrammesServiceSpec extends BaseServiceSpec {

  "update" should {
    "update partner graduate programmes successfully" in new TestFixture {
      when(mockPgpRepository.update(eqTo(AppId), eqTo(PartnerGraduateProgrammesExamples.InterestedNotAll))
      ).thenReturn(Future.successful(()))

      val response = service.update(AppId, PartnerGraduateProgrammesExchangeExamples.InterestedNotAll).futureValue
      response mustBe unit
    }
  }

  "find partner graduate programmes" should {
    "return partner graduate programmes" in new TestFixture {
      when(mockPgpRepository.find(AppId)).thenReturn(Future.successful(PartnerGraduateProgrammesExamples.InterestedNotAll))

      val response = service.find(AppId).futureValue

      response mustBe PartnerGraduateProgrammesExchangeExamples.InterestedNotAll
    }
  }

  trait TestFixture  {
    val mockPgpRepository = mock[PartnerGraduateProgrammesRepository]

    val service = new PartnerGraduateProgrammesService {
      val pgpRepository = mockPgpRepository
    }
  }
}
