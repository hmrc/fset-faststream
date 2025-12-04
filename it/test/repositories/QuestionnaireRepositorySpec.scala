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

import model.Exceptions.NotFoundException
import model.persisted.{QuestionnaireAnswer, QuestionnaireQuestion}
import model.report.QuestionnaireReportItem
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import org.scalatestplus.mockito.MockitoSugar
import repositories.application.DiversityQuestionsText
import services.reporting.SocioEconomicScoreCalculator
import testkit.MongoRepositorySpec

class QuestionnaireRepositorySpec extends MongoRepositorySpec with MockitoSugar {

  override val collectionName: String = CollectionNames.QUESTIONNAIRE

  "The Questionnaire Repo" should {
    "create indexes for the repository" in new TestFixture {
      val indexes = indexDetails(questionnaireRepo).futureValue
      indexes must contain theSameElementsAs
        Seq(
          IndexDetails(name = "_id_", keys = Seq(("_id", "Ascending")), unique = false),
          IndexDetails(name = "applicationId_1", keys = Seq(("applicationId", "Ascending")), unique = true)
        )
    }

    "create collection, append questions to the application and overwrite existing questions" in new TestFixture {
      val applicationId = System.currentTimeMillis() + ""
      questionnaireRepo.addQuestions(applicationId, List(QuestionnaireQuestion("what?",
        QuestionnaireAnswer(answer = Some("answer1"), otherDetails = None, unknown = None)))).futureValue
      val result = questionnaireRepo.find(applicationId).futureValue
      result.size mustBe 1

      // Replace the existing question
      questionnaireRepo.addQuestions(applicationId, List(QuestionnaireQuestion("what?",
        QuestionnaireAnswer(answer = Some("answer2"), otherDetails = None, unknown = None)))).futureValue
      val result1 = questionnaireRepo.find(applicationId).futureValue

      result1.size mustBe 1
      result1.head.answer.answer mustBe Some("answer2")

      // Add a new question
      questionnaireRepo.addQuestions(applicationId, List(QuestionnaireQuestion("where?",
        QuestionnaireAnswer(answer = None, otherDetails = None, unknown = Some(true))))).futureValue
      val result2 = questionnaireRepo.find(applicationId).futureValue
      result2.size mustBe 2
    }

    "find questions should return a map of questions/answers ignoring the non answered ones" in new TestFixture {
      val applicationId = System.currentTimeMillis() + ""

      val emptyAnswer = QuestionnaireAnswer(answer = None, otherDetails = None, unknown = Some(true))

      questionnaireRepo.addQuestions(applicationId, List(QuestionnaireQuestion("what?",
        QuestionnaireAnswer(answer = Some("nada"), otherDetails = None, unknown = None)))).futureValue
      questionnaireRepo.addQuestions(applicationId, List(QuestionnaireQuestion("where?", emptyAnswer))).futureValue
      val result2 = questionnaireRepo.findQuestions(applicationId).futureValue

      result2.keys.size mustBe 2
      result2("where?") mustBe emptyAnswer
    }

    "return data relevant to the pass mark report" in new TestFixture {
      when(socioEconomicCalculator.calculate(any())).thenReturn("SES Score")
      submitQuestionnaires()

      val report = questionnaireRepo.findForOnlineTestPassMarkReport(List(applicationId1, applicationId2, applicationId3)).futureValue

      report mustBe Map(
        applicationId1 -> QuestionnaireReportItem(
          sex = Some("Male"), sexualOrientation = Some("Straight"), ethnicity = Some("Black"), isEnglishYourFirstLanguage = Some("Yes"),
          parentEmploymentStatus = Some("Unemployed"), parentOccupation = None, parentTypeOfWorkAtAge14 = Some("Unemployed"),
          parentEmployedOrSelf = None, parentCompanySize = None, parentSuperviseEmployees = None, lowerSocioEconomicBackground = None,
          socioEconomicScore = "SES Score",
          university = Some("W01-USW"), categoryOfDegree = Some("Computing"), degreeType = Some("BSc/MSc/Eng"),
          postgradUniversity = None, postgradCategoryOfDegree = None, postgradDegreeType = None
        ),
        applicationId2 -> QuestionnaireReportItem(
          sex = Some("Female"), sexualOrientation = Some("Lesbian"), ethnicity = Some("White"), isEnglishYourFirstLanguage = Some("Yes"),
          parentEmploymentStatus = Some("Employed"), parentOccupation = Some("Modern professional"),
          parentTypeOfWorkAtAge14 = Some("Modern professional"),
          parentEmployedOrSelf = Some("Part-time employed"), parentCompanySize = Some("Large (26-500)"), parentSuperviseEmployees = None,
          lowerSocioEconomicBackground = Some("No"), socioEconomicScore = "SES Score",
          university = Some("W17-WARR"), categoryOfDegree = Some("Computing"), degreeType = Some("BSc/MSc/Eng"),
          postgradUniversity = None, postgradCategoryOfDegree = None, postgradDegreeType = None
        )
      )
    }

    "handle questions answered with unknown" in new TestFixture {
      when(socioEconomicCalculator.calculate(any())).thenReturn("SES Score")
      questionnaireRepo.addQuestions(applicationId1, submittedQuestionnaire3).futureValue

      val report = questionnaireRepo.findForOnlineTestPassMarkReport(List(applicationId1)).futureValue

      val dontKnowText = "I don't know/prefer not to say"

      report mustBe Map(
        applicationId1 -> QuestionnaireReportItem(
          sex = Some(dontKnowText), sexualOrientation = Some(dontKnowText), ethnicity = Some(dontKnowText),
          isEnglishYourFirstLanguage = Some(dontKnowText), parentEmploymentStatus = Some("Employed"),
          parentOccupation = Some(dontKnowText), parentTypeOfWorkAtAge14 = Some(dontKnowText),
          parentEmployedOrSelf = Some(dontKnowText), parentCompanySize = Some(dontKnowText),
          parentSuperviseEmployees = Some(dontKnowText),
          lowerSocioEconomicBackground = Some(dontKnowText), socioEconomicScore = "SES Score",
          university = Some(dontKnowText), categoryOfDegree = None, degreeType = None,
          postgradUniversity = None, postgradCategoryOfDegree = None, postgradDegreeType = None
        )
      )
    }

    "calculate the socioeconomic score for the pass mark report" in new TestFixture {
      when(socioEconomicCalculator.calculate(any())).thenReturn("SES Score")
      submitQuestionnaire()

      questionnaireRepo.findForOnlineTestPassMarkReport(List(applicationId1)).futureValue

      verify(socioEconomicCalculator).calculate(Map(
        sex -> "Male",
        sexualOrientation -> "Straight",
        ethnicGroup -> "Black",
        englishLanguage -> "Yes",
        universityName -> "W01-USW",
        categoryOfDegree -> "Computing",
        degreeType -> "BSc/MSc/Eng",
        highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Unemployed",
        lowerSocioEconomicBackground -> "Unknown"
      ))
    }

    "findQuestionsByIds" in new TestFixture {
      when(socioEconomicCalculator.calculate(any())).thenReturn("SES Score")
      submitQuestionnaires()

      val report = questionnaireRepo.findQuestionsByIds(List(applicationId1, applicationId2, applicationId3)).futureValue

      report mustBe Map(
        applicationId1 -> QuestionnaireReportItem(
          sex = Some("Male"), sexualOrientation = Some("Straight"), ethnicity = Some("Black"), isEnglishYourFirstLanguage = Some("Yes"),
          parentEmploymentStatus = Some("Unemployed"), parentOccupation = None, parentTypeOfWorkAtAge14 = Some("Unemployed"),
          parentEmployedOrSelf = None, parentCompanySize = None, parentSuperviseEmployees = None,
          lowerSocioEconomicBackground = None, socioEconomicScore = "SES Score",
          university = Some("W01-USW"), categoryOfDegree = Some("Computing"), degreeType = Some("BSc/MSc/Eng"),
          postgradUniversity = None, postgradCategoryOfDegree = None, postgradDegreeType = None
        ),
        applicationId2 -> QuestionnaireReportItem(
          sex = Some("Female"), sexualOrientation = Some("Lesbian"), ethnicity = Some("White"), isEnglishYourFirstLanguage = Some("Yes"),
          parentEmploymentStatus = Some("Employed"), parentOccupation = Some("Modern professional"),
          parentTypeOfWorkAtAge14=Some("Modern professional"), parentEmployedOrSelf = Some("Part-time employed"),
          parentCompanySize = Some("Large (26-500)"), parentSuperviseEmployees = None,
          lowerSocioEconomicBackground = Some("No"), socioEconomicScore = "SES Score",
          university = Some("W17-WARR"), categoryOfDegree = Some("Computing"), degreeType = Some("BSc/MSc/Eng"),
          postgradUniversity = None, postgradCategoryOfDegree = None, postgradDegreeType = None
        ),
        applicationId3 -> QuestionnaireReportItem(
          sex = Some("Female"), sexualOrientation = Some("Lesbian"), ethnicity = Some("White"), isEnglishYourFirstLanguage = Some("Yes"),
          parentEmploymentStatus = None, parentOccupation = None, parentTypeOfWorkAtAge14 = None,
          parentEmployedOrSelf = None, parentCompanySize = None, parentSuperviseEmployees = None,
          lowerSocioEconomicBackground = None, socioEconomicScore = "",
          university = None, categoryOfDegree = None, degreeType = None,
          postgradUniversity = None, postgradCategoryOfDegree = None, postgradDegreeType = None
        )
      )
    }

    "find all for diversity report" in new TestFixture {
      when(socioEconomicCalculator.calculate(any())).thenReturn("SES Score")
      submitQuestionnaires()

      val report = questionnaireRepo.findAllForDiversityReport.futureValue
      report mustBe Map(
        applicationId1 -> QuestionnaireReportItem(
          sex = Some("Male"), sexualOrientation = Some("Straight"), ethnicity = Some("Black"), isEnglishYourFirstLanguage = Some("Yes"),
          parentEmploymentStatus = Some("Unemployed"), parentOccupation = None, parentTypeOfWorkAtAge14 = Some("Unemployed"),
          parentEmployedOrSelf = None, parentCompanySize = None, parentSuperviseEmployees = None,
          lowerSocioEconomicBackground = None, socioEconomicScore = "SES Score",
          university = Some("W01-USW"), categoryOfDegree = Some("Computing"), degreeType = Some("BSc/MSc/Eng"),
          postgradUniversity = None, postgradCategoryOfDegree = None, postgradDegreeType = None
        ),
        applicationId2 -> QuestionnaireReportItem(
          sex = Some("Female"), sexualOrientation = Some("Lesbian"), ethnicity = Some("White"), isEnglishYourFirstLanguage = Some("Yes"),
          parentEmploymentStatus = Some("Employed"), parentOccupation = Some("Modern professional"),
          parentTypeOfWorkAtAge14=Some("Modern professional"), parentEmployedOrSelf = Some("Part-time employed"),
          parentCompanySize = Some("Large (26-500)"), parentSuperviseEmployees = None,
          lowerSocioEconomicBackground = Some("No"), socioEconomicScore = "SES Score",
          university = Some("W17-WARR"), categoryOfDegree = Some("Computing"), degreeType = Some("BSc/MSc/Eng"),
          postgradUniversity = None, postgradCategoryOfDegree = None, postgradDegreeType = None
        ),
        applicationId3 -> QuestionnaireReportItem(
          sex = Some("Female"), sexualOrientation = Some("Lesbian"), ethnicity = Some("White"), isEnglishYourFirstLanguage = Some("Yes"),
          parentEmploymentStatus = None, parentOccupation = None, parentTypeOfWorkAtAge14 = None, parentEmployedOrSelf = None,
          parentCompanySize = None, parentSuperviseEmployees = None, lowerSocioEconomicBackground = None, socioEconomicScore = "",
          university = None, categoryOfDegree = None, degreeType = None,
          postgradUniversity = None, postgradCategoryOfDegree = None, postgradDegreeType = None
        )
      )
    }

    "remove questions" in new TestFixture {
      val applicationId = "appId"
      questionnaireRepo.addQuestions(applicationId, List(QuestionnaireQuestion("what?",
        QuestionnaireAnswer(answer = Some("answer1"), otherDetails = None, unknown = None)))).futureValue
      val result = questionnaireRepo.find(applicationId).futureValue
      result.size mustBe 1

      questionnaireRepo.removeQuestions(applicationId).futureValue
      val result1 = questionnaireRepo.find(applicationId).futureValue
      result1.size mustBe 0
    }

    "throw an exception if a delete is attempted but there are no questions to remove" in new TestFixture {
      val applicationId = "appId"
      val result = questionnaireRepo.find(applicationId).futureValue
      result.size mustBe 0

      val result1 = questionnaireRepo.removeQuestions(applicationId).failed.futureValue
      result1 mustBe a[NotFoundException]
    }
  }

  trait TestFixture extends DiversityQuestionsText {
    val applicationId1 = "abc"
    val applicationId2 = "123"
    val applicationId3 = "partiallyCompleteId"
    val submittedQuestionnaire1 = List(
      QuestionnaireQuestion(sex, QuestionnaireAnswer(Some("Male"), otherDetails = None, unknown = None)),
      QuestionnaireQuestion(sexualOrientation, QuestionnaireAnswer(Some("Straight"), otherDetails = None, unknown = None)),
      QuestionnaireQuestion(ethnicGroup, QuestionnaireAnswer(Some("Black"), otherDetails = None, unknown = None)),
      QuestionnaireQuestion(englishLanguage, QuestionnaireAnswer(Some("Yes"), otherDetails = None, unknown = None)),
      QuestionnaireQuestion(universityName, QuestionnaireAnswer(Some("W01-USW"), otherDetails = None, unknown = None)),
      QuestionnaireQuestion(categoryOfDegree, QuestionnaireAnswer(Some("Computing"), otherDetails = None, unknown = None)),
      QuestionnaireQuestion(degreeType, QuestionnaireAnswer(Some("BSc/MSc/Eng"), otherDetails = None, unknown = None)),
      QuestionnaireQuestion(highestEarningParentOrGuardianTypeOfWorkAtAge14,
        QuestionnaireAnswer(Some("Unemployed"), otherDetails = None, unknown = None)
      ),
      QuestionnaireQuestion(lowerSocioEconomicBackground, QuestionnaireAnswer(answer = None, otherDetails = None, unknown = None))
    )
    val submittedQuestionnaire2 = List(
      QuestionnaireQuestion(sex, QuestionnaireAnswer(Some("Female"), otherDetails = None, unknown = None)),
      QuestionnaireQuestion(sexualOrientation, QuestionnaireAnswer(Some("Lesbian"), otherDetails = None, unknown = None)),
      QuestionnaireQuestion(ethnicGroup, QuestionnaireAnswer(Some("White"), otherDetails = None, unknown = None)),
      QuestionnaireQuestion(englishLanguage, QuestionnaireAnswer(Some("Yes"), otherDetails = None, unknown = None)),
      QuestionnaireQuestion(universityName, QuestionnaireAnswer(Some("W17-WARR"), otherDetails = None, unknown = None)),
      QuestionnaireQuestion(categoryOfDegree, QuestionnaireAnswer(Some("Computing"), otherDetails = None, unknown = None)),
      QuestionnaireQuestion(degreeType, QuestionnaireAnswer(Some("BSc/MSc/Eng"), otherDetails = None, unknown = None)),
      QuestionnaireQuestion(highestEarningParentOrGuardianTypeOfWorkAtAge14,
        QuestionnaireAnswer(Some("Modern professional"), otherDetails = None, unknown = None)
      ),
      QuestionnaireQuestion(employeeOrSelfEmployed, QuestionnaireAnswer(Some("Part-time employed"), otherDetails = None, unknown = None)),
      QuestionnaireQuestion(sizeOfPlaceOfWork, QuestionnaireAnswer(Some("Large (26-500)"), otherDetails = None, unknown = None)),
      QuestionnaireQuestion(lowerSocioEconomicBackground, QuestionnaireAnswer(Some("No"), otherDetails = None, unknown = None))
    )
    val unknown = QuestionnaireAnswer(answer = None, otherDetails = None, unknown = Some(true))
    val submittedQuestionnaire3 = List(
      QuestionnaireQuestion(sex, unknown),
      QuestionnaireQuestion(sexualOrientation, unknown),
      QuestionnaireQuestion(ethnicGroup, unknown),
      QuestionnaireQuestion(englishLanguage, unknown),
      QuestionnaireQuestion(liveInUkAged14to18, QuestionnaireAnswer(Some("Yes"), otherDetails = None, unknown = None)),
      QuestionnaireQuestion(postcodeAtAge14, unknown),
      QuestionnaireQuestion(schoolNameAged14to16, unknown),
      QuestionnaireQuestion(universityName, unknown),
      QuestionnaireQuestion(superviseEmployees, unknown),
      QuestionnaireQuestion(lowerSocioEconomicBackground, unknown),
      QuestionnaireQuestion(highestEarningParentOrGuardianTypeOfWorkAtAge14, unknown),
      QuestionnaireQuestion(employeeOrSelfEmployed, unknown),
      QuestionnaireQuestion(sizeOfPlaceOfWork, unknown)
    )
    val partiallyCompleteQuestionnaire = List(
      QuestionnaireQuestion(sex, QuestionnaireAnswer(Some("Female"), otherDetails = None, unknown = None)),
      QuestionnaireQuestion(sexualOrientation, QuestionnaireAnswer(Some("Lesbian"), otherDetails = None, unknown = None)),
      QuestionnaireQuestion(ethnicGroup, QuestionnaireAnswer(Some("White"), otherDetails = None, unknown = None)),
      QuestionnaireQuestion(englishLanguage, QuestionnaireAnswer(Some("Yes"), otherDetails = None, unknown = None))
    )

    val socioEconomicCalculator = mock[SocioEconomicScoreCalculator]

    def questionnaireRepo = new QuestionnaireMongoRepository(socioEconomicCalculator, mongo)

    def submitQuestionnaire(): Unit =
      questionnaireRepo.addQuestions(applicationId1, submittedQuestionnaire1).futureValue

    def submitQuestionnaires(): Unit = {
      questionnaireRepo.addQuestions(applicationId1, submittedQuestionnaire1).futureValue
      questionnaireRepo.addQuestions(applicationId2, submittedQuestionnaire2).futureValue
      questionnaireRepo.addQuestions(applicationId3, partiallyCompleteQuestionnaire).futureValue
    }
  }
}
