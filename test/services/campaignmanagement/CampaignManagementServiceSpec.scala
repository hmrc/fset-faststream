/*
 * Copyright 2023 HM Revenue & Customs
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

package services.campaignmanagement

import connectors.AuthProviderClient
import factories.UUIDFactory
import model.Exceptions.NotFoundException
import model.Phase1TestExamples.*
import model.Phase2TestExamples.*
import model.UniqueIdentifier
import model.command.{AssessmentCentre, Fsb, ProgressResponse, SetTScoreRequest, SiftProgressResponse}
import model.exchange.campaignmanagement.{AfterDeadlineSignupCode, AfterDeadlineSignupCodeUnused}
import model.persisted.fileupload.FileUploadInfo
import model.persisted.fsac.{AnalysisExercise, AssessmentCentreTests}
import model.persisted.sift.{SchemeSpecificAnswer, SiftAnswers, SiftAnswersStatus}
import model.persisted.{CampaignManagementAfterDeadlineCode, Phase1TestProfile, Phase2TestGroup}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import repositories.application.GeneralApplicationRepository
import repositories.campaignmanagement.CampaignManagementAfterDeadlineSignupCodeRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.onlinetesting.*
import repositories.sift.SiftAnswersRepository
import repositories.*
import repositories.assessmentcentre.AssessmentCentreRepository
import repositories.fileupload.FileUploadRepository
import services.BaseServiceSpec
import testkit.MockitoImplicits.*

import java.time.{OffsetDateTime, ZoneId}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CampaignManagementServiceSpec extends BaseServiceSpec {

  "afterDeadlineSignupCodeUnusedAndValid" should {
    "return true with an expiry if code is unused and unexpired" in new TestFixture {
      val expiryTime = OffsetDateTime.now(ZoneId.of("UTC"))

      when(mockAfterDeadlineCodeRepository.findUnusedValidCode("1234")
      ).thenReturnAsync(Some(CampaignManagementAfterDeadlineCode("1234", "userId1", expiryTime, None)))

      val response = service.afterDeadlineSignupCodeUnusedAndValid("1234").futureValue
      response mustBe AfterDeadlineSignupCodeUnused(unused = true, Some(expiryTime))
    }

    "return false without an expiry if code is used or expired"  in new TestFixture {
      val expiryTime = OffsetDateTime.now(ZoneId.of("UTC"))

      when(mockAfterDeadlineCodeRepository.findUnusedValidCode("1234")
      ).thenReturnAsync(None)

      val response = service.afterDeadlineSignupCodeUnusedAndValid("1234").futureValue
      response mustBe AfterDeadlineSignupCodeUnused(unused = false, None)
    }
  }

  "generateAfterDeadlineSignupCode" should {
    "save and return a new signup code" in new TestFixture {
      when(mockAfterDeadlineCodeRepository.save(any[CampaignManagementAfterDeadlineCode]()))
        .thenReturnAsync()
      when(mockUuidFactory.generateUUID()).thenReturn("1234")

      val response = service.generateAfterDeadlineSignupCode("userId1", 48).futureValue

      response mustBe AfterDeadlineSignupCode("1234")
    }
  }

  "setPhase1TScore" should {
    "handle not finding a test profile" in new TestFixture {
      when(mockPhase1TestRepository.getTestGroup(any[String])).thenReturnAsync(None)

      val request = SetTScoreRequest(applicationId = "appId", inventoryId = None, phase = "PHASE1", tScore = 20.0)
      val response = service.setPhase1TScore(request)

      val exception = response.failed.futureValue
      exception mustBe an[IllegalStateException]
    }

    "handle finding a test profile that contains fewer than the full set of active tests" in new TestFixture {
      val phase1TestProfile = Phase1TestProfile(expirationDate,
                                    tests = List(firstPsiTest),
                                    evaluation = None)

      when(mockPhase1TestRepository.getTestGroup(any[String])).thenReturnAsync(Some(phase1TestProfile))

      val request = SetTScoreRequest(applicationId = "appId", inventoryId = None, phase = phase1, tScore = 20.0)
      val response = service.setPhase1TScore(request)

      val exception = response.failed.futureValue
      exception mustBe an[IllegalStateException]
    }

    "handle finding a test profile that contains the full set of active tests but missing one test result" in new TestFixture {
      val phase1TestProfile = Phase1TestProfile(expirationDate,
                                    tests = List(firstPsiTest, secondPsiTest.copy(testResult = None)),
                                    evaluation = None)

      when(mockPhase1TestRepository.getTestGroup(any[String])).thenReturnAsync(Some(phase1TestProfile))

      val request = SetTScoreRequest(applicationId = "appId", inventoryId = None, phase = phase1, tScore = 20.0)
      val response = service.setPhase1TScore(request)

      val exception = response.failed.futureValue
      exception mustBe an[IllegalStateException]
    }

    "successfully process a request when updating the full set of active tests with test results" in new TestFixture {
      val phase1TestProfile = Phase1TestProfile(expirationDate,
                                    tests = List(firstPsiTest, secondPsiTest),
                                    evaluation = None)
      when(mockPhase1TestRepository.getTestGroup(any[String])).thenReturnAsync(Some(phase1TestProfile))

      when(mockPhase1TestRepository.insertOrUpdateTestGroup(any[String], any[Phase1TestProfile])).thenReturnAsync()

      val request = SetTScoreRequest(applicationId = "appId", inventoryId = None, phase = phase1, tScore = 20.0)
      val response = service.setPhase1TScore(request).futureValue
      response mustBe unit
    }

    "successfully process a request when updating the full set of active tests with test results for a gis candidate" in new TestFixture {
      val phase1TestProfile = Phase1TestProfile(expirationDate,
        tests = List(firstPsiTest, thirdPsiTest), evaluation = None)
      when(mockApplicationRepository.gisByApplication(any[String])).thenReturnAsync(true)
      when(mockPhase1TestRepository.getTestGroup(any[String])).thenReturnAsync(Some(phase1TestProfile))

      when(mockPhase1TestRepository.insertOrUpdateTestGroup(any[String], any[Phase1TestProfile])).thenReturnAsync()

      val request = SetTScoreRequest(applicationId = "appId", inventoryId = None, phase = phase1, tScore = 20.0)
      val response = service.setPhase1TScore(request).futureValue
      response mustBe unit
    }

    "throw an exception when updating tests for a gis candidate and the number of tests is not as expected" in new TestFixture {
      val phase1TestProfile = Phase1TestProfile(expirationDate,
        tests = List(firstPsiTest, secondPsiTest, thirdPsiTest), evaluation = None)
      when(mockApplicationRepository.gisByApplication(any[String])).thenReturnAsync(true)
      when(mockPhase1TestRepository.getTestGroup(any[String])).thenReturnAsync(Some(phase1TestProfile))

      when(mockPhase1TestRepository.insertOrUpdateTestGroup(any[String], any[Phase1TestProfile])).thenReturnAsync()

      val request = SetTScoreRequest(applicationId = "appId", inventoryId = None, phase = phase1, tScore = 20.0)
      val exception = service.setPhase1TScore(request).failed.futureValue
      exception mustBe an[IllegalStateException]
    }

    "successfully process a request when updating a single active test" in new TestFixture {
      val phase1TestProfile = Phase1TestProfile(expirationDate,
                                    tests = List(firstPsiTest, secondPsiTest),
                                    evaluation = None)
      when(mockPhase1TestRepository.getTestGroup(any[String])).thenReturnAsync(Some(phase1TestProfile))

      when(mockPhase1TestRepository.insertOrUpdateTestGroup(any[String], any[Phase1TestProfile])).thenReturnAsync()

      val request = SetTScoreRequest(applicationId = "appId", inventoryId = Some("inventoryId1"), phase = phase1, tScore = 20.0)
      val response = service.setPhase1TScore(request).futureValue
      response mustBe unit
    }

    "handle an incorrect inventory id when processing a request to updating a single active test" in new TestFixture {
      val phase1TestProfile = Phase1TestProfile(expirationDate,
                                    tests = List(firstPsiTest, secondPsiTest, thirdPsiTest),
                                    evaluation = None)
      when(mockPhase1TestRepository.getTestGroup(any[String])).thenReturnAsync(Some(phase1TestProfile))

      when(mockPhase1TestRepository.insertOrUpdateTestGroup(any[String], any[Phase1TestProfile])).thenReturnAsync()

      val request = SetTScoreRequest(applicationId = "appId", inventoryId = Some("will-not-find"), phase = phase1, tScore = 20.0)
      val exception = service.setPhase1TScore(request).failed.futureValue
      exception mustBe a[Exception]
    }
  }

  "setPhase2TScore" should {
    "handle not finding a test profile" in new TestFixture {
      when(mockPhase2TestRepository.getTestGroup(any[String])).thenReturnAsync(None)

      val request = SetTScoreRequest(applicationId = "appId", inventoryId = None, phase = phase2, tScore = 20.0)
      val response = service.setPhase2TScore(request)

      val exception = response.failed.futureValue
      exception mustBe an[IllegalStateException]
    }

    "handle finding a test profile that contains fewer than the full set of active tests" in new TestFixture {
      val phase2TestProfile = Phase2TestGroup(expirationDate,
                                    tests = List(fifthPsiTest),
                                    evaluation = None)

      when(mockPhase2TestRepository.getTestGroup(any[String])).thenReturnAsync(Some(phase2TestProfile))

      val request = SetTScoreRequest(applicationId = "appId", inventoryId = None, phase = phase2, tScore = 20.0)
      val response = service.setPhase2TScore(request)

      val exception = response.failed.futureValue
      exception mustBe an[IllegalStateException]
    }

    "handle finding a test profile that contains the full set of active tests but missing one test result" in new TestFixture {
      val phase2TestProfile = Phase2TestGroup(expirationDate,
                                    tests = List(fifthPsiTest, sixthPsiTest.copy(testResult = None)),
                                    evaluation = None)

      when(mockPhase2TestRepository.getTestGroup(any[String])).thenReturnAsync(Some(phase2TestProfile))

      val request = SetTScoreRequest(applicationId = "appId", inventoryId = None, phase = phase2, tScore = 20.0)
      val response = service.setPhase2TScore(request)

      val exception = response.failed.futureValue
      exception mustBe an[IllegalStateException]
    }

    "successfully process a request when updating the full set of active tests with test results" in new TestFixture {
      val phase2TestProfile = Phase2TestGroup(expirationDate,
                                    tests = List(fifthPsiTest, sixthPsiTest),
                                    evaluation = None)
      when(mockPhase2TestRepository.getTestGroup(any[String])).thenReturnAsync(Some(phase2TestProfile))

      when(mockPhase2TestRepository.insertOrUpdateTestGroup(any[String], any[Phase2TestGroup])).thenReturnAsync()

      val request = SetTScoreRequest(applicationId = "appId", inventoryId = None, phase = phase2, tScore = 20.0)
      val response = service.setPhase2TScore(request).futureValue
      response mustBe unit
    }
  }

  "remove candidate" should {
    val appId = "f83a4063-2a09-411e-8c83-960f7babb9d2"
    val userId = "userId"
    val siftAnswers = Some(
      SiftAnswers(appId, SiftAnswersStatus.DRAFT, generalAnswers = None, schemeAnswers = Map.empty[String, SchemeSpecificAnswer])
    )

    "deal with a candidate who has only created their account but not started the application" in new TestFixture {
      val progress = ProgressResponse(appId)
      when(mockApplicationRepository.findProgress(any[String])).thenReturnAsync(progress)
      when(mockApplicationRepository.removeCandidate(any[String])).thenReturnAsync()
      when(mockMediaRepository.removeMedia(any[String])).thenReturnAsync()

      val contactDetailsErrorMsg = s"No document found whilst deleting contact details for id $appId"
      when(mockContactDetailsRepository.removeContactDetails(any[String]))
        .thenReturn(Future.failed(new NotFoundException(contactDetailsErrorMsg)))

      val questionsErrorMsg = s"No document found whilst deleting questions for id $appId"
      when(mockQuestionnaireRepository.removeQuestions(any[String]))
        .thenReturn(Future.failed(new NotFoundException(questionsErrorMsg)))

      when(mockSiftAnswersRepository.findSiftAnswers(any[String])).thenReturnAsync(None)

      val siftErrorMsg = s"No document found whilst deleting sift answers for id $appId"
      when(mockSiftAnswersRepository.removeSiftAnswers(any[String]))
        .thenReturn(Future.failed(new NotFoundException(siftErrorMsg)))

      val fsacAssessorErrorMsg = s"No document found whilst deleting fsac assessor scores for id $appId"
      when(mockAssessorAssessmentScoresMongoRepository.removeScores(any[UniqueIdentifier]))
        .thenReturn(Future.failed(new NotFoundException(fsacAssessorErrorMsg)))

      val fsacReviewerErrorMsg = s"No document found whilst deleting fsac reviewer scores for id $appId"
      when(mockReviewerAssessmentScoresMongoRepository.removeScores(any[UniqueIdentifier]))
        .thenReturn(Future.failed(new NotFoundException(fsacReviewerErrorMsg)))

      when(mockAssessmentCentreRepository.getTests(any[String])).thenReturnAsync(AssessmentCentreTests(analysisExercise = None))

      when(mockFileUploadRepository.retrieveMetaData(any[String])).thenReturnAsync(None)

      when(mockFileUploadRepository.deleteDocument(any[String], any[String]))
        .thenReturn(Future.failed(new NotFoundException(s"No uploaded file found with the ObjectId: for applicationId: $appId")))

      val eventsErrorMsg = s"No document found whilst deleting candidate allocations for id $appId"
      when(mockCandidateAllocationRepository.deleteAllocations(any[String]))
        .thenReturn(Future.failed(new NotFoundException(eventsErrorMsg)))

      val result = service.removeCandidate(appId, userId).futureValue
      result mustBe unit
    }

    "deal with a candidate who has entered personal details" in new TestFixture {
      val progress = ProgressResponse(appId, personalDetails = true)
      when(mockApplicationRepository.findProgress(any[String])).thenReturnAsync(progress)
      when(mockApplicationRepository.removeCandidate(any[String])).thenReturnAsync()
      when(mockMediaRepository.removeMedia(any[String])).thenReturnAsync()
      when(mockContactDetailsRepository.removeContactDetails(any[String])).thenReturnAsync()

      val questionsErrorMsg = s"No document found whilst deleting questions for id $appId"
      when(mockQuestionnaireRepository.removeQuestions(any[String]))
        .thenReturn(Future.failed(new NotFoundException(questionsErrorMsg)))

      when(mockSiftAnswersRepository.findSiftAnswers(any[String])).thenReturnAsync(None)

      val siftErrorMsg = s"No document found whilst deleting sift answers for id $appId"
      when(mockSiftAnswersRepository.removeSiftAnswers(any[String]))
        .thenReturn(Future.failed(new NotFoundException(siftErrorMsg)))

      val fsacAssessorErrorMsg = s"No document found whilst deleting fsac assessor scores for id $appId"
      when(mockAssessorAssessmentScoresMongoRepository.removeScores(any[UniqueIdentifier]))
        .thenReturn(Future.failed(new NotFoundException(fsacAssessorErrorMsg)))

      val fsacReviewerErrorMsg = s"No document found whilst deleting fsac reviewer scores for id $appId"
      when(mockReviewerAssessmentScoresMongoRepository.removeScores(any[UniqueIdentifier]))
        .thenReturn(Future.failed(new NotFoundException(fsacReviewerErrorMsg)))

      when(mockAssessmentCentreRepository.getTests(any[String])).thenReturnAsync(AssessmentCentreTests(analysisExercise = None))

      when(mockFileUploadRepository.retrieveMetaData(any[String])).thenReturnAsync(None)

      when(mockFileUploadRepository.deleteDocument(any[String], any[String]))
        .thenReturn(Future.failed(new NotFoundException(s"No uploaded file found with the ObjectId: for applicationId: $appId")))

      val eventsErrorMsg = s"No document found whilst deleting candidate allocations for id $appId"
      when(mockCandidateAllocationRepository.deleteAllocations(any[String]))
        .thenReturn(Future.failed(new NotFoundException(eventsErrorMsg)))

      val result = service.removeCandidate(appId, userId).futureValue
      result mustBe unit
    }

    "deal with a candidate who has entered diversity questions" in new TestFixture {
      val progress = ProgressResponse(appId, personalDetails = true, questionnaire = List(""))
      when(mockApplicationRepository.findProgress(any[String])).thenReturnAsync(progress)
      when(mockApplicationRepository.removeCandidate(any[String])).thenReturnAsync()
      when(mockMediaRepository.removeMedia(any[String])).thenReturnAsync()
      when(mockContactDetailsRepository.removeContactDetails(any[String])).thenReturnAsync()
      when(mockQuestionnaireRepository.removeQuestions(any[String])).thenReturnAsync()

      when(mockSiftAnswersRepository.findSiftAnswers(any[String])).thenReturnAsync(None)
      val siftErrorMsg = s"No document found whilst deleting sift answers for id $appId"
      when(mockSiftAnswersRepository.removeSiftAnswers(any[String]))
        .thenReturn(Future.failed(new NotFoundException(siftErrorMsg)))

      val fsacAssessorErrorMsg = s"No document found whilst deleting fsac assessor scores for id $appId"
      when(mockAssessorAssessmentScoresMongoRepository.removeScores(any[UniqueIdentifier]))
        .thenReturn(Future.failed(new NotFoundException(fsacAssessorErrorMsg)))

      val fsacReviewerErrorMsg = s"No document found whilst deleting fsac reviewer scores for id $appId"
      when(mockReviewerAssessmentScoresMongoRepository.removeScores(any[UniqueIdentifier]))
        .thenReturn(Future.failed(new NotFoundException(fsacReviewerErrorMsg)))

      when(mockAssessmentCentreRepository.getTests(any[String])).thenReturnAsync(AssessmentCentreTests(analysisExercise = None))

      when(mockFileUploadRepository.retrieveMetaData(any[String])).thenReturnAsync(None)

      when(mockFileUploadRepository.deleteDocument(any[String], any[String]))
        .thenReturn(Future.failed(new NotFoundException(s"No uploaded file found with the ObjectId: for applicationId: $appId")))

      val eventsErrorMsg = s"No document found whilst deleting candidate allocations for id $appId"
      when(mockCandidateAllocationRepository.deleteAllocations(any[String]))
        .thenReturn(Future.failed(new NotFoundException(eventsErrorMsg)))

      val result = service.removeCandidate(appId, userId).futureValue
      result mustBe unit
    }

    "deal with a candidate who has entered sift answers" in new TestFixture {
      val siftProgressResponse = SiftProgressResponse(siftEntered = true)
      val progress = ProgressResponse(appId, personalDetails = true, questionnaire = List(""), siftProgressResponse = siftProgressResponse)
      when(mockApplicationRepository.findProgress(any[String])).thenReturnAsync(progress)
      when(mockApplicationRepository.removeCandidate(any[String])).thenReturnAsync()
      when(mockMediaRepository.removeMedia(any[String])).thenReturnAsync()
      when(mockContactDetailsRepository.removeContactDetails(any[String])).thenReturnAsync()
      when(mockQuestionnaireRepository.removeQuestions(any[String])).thenReturnAsync()

      when(mockSiftAnswersRepository.findSiftAnswers(any[String])).thenReturnAsync(siftAnswers)

      when(mockSiftAnswersRepository.removeSiftAnswers(any[String])).thenReturnAsync()

      val fsacAssessorErrorMsg = s"No document found whilst deleting fsac assessor scores for id $appId"
      when(mockAssessorAssessmentScoresMongoRepository.removeScores(any[UniqueIdentifier]))
        .thenReturn(Future.failed(new NotFoundException(fsacAssessorErrorMsg)))

      val fsacReviewerErrorMsg = s"No document found whilst deleting fsac reviewer scores for id $appId"
      when(mockReviewerAssessmentScoresMongoRepository.removeScores(any[UniqueIdentifier]))
        .thenReturn(Future.failed(new NotFoundException(fsacReviewerErrorMsg)))

      when(mockAssessmentCentreRepository.getTests(any[String])).thenReturnAsync(AssessmentCentreTests(analysisExercise = None))

      when(mockFileUploadRepository.retrieveMetaData(any[String])).thenReturnAsync(None)

      when(mockFileUploadRepository.deleteDocument(any[String], any[String]))
        .thenReturn(Future.failed(new NotFoundException(s"No uploaded file found with the ObjectId: for applicationId: $appId")))

      val eventsErrorMsg = s"No document found whilst deleting candidate allocations for id $appId"
      when(mockCandidateAllocationRepository.deleteAllocations(any[String]))
        .thenReturn(Future.failed(new NotFoundException(eventsErrorMsg)))

      val result = service.removeCandidate(appId, userId).futureValue
      result mustBe unit
    }

    "deal with a candidate who didn't go via sift, has fsac scores assessed but not reviewed and no uploaded document" in new TestFixture {
      val assessmentCentre = AssessmentCentre(allocationConfirmed = true, scoresEntered = true)
      val progress = ProgressResponse(appId, personalDetails = true, questionnaire = List(""), assessmentCentre = assessmentCentre)
      when(mockApplicationRepository.findProgress(any[String])).thenReturnAsync(progress)
      when(mockApplicationRepository.removeCandidate(any[String])).thenReturnAsync()
      when(mockMediaRepository.removeMedia(any[String])).thenReturnAsync()
      when(mockContactDetailsRepository.removeContactDetails(any[String])).thenReturnAsync()
      when(mockQuestionnaireRepository.removeQuestions(any[String])).thenReturnAsync()

      when(mockSiftAnswersRepository.findSiftAnswers(any[String])).thenReturnAsync(None)

      val siftErrorMsg = s"No document found whilst deleting sift answers for id $appId"
      when(mockSiftAnswersRepository.removeSiftAnswers(any[String]))
        .thenReturn(Future.failed(new NotFoundException(siftErrorMsg)))

      when(mockAssessorAssessmentScoresMongoRepository.removeScores(any[UniqueIdentifier])).thenReturnAsync()

      val fsacReviewerErrorMsg = s"No document found whilst deleting fsac reviewer scores for id $appId"
      when(mockReviewerAssessmentScoresMongoRepository.removeScores(any[UniqueIdentifier]))
        .thenReturn(Future.failed(new NotFoundException(fsacReviewerErrorMsg)))

      when(mockAssessmentCentreRepository.getTests(any[String])).thenReturnAsync(AssessmentCentreTests(analysisExercise = None))

      when(mockFileUploadRepository.retrieveMetaData(any[String])).thenReturnAsync(None)

      when(mockFileUploadRepository.deleteDocument(any[String], any[String]))
        .thenReturn(Future.failed(new NotFoundException(s"No uploaded file found with the ObjectId: for applicationId: $appId")))

      when(mockCandidateAllocationRepository.deleteAllocations(any[String])).thenReturnAsync()

      val result = service.removeCandidate(appId, userId).futureValue
      result mustBe unit
    }

    "deal with a candidate who didn't go via sift, has fsac scores assessed but not reviewed and has an uploaded document" in new TestFixture {
      val assessmentCentre = AssessmentCentre(allocationConfirmed = true, scoresEntered = true)
      val progress = ProgressResponse(appId, personalDetails = true, questionnaire = List(""), assessmentCentre = assessmentCentre)
      when(mockApplicationRepository.findProgress(any[String])).thenReturnAsync(progress)
      when(mockApplicationRepository.removeCandidate(any[String])).thenReturnAsync()
      when(mockMediaRepository.removeMedia(any[String])).thenReturnAsync()
      when(mockContactDetailsRepository.removeContactDetails(any[String])).thenReturnAsync()
      when(mockQuestionnaireRepository.removeQuestions(any[String])).thenReturnAsync()

      when(mockSiftAnswersRepository.findSiftAnswers(any[String])).thenReturnAsync(None)

      val siftErrorMsg = s"No document found whilst deleting sift answers for id $appId"
      when(mockSiftAnswersRepository.removeSiftAnswers(any[String]))
        .thenReturn(Future.failed(new NotFoundException(siftErrorMsg)))

      when(mockAssessorAssessmentScoresMongoRepository.removeScores(any[UniqueIdentifier])).thenReturnAsync()

      val fsacReviewerErrorMsg = s"No document found whilst deleting fsac reviewer scores for id $appId"
      when(mockReviewerAssessmentScoresMongoRepository.removeScores(any[UniqueIdentifier]))
        .thenReturn(Future.failed(new NotFoundException(fsacReviewerErrorMsg)))

      when(mockAssessmentCentreRepository.getTests(any[String]))
        .thenReturnAsync(AssessmentCentreTests(Some(AnalysisExercise(fileId = "fileId"))))

      val fileUploadInfo = FileUploadInfo(_id = "objectId", id = "fileId",
        contentType = "application/msword", created = "2025-12-01T12:24:56.110Z", length = 99
      )
      when(mockFileUploadRepository.retrieveMetaData(any[String])).thenReturnAsync(Some(fileUploadInfo))

      when(mockFileUploadRepository.deleteDocument(any[String], any[String])).thenReturnAsync()

      when(mockCandidateAllocationRepository.deleteAllocations(any[String])).thenReturnAsync()

      val result = service.removeCandidate(appId, userId).futureValue
      result mustBe unit
    }

    "deal with a candidate who didn't go via sift, has fsac scores assessed and reviewed and an uploaded document" in new TestFixture {
      val assessmentCentre = AssessmentCentre(allocationConfirmed = true, scoresEntered = true, scoresAccepted = true)
      val progress = ProgressResponse(appId, personalDetails = true, questionnaire = List(""), assessmentCentre = assessmentCentre)
      when(mockApplicationRepository.findProgress(any[String])).thenReturnAsync(progress)
      when(mockApplicationRepository.removeCandidate(any[String])).thenReturnAsync()
      when(mockMediaRepository.removeMedia(any[String])).thenReturnAsync()
      when(mockContactDetailsRepository.removeContactDetails(any[String])).thenReturnAsync()
      when(mockQuestionnaireRepository.removeQuestions(any[String])).thenReturnAsync()

      when(mockSiftAnswersRepository.findSiftAnswers(any[String])).thenReturnAsync(None)

      val siftErrorMsg = s"No document found whilst deleting sift answers for id $appId"
      when(mockSiftAnswersRepository.removeSiftAnswers(any[String]))
        .thenReturn(Future.failed(new NotFoundException(siftErrorMsg)))

      when(mockAssessorAssessmentScoresMongoRepository.removeScores(any[UniqueIdentifier])).thenReturnAsync()

      when(mockReviewerAssessmentScoresMongoRepository.removeScores(any[UniqueIdentifier])).thenReturnAsync()

      when(mockAssessmentCentreRepository.getTests(any[String]))
        .thenReturnAsync(AssessmentCentreTests(Some(AnalysisExercise(fileId = "fileId"))))

      val fileUploadInfo = FileUploadInfo(_id = "objectId", id = "fileId",
        contentType = "application/msword", created = "2025-12-01T12:24:56.110Z", length = 99
      )
      when(mockFileUploadRepository.retrieveMetaData(any[String])).thenReturnAsync(Some(fileUploadInfo))

      when(mockFileUploadRepository.deleteDocument(any[String], any[String])).thenReturnAsync()

      when(mockCandidateAllocationRepository.deleteAllocations(any[String])).thenReturnAsync()

      val result = service.removeCandidate(appId, userId).futureValue
      result mustBe unit
    }

    "deal with a candidate who has filled in personal details but none are found" in new TestFixture {
      val progress = ProgressResponse(appId, personalDetails = true)
      when(mockApplicationRepository.findProgress(any[String])).thenReturnAsync(progress)
      when(mockAssessmentCentreRepository.getTests(any[String])).thenReturnAsync(AssessmentCentreTests(analysisExercise = None))

      when(mockApplicationRepository.removeCandidate(any[String])).thenReturnAsync()
      when(mockMediaRepository.removeMedia(any[String])).thenReturnAsync()

      val errorMsg = s"No document found whilst deleting contact details for id $appId"
      when(mockContactDetailsRepository.removeContactDetails(any[String]))
        .thenReturn(Future.failed(new NotFoundException(errorMsg)))

      val result = service.removeCandidate(appId, userId).failed.futureValue
      result mustBe a[NotFoundException]
      result.getMessage mustBe errorMsg
    }

    "deal with a candidate who has filled in diversity questions but none are found" in new TestFixture {
      val progress = ProgressResponse(appId, personalDetails = true, questionnaire = List(""))
      when(mockApplicationRepository.findProgress(any[String])).thenReturnAsync(progress)
      when(mockAssessmentCentreRepository.getTests(any[String])).thenReturnAsync(AssessmentCentreTests(analysisExercise = None))
      when(mockApplicationRepository.removeCandidate(any[String])).thenReturnAsync()
      when(mockMediaRepository.removeMedia(any[String])).thenReturnAsync()
      when(mockContactDetailsRepository.removeContactDetails(any[String])).thenReturnAsync()

      val errorMsg = s"No document found whilst deleting questions for id $appId"
      when(mockQuestionnaireRepository.removeQuestions(any[String]))
        .thenReturn(Future.failed(new NotFoundException(errorMsg)))

      val result = service.removeCandidate(appId, userId).failed.futureValue
      result mustBe a[NotFoundException]
      result.getMessage mustBe errorMsg
    }

    "deal with a candidate who has filled in sift answers but none are found" in new TestFixture {
      val siftProgressResponse = SiftProgressResponse(siftEntered = true)
      val progress = ProgressResponse(appId, personalDetails = true, questionnaire = List(""),
        siftProgressResponse = siftProgressResponse
      )
      when(mockApplicationRepository.findProgress(any[String])).thenReturnAsync(progress)
      when(mockAssessmentCentreRepository.getTests(any[String])).thenReturnAsync(AssessmentCentreTests(analysisExercise = None))
      when(mockApplicationRepository.removeCandidate(any[String])).thenReturnAsync()
      when(mockMediaRepository.removeMedia(any[String])).thenReturnAsync()
      when(mockContactDetailsRepository.removeContactDetails(any[String])).thenReturnAsync()
      when(mockQuestionnaireRepository.removeQuestions(any[String])).thenReturnAsync()
      when(mockSiftAnswersRepository.findSiftAnswers(any[String])).thenReturnAsync(siftAnswers)

      val errorMsg = s"No document found whilst deleting sift answers for id $appId"
      when(mockSiftAnswersRepository.removeSiftAnswers(any[String]))
        .thenReturn(Future.failed(new NotFoundException(errorMsg)))

      val result = service.removeCandidate(appId, userId).failed.futureValue
      result mustBe a[NotFoundException]
      result.getMessage mustBe errorMsg
    }

    "deal with a candidate who doesn't have an uploaded document but who has fsac assessor scores but none are found" in new TestFixture {
      val siftProgressResponse = SiftProgressResponse(siftReady = true)
      val assessmentCentre = AssessmentCentre(allocationConfirmed = true, scoresEntered = true)
      val progress = ProgressResponse(
        appId, personalDetails = true, questionnaire = List(""),
        siftProgressResponse = siftProgressResponse, assessmentCentre = assessmentCentre
      )
      when(mockApplicationRepository.findProgress(any[String])).thenReturnAsync(progress)
      when(mockAssessmentCentreRepository.getTests(any[String])).thenReturnAsync(AssessmentCentreTests(analysisExercise = None))
      when(mockApplicationRepository.removeCandidate(any[String])).thenReturnAsync()
      when(mockMediaRepository.removeMedia(any[String])).thenReturnAsync()
      when(mockContactDetailsRepository.removeContactDetails(any[String])).thenReturnAsync()
      when(mockQuestionnaireRepository.removeQuestions(any[String])).thenReturnAsync()
      when(mockSiftAnswersRepository.findSiftAnswers(any[String])).thenReturnAsync(siftAnswers)
      when(mockSiftAnswersRepository.removeSiftAnswers(any[String])).thenReturnAsync()

      val errorMsg = s"No document found whilst deleting fsac assessor scores for id $appId"
      when(mockAssessorAssessmentScoresMongoRepository.removeScores(any[UniqueIdentifier]))
        .thenReturn(Future.failed(new NotFoundException(errorMsg)))

      val result = service.removeCandidate(appId, userId).failed.futureValue
      result mustBe a[NotFoundException]
      result.getMessage mustBe errorMsg
    }

    "deal with a candidate who doesn't have an uploaded document but who has fsac reviewer scores but none are found" in new TestFixture {
      val siftProgressResponse = SiftProgressResponse(siftReady = true)
      val assessmentCentre = AssessmentCentre(allocationConfirmed = true, scoresEntered = true, scoresAccepted = true)
      val progress = ProgressResponse(
        appId, personalDetails = true, questionnaire = List(""), siftProgressResponse = siftProgressResponse, assessmentCentre = assessmentCentre
      )
      when(mockApplicationRepository.findProgress(any[String])).thenReturnAsync(progress)
      when(mockAssessmentCentreRepository.getTests(any[String])).thenReturnAsync(AssessmentCentreTests(analysisExercise = None))
      when(mockApplicationRepository.removeCandidate(any[String])).thenReturnAsync()
      when(mockMediaRepository.removeMedia(any[String])).thenReturnAsync()
      when(mockContactDetailsRepository.removeContactDetails(any[String])).thenReturnAsync()
      when(mockQuestionnaireRepository.removeQuestions(any[String])).thenReturnAsync()
      when(mockSiftAnswersRepository.findSiftAnswers(any[String])).thenReturnAsync(siftAnswers)
      when(mockSiftAnswersRepository.removeSiftAnswers(any[String])).thenReturnAsync()
      when(mockAssessorAssessmentScoresMongoRepository.removeScores(any[UniqueIdentifier])).thenReturnAsync()

      val errorMsg = s"No document found whilst deleting fsac reviewer scores for id $appId"
      when(mockReviewerAssessmentScoresMongoRepository.removeScores(any[UniqueIdentifier]))
        .thenReturn(Future.failed(new NotFoundException(errorMsg)))

      val result = service.removeCandidate(appId, userId).failed.futureValue
      result mustBe a[NotFoundException]
      result.getMessage mustBe errorMsg
    }

    "deal with a candidate who has an uploaded document and has fsac reviewer scores but the document isn't found" in new TestFixture {
      val siftProgressResponse = SiftProgressResponse(siftReady = true)
      val assessmentCentre = AssessmentCentre(allocationConfirmed = true, scoresEntered = true, scoresAccepted = true)
      val progress = ProgressResponse(
        appId, personalDetails = true, questionnaire = List(""), siftProgressResponse = siftProgressResponse, assessmentCentre = assessmentCentre
      )
      val fileId = "fileId"
      when(mockApplicationRepository.findProgress(any[String])).thenReturnAsync(progress)
      when(mockAssessmentCentreRepository.getTests(any[String]))
        .thenReturnAsync(AssessmentCentreTests(Some(AnalysisExercise(fileId))))
      when(mockApplicationRepository.removeCandidate(any[String])).thenReturnAsync()
      when(mockMediaRepository.removeMedia(any[String])).thenReturnAsync()
      when(mockContactDetailsRepository.removeContactDetails(any[String])).thenReturnAsync()
      when(mockQuestionnaireRepository.removeQuestions(any[String])).thenReturnAsync()
      when(mockSiftAnswersRepository.findSiftAnswers(any[String])).thenReturnAsync(siftAnswers)
      when(mockSiftAnswersRepository.removeSiftAnswers(any[String])).thenReturnAsync()
      when(mockAssessorAssessmentScoresMongoRepository.removeScores(any[UniqueIdentifier])).thenReturnAsync()
      when(mockReviewerAssessmentScoresMongoRepository.removeScores(any[UniqueIdentifier])).thenReturnAsync()

      val fileUploadInfo = FileUploadInfo(_id = "objectId", id = fileId,
        contentType = "application/msword", created = "2025-12-01T12:24:56.110Z", length = 99
      )
      when(mockFileUploadRepository.retrieveMetaData(any[String])).thenReturnAsync(Some(fileUploadInfo))

      val errorMsg = s"No uploaded file found with the ObjectId: $fileId for applicationId: $appId"
      when(mockFileUploadRepository.deleteDocument(any[String], any[String]))
        .thenReturn(Future.failed(new NotFoundException(errorMsg)))

      val result = service.removeCandidate(appId, userId).failed.futureValue
      result mustBe a[NotFoundException]
      result.getMessage mustBe errorMsg
    }

    "deal with a candidate who has been allocated to an event but none is found" in new TestFixture {
      val siftProgressResponse = SiftProgressResponse(siftReady = true)
      val assessmentCentre = AssessmentCentre(allocationConfirmed = true, scoresEntered = true, scoresAccepted = true)
      val progress = ProgressResponse(
        appId, personalDetails = true, questionnaire = List(""), siftProgressResponse = siftProgressResponse, assessmentCentre = assessmentCentre
      )
      when(mockApplicationRepository.findProgress(any[String])).thenReturnAsync(progress)
      when(mockAssessmentCentreRepository.getTests(any[String])).thenReturnAsync(AssessmentCentreTests(analysisExercise = None))
      when(mockApplicationRepository.removeCandidate(any[String])).thenReturnAsync()
      when(mockMediaRepository.removeMedia(any[String])).thenReturnAsync()
      when(mockContactDetailsRepository.removeContactDetails(any[String])).thenReturnAsync()
      when(mockQuestionnaireRepository.removeQuestions(any[String])).thenReturnAsync()
      when(mockSiftAnswersRepository.findSiftAnswers(any[String])).thenReturnAsync(siftAnswers)
      when(mockSiftAnswersRepository.removeSiftAnswers(any[String])).thenReturnAsync()
      when(mockAssessorAssessmentScoresMongoRepository.removeScores(any[UniqueIdentifier])).thenReturnAsync()
      when(mockReviewerAssessmentScoresMongoRepository.removeScores(any[UniqueIdentifier])).thenReturnAsync()

      when(mockFileUploadRepository.retrieveMetaData(any[String])).thenReturnAsync(None)

      val fileUploadErrorMsg = s"No uploaded file found with the ObjectId: for applicationId: $appId"
      when(mockFileUploadRepository.deleteDocument(any[String], any[String]))
        .thenReturn(Future.failed(new NotFoundException(fileUploadErrorMsg)))

      val errorMsg = s"No document found whilst deleting candidate allocations for id $appId"
      when(mockCandidateAllocationRepository.deleteAllocations(any[String]))
        .thenReturn(Future.failed(new NotFoundException(errorMsg)))

      val result = service.removeCandidate(appId, userId).failed.futureValue
      result mustBe a[NotFoundException]
      result.getMessage mustBe errorMsg
    }

    "deal with a candidate who has been allocated to an event and the data is deleted" in new TestFixture {
      val siftProgressResponse = SiftProgressResponse(siftReady = true)
      val assessmentCentre = AssessmentCentre(allocationConfirmed = true, scoresEntered = true, scoresAccepted = true)
      val progress = ProgressResponse(
        appId, personalDetails = true, questionnaire = List(""), siftProgressResponse = siftProgressResponse, assessmentCentre = assessmentCentre
      )
      val fileId = "fileId"
      when(mockApplicationRepository.findProgress(any[String])).thenReturnAsync(progress)
      when(mockAssessmentCentreRepository.getTests(any[String])).thenReturnAsync(AssessmentCentreTests(Some(AnalysisExercise(fileId))))
      when(mockApplicationRepository.removeCandidate(any[String])).thenReturnAsync()
      when(mockMediaRepository.removeMedia(any[String])).thenReturnAsync()
      when(mockContactDetailsRepository.removeContactDetails(any[String])).thenReturnAsync()
      when(mockQuestionnaireRepository.removeQuestions(any[String])).thenReturnAsync()
      when(mockSiftAnswersRepository.findSiftAnswers(any[String])).thenReturnAsync(siftAnswers)
      when(mockSiftAnswersRepository.removeSiftAnswers(any[String])).thenReturnAsync()
      when(mockAssessorAssessmentScoresMongoRepository.removeScores(any[UniqueIdentifier])).thenReturnAsync()
      when(mockReviewerAssessmentScoresMongoRepository.removeScores(any[UniqueIdentifier])).thenReturnAsync()

      val fileUploadInfo = FileUploadInfo(_id = "objectId", id = fileId,
        contentType = "application/msword", created = "2025-12-01T12:24:56.110Z", length = 99
      )
      when(mockFileUploadRepository.retrieveMetaData(any[String])).thenReturnAsync(Some(fileUploadInfo))

      when(mockFileUploadRepository.deleteDocument(any[String], any[String])).thenReturnAsync()

      when(mockCandidateAllocationRepository.deleteAllocations(any[String])).thenReturnAsync()

      val result = service.removeCandidate(appId, userId).futureValue
      result mustBe unit
    }

    "deal with a candidate who skipped sift and fsac and has been allocated to an fsb event" in new TestFixture {
      val fsb = Fsb(allocationConfirmed = true)
      val progress = ProgressResponse(appId, personalDetails = true, questionnaire = List(""), fsb = fsb)
      when(mockApplicationRepository.findProgress(any[String])).thenReturnAsync(progress)
      when(mockAssessmentCentreRepository.getTests(any[String])).thenReturnAsync(AssessmentCentreTests(analysisExercise = None))
      when(mockApplicationRepository.removeCandidate(any[String])).thenReturnAsync()
      when(mockMediaRepository.removeMedia(any[String])).thenReturnAsync()
      when(mockContactDetailsRepository.removeContactDetails(any[String])).thenReturnAsync()
      when(mockQuestionnaireRepository.removeQuestions(any[String])).thenReturnAsync()
      when(mockSiftAnswersRepository.findSiftAnswers(any[String])).thenReturnAsync(None)

      val siftErrorMsg = s"No document found whilst deleting sift answers for id $appId"
      when(mockSiftAnswersRepository.removeSiftAnswers(any[String]))
        .thenReturn(Future.failed(new NotFoundException(siftErrorMsg)))

      val fsacAssessorErrorMsg = s"No document found whilst deleting fsac assessor scores for id $appId"
      when(mockAssessorAssessmentScoresMongoRepository.removeScores(any[UniqueIdentifier]))
        .thenReturn(Future.failed(new NotFoundException(fsacAssessorErrorMsg)))

      val fsacReviewerErrorMsg = s"No document found whilst deleting fsac reviewer scores for id $appId"
      when(mockReviewerAssessmentScoresMongoRepository.removeScores(any[UniqueIdentifier]))
        .thenReturn(Future.failed(new NotFoundException(fsacReviewerErrorMsg)))

      when(mockFileUploadRepository.retrieveMetaData(any[String])).thenReturnAsync(None)

      val fileUploadErrorMsg = s"No uploaded file found with the ObjectId: for applicationId: $appId"
      when(mockFileUploadRepository.deleteDocument(any[String], any[String]))
        .thenReturn(Future.failed(new NotFoundException(fileUploadErrorMsg)))

      when(mockCandidateAllocationRepository.deleteAllocations(any[String])).thenReturnAsync()

      val result = service.removeCandidate(appId, userId).futureValue
      result mustBe unit
    }

    "deal with a candidate who skipped sift, has had their fsac scores assessed but not reviewed with no uploaded document" in new TestFixture {
      val assessmentCentre = AssessmentCentre(allocationConfirmed = true, scoresEntered = true)
      val progress = ProgressResponse(appId, personalDetails = true, questionnaire = List(""), assessmentCentre = assessmentCentre)
      when(mockApplicationRepository.findProgress(any[String])).thenReturnAsync(progress)
      when(mockAssessmentCentreRepository.getTests(any[String])).thenReturnAsync(AssessmentCentreTests(analysisExercise = None))
      when(mockApplicationRepository.removeCandidate(any[String])).thenReturnAsync()
      when(mockMediaRepository.removeMedia(any[String])).thenReturnAsync()
      when(mockContactDetailsRepository.removeContactDetails(any[String])).thenReturnAsync()
      when(mockQuestionnaireRepository.removeQuestions(any[String])).thenReturnAsync()
      when(mockSiftAnswersRepository.findSiftAnswers(any[String])).thenReturnAsync(None)

      val siftErrorMsg = s"No document found whilst deleting sift answers for id $appId"
      when(mockSiftAnswersRepository.removeSiftAnswers(any[String]))
        .thenReturn(Future.failed(new NotFoundException(siftErrorMsg)))

      when(mockAssessorAssessmentScoresMongoRepository.removeScores(any[UniqueIdentifier])).thenReturnAsync()

      val fsacReviewerErrorMsg = s"No document found whilst deleting fsac reviewer scores for id $appId"
      when(mockReviewerAssessmentScoresMongoRepository.removeScores(any[UniqueIdentifier]))
        .thenReturn(Future.failed(new NotFoundException(fsacReviewerErrorMsg)))

      when(mockFileUploadRepository.retrieveMetaData(any[String])).thenReturnAsync(None)

      val fileUploadErrorMsg = s"No uploaded file found with the ObjectId: for applicationId: $appId"
      when(mockFileUploadRepository.deleteDocument(any[String], any[String]))
        .thenReturn(Future.failed(new NotFoundException(fileUploadErrorMsg)))

      when(mockCandidateAllocationRepository.deleteAllocations(any[String])).thenReturnAsync()

      val result = service.removeCandidate(appId, userId).futureValue
      result mustBe unit
    }
  }

  trait TestFixture  {
    val mockAfterDeadlineCodeRepository = mock[CampaignManagementAfterDeadlineSignupCodeRepository]
    val mockUuidFactory = mock[UUIDFactory]
    val mockApplicationRepository = mock[GeneralApplicationRepository]
    val mockPhase1TestRepository = mock[Phase1TestRepository]
    val mockPhase2TestRepository = mock[Phase2TestRepository]
    val mockQuestionnaireRepository = mock[QuestionnaireRepository]
    val mockMediaRepository = mock[MediaRepository]
    val mockContactDetailsRepository = mock[ContactDetailsRepository]
    val mockSiftAnswersRepository = mock[SiftAnswersRepository]
    val mockAssessorAssessmentScoresMongoRepository = mock[AssessorAssessmentScoresMongoRepository]
    val mockReviewerAssessmentScoresMongoRepository = mock[ReviewerAssessmentScoresMongoRepository]
    val mockAssessmentCentreRepository = mock[AssessmentCentreRepository]
    val mockFileUploadRepository = mock[FileUploadRepository]
    val mockCandidateAllocationRepository = mock[CandidateAllocationRepository]
    val mockAuthProviderClient = mock[AuthProviderClient]
    val expirationDate = OffsetDateTime.now

    val service = new CampaignManagementService(
      mockAfterDeadlineCodeRepository,
      mockUuidFactory,
      mockApplicationRepository,
      mockPhase1TestRepository,
      mockPhase2TestRepository,
      mockQuestionnaireRepository,
      mockMediaRepository,
      mockContactDetailsRepository,
      mockSiftAnswersRepository,
      mockAssessorAssessmentScoresMongoRepository,
      mockReviewerAssessmentScoresMongoRepository,
      mockAssessmentCentreRepository,
      mockFileUploadRepository,
      mockCandidateAllocationRepository,
      mockAuthProviderClient
    )

    val phase1 = "PHASE1"
    val phase2 = "PHASE2"

    when(mockApplicationRepository.gisByApplication(any[String])).thenReturnAsync(false)
  }
}
