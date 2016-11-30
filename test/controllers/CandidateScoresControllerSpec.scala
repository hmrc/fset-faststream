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

import model.ApplicationStatus
import model.CandidateScoresCommands.Implicits._
import model.CandidateScoresCommands.{ ApplicationScores, CandidateScores, CandidateScoresAndFeedback, RecordCandidateScores }
import model.Commands.ApplicationAssessment
import model.PersistedObjects.PersonalDetails
import org.joda.time.LocalDate
import org.mockito.ArgumentMatchers.{ eq => eqTo }
import org.mockito.Mockito._
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import repositories.application.{ GeneralApplicationRepository, PersonalDetailsRepository }
import repositories.{ ApplicationAssessmentRepository, ApplicationAssessmentScoresRepository }
import testkit.UnitWithAppSpec

import scala.concurrent.Future

class CandidateScoresControllerSpec extends UnitWithAppSpec {

  val CandidateScoresWithFeedback = CandidateScoresAndFeedback("app1", Some(true), assessmentIncomplete = false,
    CandidateScores(Some(4.0), Some(3.0), Some(2.0)), CandidateScores(Some(4.0), Some(3.0), Some(2.0)),
    CandidateScores(Some(4.0), Some(3.0), Some(2.0)), CandidateScores(Some(4.0), Some(3.0), Some(2.0)),
    CandidateScores(Some(4.0), Some(3.0), Some(2.0)), CandidateScores(Some(4.0), Some(3.0), Some(2.0)),
    CandidateScores(Some(4.0), Some(3.0), Some(2.0)))

  "Get Candidate Scores" should {
    val assessmentDate = LocalDate.now.minusDays(1)
    val applicationAssessment = ApplicationAssessment("app1", "London (FSAC) 1", assessmentDate, "am", 1, confirmed = false)
    val personalDetails = PersonalDetails("John", "Smith", "Jon", LocalDate.now().minusYears(15), aLevel = true, stemLevel = false)

    "return basic candidate information when there is no score submitted" in new TestFixture {
      when(mockApplicationAssessmentRepository.find("app1")).thenReturn(Future.successful(applicationAssessment))
      when(mockPersonalDetailsRepository.find("app1")).thenReturn(Future.successful(personalDetails))
      when(mockApplicationAssessmentScoresRepository.tryFind("app1")).thenReturn(Future.successful(None))

      val result = TestCandidateScoresController.getCandidateScores("app1")(createGetCandidateScores("app1")).run

      status(result) must be(200)

      val recordCandidateScores = contentAsJson(result).as[ApplicationScores]
      recordCandidateScores must be(ApplicationScores(RecordCandidateScores("John", "Smith", "London (FSAC) 1", assessmentDate), None))
    }

    "return basic candidate information and score with feedback when they have been submitted" in new TestFixture {
      when(mockApplicationAssessmentRepository.find("app1")).thenReturn(Future.successful(applicationAssessment))
      when(mockPersonalDetailsRepository.find("app1")).thenReturn(Future.successful(personalDetails))
      when(mockApplicationAssessmentScoresRepository.tryFind("app1")).thenReturn(Future.successful(Some(CandidateScoresWithFeedback)))

      val result = TestCandidateScoresController.getCandidateScores("app1")(createGetCandidateScores("app1")).run

      status(result) must be(200)

      val recordCandidateScores = contentAsJson(result).as[ApplicationScores]
      recordCandidateScores must be(ApplicationScores(RecordCandidateScores("John", "Smith", "London (FSAC) 1",
        assessmentDate), Some(CandidateScoresWithFeedback)))
    }
  }

  "Save Candidate Scores" should {
    "save candidate scores & feedback and update application status" in new TestFixture {
      when(mockApplicationAssessmentScoresRepository.save(CandidateScoresWithFeedback)).thenReturn(Future.successful(()))
      when(mockApplicationRepository.updateStatus("app1", ApplicationStatus.ASSESSMENT_SCORES_ENTERED)).thenReturn(Future.successful(()))

      val result = TestCandidateScoresController.createCandidateScoresAndFeedback("app1")(
        createSaveCandidateScoresAndFeedback("app1", Json.toJson(CandidateScoresWithFeedback).toString())
      )

      status(result) must be(CREATED)
      verify(mockApplicationAssessmentScoresRepository).save(CandidateScoresWithFeedback)
      verify(mockApplicationRepository).updateStatus("app1", ApplicationStatus.ASSESSMENT_SCORES_ENTERED)
    }

    "mark application status as FAILED_TO_ATTEND when candidate didn't show and save the score" in new TestFixture {
      val candidateScores = CandidateScoresAndFeedback("app1", Some(false), assessmentIncomplete = false)
      when(mockApplicationAssessmentScoresRepository.save(candidateScores)).thenReturn(Future.successful(()))
      when(mockApplicationRepository.updateStatus("app1", ApplicationStatus.FAILED_TO_ATTEND)).thenReturn(Future.successful(()))

      val result = TestCandidateScoresController.createCandidateScoresAndFeedback("app1")(
        createSaveCandidateScoresAndFeedback("app1", Json.toJson(candidateScores).toString())
      )

      status(result) must be(CREATED)
      verify(mockApplicationAssessmentScoresRepository).save(candidateScores)
      verify(mockApplicationRepository).updateStatus("app1", ApplicationStatus.FAILED_TO_ATTEND)
    }

    "return Bad Request when attendancy is not set" in new TestFixture {
      val candidateScores = CandidateScoresAndFeedback("app1", attendancy = None, assessmentIncomplete = false)
      val result = TestCandidateScoresController.createCandidateScoresAndFeedback("app1")(
        createSaveCandidateScoresAndFeedback("app1", Json.toJson(candidateScores).toString())
      )

      status(result) must be(BAD_REQUEST)
    }
  }

  trait TestFixture {
    val mockApplicationAssessmentRepository = mock[ApplicationAssessmentRepository]
    val mockPersonalDetailsRepository = mock[PersonalDetailsRepository]
    val mockApplicationAssessmentScoresRepository = mock[ApplicationAssessmentScoresRepository]
    val mockApplicationRepository = mock[GeneralApplicationRepository]

    object TestCandidateScoresController extends CandidateScoresController {
      val aaRepository = mockApplicationAssessmentRepository
      val pRepository = mockPersonalDetailsRepository
      val aasRepository = mockApplicationAssessmentScoresRepository
      val aRepository = mockApplicationRepository
    }

    def createGetCandidateScores(applicationId: String) = {
      FakeRequest(Helpers.GET, controllers.routes.CandidateScoresController.getCandidateScores(applicationId).url, FakeHeaders(), "")
        .withHeaders("Content-Type" -> "application/json")
    }

    def createSaveCandidateScoresAndFeedback(applicationId: String, jsonString: String) = {
      val json = Json.parse(jsonString)
      FakeRequest(
        Helpers.POST,
        controllers.routes.CandidateScoresController.createCandidateScoresAndFeedback(applicationId).url, FakeHeaders(), json
      ).withHeaders("Content-Type" -> "application/json")
    }
  }
}
