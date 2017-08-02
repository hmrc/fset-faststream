/*
 * Copyright 2017 HM Revenue & Customs
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

package services.assessmentscores

import factories.DateTimeFactory
import model.Exceptions.EventNotFoundException
import model.ProgressStatuses.ProgressStatus
import model.{ AllocationStatuses, ProgressStatuses, UniqueIdentifier }
import model.assessmentscores.{ AssessmentScoresAllExercises, AssessmentScoresAllExercisesExamples, AssessmentScoresExerciseExamples }
import model.command.AssessmentScoresCommands.{ AssessmentExerciseType, AssessmentScoresFindResponse, RecordCandidateScores }
import model.command.PersonalDetailsExamples
import model.persisted.{ CandidateAllocation, EventExamples }
import org.joda.time.DateTimeZone
import org.mockito.Mockito.when
import repositories.{ AssessmentScoresRepository, CandidateAllocationMongoRepository }
import repositories.events.EventsRepository
import repositories.personaldetails.PersonalDetailsRepository
import services.BaseServiceSpec
import org.mockito.ArgumentMatchers.{ eq => eqTo }
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import repositories.application.GeneralApplicationRepository

import scala.concurrent.Future

class AssessmentScoresServiceSpec extends BaseServiceSpec {

  "save" should {
    "save assessment scores with updated submitted date and update status" in new SaveTestFixture {
      val UpdatedExample = AssessmentScoresAllExercisesExamples.AllExercises.copy(
        analysisExercise = AssessmentScoresAllExercisesExamples.AllExercises.analysisExercise.map(_.copy(submittedDate = Some(now))),
        groupExercise = AssessmentScoresAllExercisesExamples.AllExercises.groupExercise.map(_.copy(submittedDate = Some(now))),
        leadershipExercise = AssessmentScoresAllExercisesExamples.AllExercises.leadershipExercise.map(_.copy(submittedDate = Some(now)))
      )
      val AppId = UpdatedExample.applicationId

      when(assessmentScoresRepositoryMock.save(eqTo(UpdatedExample))).thenReturn(Future.successful(()))
      when(applicationRepositoyMock.addProgressStatusAndUpdateAppStatus(
        AppId.toString(), ProgressStatuses.ASSESSMENT_CENTRE_SCORES_ENTERED)).thenReturn(Future.successful(()))

      val result = service.save(AssessmentScoresAllExercisesExamples.AllExercises).futureValue

      verify(assessmentScoresRepositoryMock).save(eqTo(UpdatedExample))
      verify(applicationRepositoyMock).addProgressStatusAndUpdateAppStatus(
        AppId.toString(), ProgressStatuses.ASSESSMENT_CENTRE_SCORES_ENTERED)
    }
  }

  "saveExercise" should {
    "update analysis exercise scores " +
      "when assessment scores exist and we specify we want to update analysis exercise scores" in new SaveExerciseTestFixture {
      val UpdatedExample = AssessmentScoresAllExercisesExamples.OnlyLeadershipExercise.copy(
        analysisExercise = Some(AssessmentScoresExerciseExamples.Example4.copy(submittedDate = Some(now))))
      when(assessmentScoresRepositoryMock.save(eqTo(UpdatedExample))).thenReturn(Future.successful(()))

      service.saveExercise(appId, AssessmentExerciseType.analysisExercise, AssessmentScoresExerciseExamples.Example4).futureValue

      verify(assessmentScoresRepositoryMock).save(eqTo(UpdatedExample))
      verify(applicationRepositoyMock, times(0)).addProgressStatusAndUpdateAppStatus(any[String],any[ProgressStatus])
    }

    "update group exercise scores " +
      "when assessment scores exist and we specify we want to update group exercise scores" in new SaveExerciseTestFixture {
      val UpdatedExample = AssessmentScoresAllExercisesExamples.OnlyLeadershipExercise.copy(
        groupExercise = Some(AssessmentScoresExerciseExamples.Example4.copy(submittedDate = Some(now))))
      when(assessmentScoresRepositoryMock.save(eqTo(UpdatedExample))).thenReturn(Future.successful(()))

      service.saveExercise(appId, AssessmentExerciseType.groupExercise, AssessmentScoresExerciseExamples.Example4).futureValue

      verify(assessmentScoresRepositoryMock).save(eqTo(UpdatedExample))
      verify(applicationRepositoyMock, times(0)).addProgressStatusAndUpdateAppStatus(any[String],any[ProgressStatus])
    }

    "update leadership exercise scores " +
      "when assessment scores exist and we specify we want to update leadership exercise scores" in new SaveExerciseTestFixture {
      val AppId = AssessmentScoresAllExercisesExamples.OnlyAnalysisExercise.applicationId
      when(assessmentScoresRepositoryMock.find(eqTo(AppId))).thenReturn(
        Future.successful(Some(AssessmentScoresAllExercisesExamples.OnlyAnalysisExercise)))
      val UpdatedExample = AssessmentScoresAllExercisesExamples.OnlyAnalysisExercise.copy(
        leadershipExercise = Some(AssessmentScoresExerciseExamples.Example4.copy(submittedDate = Some(now))))
      when(assessmentScoresRepositoryMock.save(eqTo(UpdatedExample))).thenReturn(Future.successful(()))

      service.saveExercise(AppId, AssessmentExerciseType.leadershipExercise, AssessmentScoresExerciseExamples.Example4).futureValue

      verify(assessmentScoresRepositoryMock).save(eqTo(UpdatedExample))
      verify(applicationRepositoyMock, times(0)).addProgressStatusAndUpdateAppStatus(any[String],any[ProgressStatus])
    }

    "update analysis exercise scores and set application status and progress status to ASSESSMENT_CENTRE_SCORES_ENTERED " +
      "when assessment scores for group and analysis exercise exist and we specify we want to update analysis exercise scores" in
      new SaveExerciseTestFixture {
        val AppId = AssessmentScoresAllExercisesExamples.AllButAnalysisExercise.applicationId
        when(assessmentScoresRepositoryMock.find(eqTo(AppId))).thenReturn(
          Future.successful(Some(AssessmentScoresAllExercisesExamples.AllButAnalysisExercise)))

        val UpdatedExample = AssessmentScoresAllExercisesExamples.AllButAnalysisExercise.copy(
        analysisExercise = Some(AssessmentScoresExerciseExamples.Example4.copy(submittedDate = Some(now))))
      when(assessmentScoresRepositoryMock.save(eqTo(UpdatedExample))).thenReturn(Future.successful(()))
      when(applicationRepositoyMock.addProgressStatusAndUpdateAppStatus(
        AppId.toString(), ProgressStatuses.ASSESSMENT_CENTRE_SCORES_ENTERED)).thenReturn(Future.successful(()))

      service.saveExercise(AppId, AssessmentExerciseType.analysisExercise, AssessmentScoresExerciseExamples.Example4).futureValue

      verify(assessmentScoresRepositoryMock).save(eqTo(UpdatedExample))
      verify(applicationRepositoyMock).addProgressStatusAndUpdateAppStatus(AppId.toString(), ProgressStatuses.ASSESSMENT_CENTRE_SCORES_ENTERED)
    }


    "create assessment scores with analysis exercise scores " +
      "when assessment scores does not exist and we pass analysis exercise scores" in new SaveExerciseTestFixture {
      when(assessmentScoresRepositoryMock.find(eqTo(appId))).thenReturn(Future.successful(None))
      val expectedAssessmentScores = AssessmentScoresAllExercises(appId,
        Some(AssessmentScoresExerciseExamples.Example4.copy(submittedDate = Some(now))), None, None)
      when(assessmentScoresRepositoryMock.save(eqTo(expectedAssessmentScores))).thenReturn(Future.successful(()))

      service.saveExercise(appId, AssessmentExerciseType.analysisExercise,
        AssessmentScoresExerciseExamples.Example4).futureValue

      verify(assessmentScoresRepositoryMock).save(eqTo(expectedAssessmentScores))

    }
  }

  "findAssessmentScoresWithCandidateSummaryByApplicationId" should {
    "return Assessment Scores response with empty assessment scores if there is not any" in
      new FindAssessmentScoresWithCandidateSummaryTestFixture {

      when(assessmentScoresRepositoryMock.find(appId)).thenReturn(Future.successful(None))

      val result = service.findAssessmentScoresWithCandidateSummaryByApplicationId(appId).futureValue

      val expectedCandidate = RecordCandidateScores(
        appId,
        PersonalDetailsExamples.completed.firstName,
        PersonalDetailsExamples.completed.lastName,
        EventExamples.e1WithSession.venue.description,
        today,
        UniqueIdentifier(EventExamples.e1WithSession.sessions.head.id)
      )
      val expectedResult = AssessmentScoresFindResponse(expectedCandidate, None)
      result mustBe expectedResult
    }

    "return Assessment Scores response with assessment scores if there are assessment scores" in
      new FindAssessmentScoresWithCandidateSummaryTestFixture {
      when(assessmentScoresRepositoryMock.find(appId)).thenReturn(
        Future.successful(Some(AssessmentScoresAllExercisesExamples.OnlyLeadershipExercise)))

      val result = service.findAssessmentScoresWithCandidateSummaryByApplicationId(appId).futureValue

      val expectedCandidate = RecordCandidateScores(
        appId,
        PersonalDetailsExamples.completed.firstName,
        PersonalDetailsExamples.completed.lastName,
        EventExamples.e1WithSession.venue.description,
        today,
        UniqueIdentifier(EventExamples.e1WithSession.sessions.head.id)
      )
      val expectedResult = AssessmentScoresFindResponse(expectedCandidate, Some(AssessmentScoresAllExercisesExamples.OnlyLeadershipExercise))
      result mustBe expectedResult
    }
  }

  "findAssessmentScoresWithCandidateSummaryByEventId" should {
    "throw EventNotFoundException when event cannot be found" in
      new FindAssessmentScoresWithCandidateSummaryTestFixture {
        when(eventsRepositoryMock.getEvent(eventId)).thenReturn(Future.failed(new EventNotFoundException(s"No event found with id $eventId")))

        val ex = intercept[Exception] {
          service.findAssessmentScoresWithCandidateSummaryByEventId(UniqueIdentifier(eventId)).futureValue
        }
        ex.getCause mustBe (EventNotFoundException(s"No event found with id $eventId"))
      }

    "return List Assessment Scores find response with empty assessment scores if there is not any" in
      new FindAssessmentScoresWithCandidateSummaryTestFixture {
        when(assessmentScoresRepositoryMock.find(appId)).thenReturn(Future.successful(None))
        when(candidateAllocationRepositoryMock.activeAllocationsForEvent(eventId)).thenReturn(Future.successful(candidateAllocations))

        val result = service.findAssessmentScoresWithCandidateSummaryByEventId(UniqueIdentifier(eventId)).futureValue

        val expectedCandidate = RecordCandidateScores(
          appId,
          PersonalDetailsExamples.completed.firstName,
          PersonalDetailsExamples.completed.lastName,
          EventExamples.e1WithSession.venue.description,
          today,
          UniqueIdentifier(EventExamples.e1WithSession.sessions.head.id)
        )
        val expectedResult = List(AssessmentScoresFindResponse(expectedCandidate, None))
        result mustBe expectedResult
      }

    "return Assessment Scores response with assessment scores if there are assessment scores" in
      new FindAssessmentScoresWithCandidateSummaryTestFixture {
        when(assessmentScoresRepositoryMock.find(appId)).thenReturn(
          Future.successful(Some(AssessmentScoresAllExercisesExamples.OnlyLeadershipExercise)))
        when(candidateAllocationRepositoryMock.activeAllocationsForEvent(eventId)).thenReturn(Future.successful(candidateAllocations))

        val result = service.findAssessmentScoresWithCandidateSummaryByEventId(UniqueIdentifier(eventId)).futureValue

        val expectedCandidate = RecordCandidateScores(
          appId,
          PersonalDetailsExamples.completed.firstName,
          PersonalDetailsExamples.completed.lastName,
          EventExamples.e1WithSession.venue.description,
          today,
          UniqueIdentifier(EventExamples.e1WithSession.sessions.head.id)
        )
        val expectedResult = List(AssessmentScoresFindResponse(expectedCandidate,
          Some(AssessmentScoresAllExercisesExamples.OnlyLeadershipExercise)))
        result mustBe expectedResult
      }
  }

  trait BaseTestFixture {
    val applicationRepositoyMock = mock[GeneralApplicationRepository]
    val assessmentScoresRepositoryMock = mock[AssessmentScoresRepository]
    val candidateAllocationRepositoryMock = mock[CandidateAllocationMongoRepository]
    val eventsRepositoryMock = mock[EventsRepository]
    val personalDetailsRepositoryMock = mock[PersonalDetailsRepository]

    val dataTimeFactoryMock = mock[DateTimeFactory]

    val service = new AssessmentScoresService {
      override val applicationRepository: GeneralApplicationRepository = applicationRepositoyMock
      override val assessmentScoresRepository = assessmentScoresRepositoryMock
      override val candidateAllocationRepository = candidateAllocationRepositoryMock
      override val eventsRepository = eventsRepositoryMock
      override val personalDetailsRepository = personalDetailsRepositoryMock

      override val dateTimeFactory = dataTimeFactoryMock
    }

    val appId = AssessmentScoresAllExercisesExamples.OnlyLeadershipExercise.applicationId
    val now = DateTimeFactory.nowLocalTimeZone.withZone(DateTimeZone.UTC)
    when(dataTimeFactoryMock.nowLocalTimeZone).thenReturn(now)
    val today = DateTimeFactory.nowLocalDate
    when(dataTimeFactoryMock.nowLocalDate).thenReturn(today)
  }

  trait SaveTestFixture extends BaseTestFixture

  trait SaveExerciseTestFixture extends BaseTestFixture {
    when(assessmentScoresRepositoryMock.find(eqTo(appId))).thenReturn(
      Future.successful(Some(AssessmentScoresAllExercisesExamples.OnlyLeadershipExercise)))
  }

  trait FindAssessmentScoresWithCandidateSummaryTestFixture extends BaseTestFixture {
    val eventId = EventExamples.e1WithSession.id
    val candidateAllocations = List(CandidateAllocation(
      appId.toString(),
      eventId,
      EventExamples.e1WithSession.sessions.head.id,
      AllocationStatuses.CONFIRMED,
      "version1",
      None))
    when(eventsRepositoryMock.getEvent(eventId)).thenReturn(Future.successful(EventExamples.e1WithSession))
    when(candidateAllocationRepositoryMock.find(appId.toString())).thenReturn(Future.successful(candidateAllocations))
    when(personalDetailsRepositoryMock.find(appId.toString())).thenReturn(Future.successful(PersonalDetailsExamples.completed))
    when(eventsRepositoryMock.getEvent(eventId)).thenReturn(Future.successful(EventExamples.e1WithSession))
  }
}
