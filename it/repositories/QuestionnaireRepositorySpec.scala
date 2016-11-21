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

package repositories

import model.PersistedObjects.{PersistedAnswer, PersistedQuestion}
import model.report.QuestionnaireReportItem
import org.mockito.ArgumentMatchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import services.reporting.SocioEconomicScoreCalculator
import testkit.MongoRepositorySpec

class QuestionnaireRepositorySpec extends MongoRepositorySpec with MockitoSugar {

  override val collectionName = "questionnaire"

  "The Questionnaire Repo" should {
    "create collection, append questions to the application and overwrite existing questions" in new TestFixture {
      val applicationId = System.currentTimeMillis() + ""
      questionnaireRepo.addQuestions(applicationId, List(PersistedQuestion("what?", PersistedAnswer(Some("nothing"), None, None)))).futureValue
      val result = questionnaireRepo.find(applicationId).futureValue
      result.size mustBe 1

      questionnaireRepo.addQuestions(applicationId, List(PersistedQuestion("what?", PersistedAnswer(Some("nada"), None, None)))).futureValue
      val result1 = questionnaireRepo.find(applicationId).futureValue
      result1.size mustBe 1
      result1.head.answer.answer must be(Some("nada"))

      questionnaireRepo.addQuestions(applicationId, List(PersistedQuestion("where?", PersistedAnswer(None, None, Some(true))))).futureValue
      val result2 = questionnaireRepo.find(applicationId).futureValue
      result2.size mustBe 2
    }

    "find questions should return a map of questions/answers ignoring the non answered ones" in new TestFixture {
      val applicationId = System.currentTimeMillis() + ""

      questionnaireRepo.addQuestions(applicationId, List(PersistedQuestion("what?", PersistedAnswer(Some("nada"), None, None)))).futureValue
      questionnaireRepo.addQuestions(applicationId, List(PersistedQuestion("where?", PersistedAnswer(None, None, Some(true))))).futureValue
      val result2 = questionnaireRepo.findQuestions(applicationId).futureValue

      result2.keys.size mustBe 2
      result2("where?") mustBe ""
    }

    "return data relevant to the pass mark report" in new TestFixture {
      when(socioEconomicCalculator.calculate(any())).thenReturn("SES Score")
      submitQuestionnaires()

      val report = questionnaireRepo.findForOnlineTestPassMarkReport.futureValue

      report mustBe Map(
        applicationId1 -> QuestionnaireReportItem(
          Some("Male"), Some("Straight"), Some("Black"), Some("Unemployed"), None, None,
          None, "SES Score", Some("W01-USW")),
        applicationId2 -> QuestionnaireReportItem(
          Some("Female"), Some("Lesbian"), Some("White"), Some("Employed"), Some("Modern professional"), Some("Part-time employed"),
          Some("Large (26-500)"), "SES Score", Some("W17-WARR"))
      )
    }

    "calculate the socioeconomic score for the pass mark report" in new TestFixture {
      when(socioEconomicCalculator.calculate(any())).thenReturn("SES Score")
      submitQuestionnaire()

      questionnaireRepo.findForOnlineTestPassMarkReport.futureValue

      verify(socioEconomicCalculator).calculate(Map(
        "What is your gender identity?" -> "Male",
        "What is your sexual orientation?" -> "Straight",
        "What is your ethnic group?" -> "Black",
        "What is the name of the university you received your degree from?" -> "W01-USW",
        "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Unemployed"
      ))
    }

    "find all for diversity report" in new TestFixture {
      when(socioEconomicCalculator.calculate(any())).thenReturn("SES Score")
      submitQuestionnaires()

      val report = questionnaireRepo.findAllForDiversityReport.futureValue

      report mustBe Map(
        applicationId1 -> QuestionnaireReportItem(
          Some("Male"), Some("Straight"), Some("Black"), Some("Unemployed"), None, None,
          None, "SES Score", Some("W01-USW")),
        applicationId2 -> QuestionnaireReportItem(
          Some("Female"), Some("Lesbian"), Some("White"), Some("Employed"), Some("Modern professional"), Some("Part-time employed"),
          Some("Large (26-500)"), "SES Score", Some("W17-WARR")),
        applicationId3 -> QuestionnaireReportItem(
          Some("Female"), Some("Lesbian"), Some("White"), None, None, None,
          None, "", None)
      )
    }
  }

  trait TestFixture {
    val applicationId1 = "abc"
    val applicationId2 = "123"
    val applicationId3 = "partiallyCompleteId"
    val submittedQuestionnaire1 = List(
      PersistedQuestion("What is your gender identity?", PersistedAnswer(Some("Male"), None, None)),
      PersistedQuestion("What is your sexual orientation?", PersistedAnswer(Some("Straight"), None, None)),
      PersistedQuestion("What is your ethnic group?", PersistedAnswer(Some("Black"), None, None)),
      PersistedQuestion("What is the name of the university you received your degree from?", PersistedAnswer(Some("W01-USW"), None, None)),
      PersistedQuestion("When you were 14, what kind of work did your highest-earning parent or guardian do?",
        PersistedAnswer(Some("Unemployed"), None, None))
    )
    val submittedQuestionnaire2 = List(
      PersistedQuestion("What is your gender identity?", PersistedAnswer(Some("Female"), None, None)),
      PersistedQuestion("What is your sexual orientation?", PersistedAnswer(Some("Lesbian"), None, None)),
      PersistedQuestion("What is your ethnic group?", PersistedAnswer(Some("White"), None, None)),
      PersistedQuestion("What is the name of the university you received your degree from?", PersistedAnswer(Some("W17-WARR"), None, None)),
      PersistedQuestion("When you were 14, what kind of work did your highest-earning parent or guardian do?",
        PersistedAnswer(Some("Modern professional"), None, None)),
      PersistedQuestion("Did they work as an employee or were they self-employed?", PersistedAnswer(Some("Part-time employed"), None, None)),
      PersistedQuestion("Which size would best describe their place of work?", PersistedAnswer(Some("Large (26-500)"), None, None))
    )
    val partiallyCompleteQuestionnaire = List(
      PersistedQuestion("What is your gender identity?", PersistedAnswer(Some("Female"), None, None)),
      PersistedQuestion("What is your sexual orientation?", PersistedAnswer(Some("Lesbian"), None, None)),
      PersistedQuestion("What is your ethnic group?", PersistedAnswer(Some("White"), None, None))
    )

    val socioEconomicCalculator = mock[SocioEconomicScoreCalculator]

    def questionnaireRepo = new QuestionnaireMongoRepository(socioEconomicCalculator)

    def submitQuestionnaire(): Unit =
      questionnaireRepo.addQuestions(applicationId1, submittedQuestionnaire1).futureValue

    def submitQuestionnaires(): Unit = {
      questionnaireRepo.addQuestions(applicationId1, submittedQuestionnaire1).futureValue
      questionnaireRepo.addQuestions(applicationId2, submittedQuestionnaire2).futureValue
      questionnaireRepo.addQuestions(applicationId3, partiallyCompleteQuestionnaire).futureValue
    }
  }
}
