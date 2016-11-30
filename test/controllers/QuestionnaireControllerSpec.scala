/*
 * Copyright 2016 HM Revenue & Customs
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
import mocks.QuestionnaireInMemoryRepository
import mocks.application.DocumentRootInMemoryRepository
import org.mockito.ArgumentMatchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import repositories.QuestionnaireRepository
import repositories.application.GeneralApplicationRepository
import services.AuditService
import testkit.UnitWithAppSpec
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.language.postfixOps

class QuestionnaireControllerSpec extends UnitWithAppSpec with Results {

  "The Questionnaire API" should {
    "append questions to the questionnaire for the current application" in new TestFixture {
      val appId = "1234"

      status(TestQuestionnaireController.addSection(appId, "section1")(addQuestionnaireSection(appId, "section1")(
        s"""
           |{
           |  "questions": [
           |   {"question":"parent occupation"   , "answer": {"unknown":true } },
           |   {"question":"other stuff" , "answer": {"answer": "other", "otherDetails":"something" } }
           |  ]
           |}
           |""".stripMargin
      ))) must be(202)

      await(QuestionnaireInMemoryRepository.find(appId)).size must be(2)
      verify(mockAuditService).logEvent(eqTo("QuestionnaireSectionSaved"), eqTo(
        Map("section" -> "section1")))(any[HeaderCarrier], any[RequestHeader])

      status(TestQuestionnaireController.addSection(appId, "section2")(addQuestionnaireSection(appId, "section2")(
        s"""
           |{
           |  "questions": [
           |   {"question":"income"   , "answer": {"unknown":true } },
           |   {"question":"stuff 1" , "answer": {"answer": "other"} },
           |   {"question":"stuff 2" , "answer": {"answer": "other", "otherDetails":"something" } }
           |  ]
           |}
           |""".stripMargin
      ))) must be(202)

      await(QuestionnaireInMemoryRepository.find(appId)).size must be(5)
      verify(mockAuditService).logEvent(eqTo("QuestionnaireSectionSaved"), eqTo(
        Map("section" -> "section2")))(any[HeaderCarrier], any[RequestHeader])
    }

    "return a system error on invalid json" in new TestFixture {
      val result = TestQuestionnaireController.addSection("1234", "section1")(addQuestionnaireSection("1234", "section1")(
        s"""
           |{
           |  "wrongField1":"wrong",
           |  "wrongField2":"wrong"
           |}
        """.stripMargin
      ))

      status(result) must be(400)
    }
  }

  trait TestFixture extends TestFixtureBase {
    object TestQuestionnaireController extends QuestionnaireController {
      override val qRepository: QuestionnaireRepository = QuestionnaireInMemoryRepository
      override val appRepository: GeneralApplicationRepository = DocumentRootInMemoryRepository
      override val auditService: AuditService = mockAuditService
    }

    def addQuestionnaireSection(applicationId: String, section: String)(jsonString: String) = {
      val json = Json.parse(jsonString)
      FakeRequest(Helpers.PUT, controllers.routes.QuestionnaireController.addSection(applicationId, section).url, FakeHeaders(), json)
        .withHeaders("Content-Type" -> "application/json")
    }
  }
}
