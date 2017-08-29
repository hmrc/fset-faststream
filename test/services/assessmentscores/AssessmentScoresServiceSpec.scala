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
import model.command.AssessmentScoresCommands.{ AssessmentScoresSectionType, AssessmentScoresCandidateSummary, AssessmentScoresFindResponse }
import model.command.PersonalDetailsExamples
import model.fsacscores.AssessmentScoresFinalFeedbackExamples
import model.persisted.{ CandidateAllocation, EventExamples }
import org.joda.time.{ DateTimeZone, LocalDate }
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

class AssessorAssessmentScoresServiceSpec extends AssessmentScoresServiceSpec {
  type S = AssessorAssessmentScoresService

  override def buildService(applicationRepo: GeneralApplicationRepository, assessmentScoresRepo: AssessmentScoresRepository,
    candidateAllocationRepo: CandidateAllocationMongoRepository, eventsRepo: EventsRepository,
    personalDetailsRepo: PersonalDetailsRepository, dateTimeFact: DateTimeFactory): S = {
    new AssessorAssessmentScoresService {
      override val applicationRepository = applicationRepo
      override val assessmentScoresRepository = assessmentScoresRepo
      override val candidateAllocationRepository = candidateAllocationRepo
      override val eventsRepository = eventsRepo
      override val personalDetailsRepository = personalDetailsRepo
      override val dateTimeFactory = dateTimeFact
    }
  }

  override val statusToUpdateTheApplicationTo = ProgressStatuses.ASSESSMENT_CENTRE_SCORES_ENTERED
}

class ReviewerAssessmentScoresServiceSpec extends AssessmentScoresServiceSpec {
  type S = ReviewerAssessmentScoresService

  override def buildService(applicationRepo: GeneralApplicationRepository, assessmentScoresRepo: AssessmentScoresRepository,
    candidateAllocationRepo: CandidateAllocationMongoRepository, eventsRepo: EventsRepository,
    personalDetailsRepo: PersonalDetailsRepository, dateTimeFact: DateTimeFactory): S = {
    new ReviewerAssessmentScoresService {
      override val applicationRepository = applicationRepo
      override val assessmentScoresRepository = assessmentScoresRepo
      override val candidateAllocationRepository = candidateAllocationRepo
      override val eventsRepository = eventsRepo
      override val personalDetailsRepository = personalDetailsRepo
      override val dateTimeFactory = dateTimeFact
    }
  }

  override val statusToUpdateTheApplicationTo = ProgressStatuses.ASSESSMENT_CENTRE_SCORES_ACCEPTED

}

trait AssessmentScoresServiceSpec extends BaseServiceSpec {
  type S <: AssessmentScoresService

  def buildService(applicationRepo: GeneralApplicationRepository, assessmentScoresRepo: AssessmentScoresRepository,
    candidateAllocationRepo: CandidateAllocationMongoRepository, eventsRepo: EventsRepository,
    personalDetailsRepo: PersonalDetailsRepository, dateTimeFact: DateTimeFactory): S

  val statusToUpdateTheApplicationTo: ProgressStatuses.ProgressStatus

  "save" should {
    "save assessment scores with updated submitted date and update status" in new SaveTestFixture {
      val UpdatedExample = AssessmentScoresAllExercisesExamples.AllExercises.copy(
        analysisExercise = AssessmentScoresAllExercisesExamples.AllExercises.analysisExercise.map(_.copy(submittedDate = Some(now))),
        groupExercise = AssessmentScoresAllExercisesExamples.AllExercises.groupExercise.map(_.copy(submittedDate = Some(now))),
        leadershipExercise = AssessmentScoresAllExercisesExamples.AllExercises.leadershipExercise.map(_.copy(submittedDate = Some(now))),
        finalFeedback = AssessmentScoresAllExercisesExamples.AllExercises.finalFeedback.map(_.copy(acceptedDate = now))
      )
      val AppId = UpdatedExample.applicationId

      when(assessmentScoresRepositoryMock.save(eqTo(UpdatedExample))).thenReturn(Future.successful(()))
      when(applicationRepositoyMock.addProgressStatusAndUpdateAppStatus(
        AppId.toString(), statusToUpdateTheApplicationTo)).thenReturn(Future.successful(()))

      val service = buildService(applicationRepositoyMock, assessmentScoresRepositoryMock, candidateAllocationRepositoryMock,
        eventsRepositoryMock, personalDetailsRepositoryMock, dataTimeFactoryMock)
      val result = service.save(AssessmentScoresAllExercisesExamples.AllExercises).futureValue

      verify(assessmentScoresRepositoryMock).save(eqTo(UpdatedExample))
      verify(applicationRepositoyMock).addProgressStatusAndUpdateAppStatus(
        AppId.toString(), statusToUpdateTheApplicationTo)
    }
  }

  "submitExercise" should {
    "update analysis exercise scores " +
      "when assessment scores exist and we specify we want to update analysis exercise scores" in new SaveExerciseTestFixture {
      val applicationId = AssessmentScoresAllExercisesExamples.AssessorOnlyLeadershipExercise.applicationId
      val UpdatedExercise = AssessmentScoresExerciseExamples.Example4.copy(submittedDate = Some(now))
      when(assessmentScoresRepositoryMock.saveExercise(eqTo(applicationId),
        eqTo(AssessmentScoresSectionType.analysisExercise), eqTo(UpdatedExercise), any())).thenReturn(Future.successful(()))

      val service = buildService(applicationRepositoyMock, assessmentScoresRepositoryMock, candidateAllocationRepositoryMock,
        eventsRepositoryMock, personalDetailsRepositoryMock, dataTimeFactoryMock)
      service.submitExercise(appId, AssessmentScoresSectionType.analysisExercise, AssessmentScoresExerciseExamples.Example4).futureValue

      verify(assessmentScoresRepositoryMock).saveExercise(eqTo(applicationId),
        eqTo(AssessmentScoresSectionType.analysisExercise), eqTo(UpdatedExercise), any())
      verify(applicationRepositoyMock, times(0)).addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])
    }

    "update group exercise scores " +
      "when assessment scores exist and we specify we want to update group exercise scores" in new SaveExerciseTestFixture {
      val applicationId = AssessmentScoresAllExercisesExamples.AssessorOnlyLeadershipExercise.applicationId
      val UpdatedExercise = AssessmentScoresExerciseExamples.Example4.copy(submittedDate = Some(now))
      when(assessmentScoresRepositoryMock.saveExercise(eqTo(applicationId),
        eqTo(AssessmentScoresSectionType.groupExercise), eqTo(UpdatedExercise), any())).thenReturn(Future.successful(()))

      val service = buildService(applicationRepositoyMock, assessmentScoresRepositoryMock, candidateAllocationRepositoryMock,
        eventsRepositoryMock, personalDetailsRepositoryMock, dataTimeFactoryMock)
      service.submitExercise(appId, AssessmentScoresSectionType.groupExercise, AssessmentScoresExerciseExamples.Example4).futureValue

      verify(assessmentScoresRepositoryMock).saveExercise(eqTo(applicationId),
        eqTo(AssessmentScoresSectionType.groupExercise), eqTo(UpdatedExercise), any())
      verify(applicationRepositoyMock, times(0)).addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])
    }

    "update leadership exercise scores " +
      "when assessment scores exist and we specify we want to update leadership exercise scores" in new SaveExerciseTestFixture {
      val AppId = AssessmentScoresAllExercisesExamples.AssessorOnlyAnalysisExercise.applicationId
      when(assessmentScoresRepositoryMock.find(eqTo(AppId))).thenReturn(
        Future.successful(Some(AssessmentScoresAllExercisesExamples.AssessorOnlyAnalysisExercise)))
      val UpdatedExercise = AssessmentScoresExerciseExamples.Example4.copy(submittedDate = Some(now))
      when(assessmentScoresRepositoryMock.saveExercise(eqTo(AppId),
        eqTo(AssessmentScoresSectionType.leadershipExercise), eqTo(UpdatedExercise), any())).thenReturn(Future.successful(()))

      val service = buildService(applicationRepositoyMock, assessmentScoresRepositoryMock, candidateAllocationRepositoryMock,
        eventsRepositoryMock, personalDetailsRepositoryMock, dataTimeFactoryMock)
      service.submitExercise(AppId, AssessmentScoresSectionType.leadershipExercise, AssessmentScoresExerciseExamples.Example4).futureValue

      verify(assessmentScoresRepositoryMock).saveExercise(eqTo(AppId),
        eqTo(AssessmentScoresSectionType.leadershipExercise), eqTo(UpdatedExercise), any())
      verify(applicationRepositoyMock, times(0)).addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])
    }

    "update analysis exercise scores and set application status and progress status to ASSESSMENT_CENTRE_SCORES_ENTERED " +
      "when assessment scores for group and analysis exercise exist and we specify we want to update analysis exercise scores" in
      new SaveExerciseTestFixture {
        val AppId = AssessmentScoresAllExercisesExamples.AssessorAllButAnalysisExercise.applicationId
        when(assessmentScoresRepositoryMock.find(eqTo(AppId))).thenReturn(
          Future.successful(Some(AssessmentScoresAllExercisesExamples.AssessorAllButAnalysisExercise)))
        val UpdatedExercise = AssessmentScoresExerciseExamples.Example4.copy(submittedDate = Some(now))
        when(assessmentScoresRepositoryMock.saveExercise(eqTo(AppId),
          eqTo(AssessmentScoresSectionType.analysisExercise), eqTo(UpdatedExercise), any())).thenReturn(Future.successful(()))
        when(applicationRepositoyMock.addProgressStatusAndUpdateAppStatus(
          AppId.toString(), statusToUpdateTheApplicationTo)).thenReturn(Future.successful(()))

        val service = buildService(applicationRepositoyMock, assessmentScoresRepositoryMock, candidateAllocationRepositoryMock,
          eventsRepositoryMock, personalDetailsRepositoryMock, dataTimeFactoryMock)
        service.submitExercise(AppId, AssessmentScoresSectionType.analysisExercise, AssessmentScoresExerciseExamples.Example4).futureValue

        verify(assessmentScoresRepositoryMock).saveExercise(eqTo(AppId),
          eqTo(AssessmentScoresSectionType.analysisExercise), eqTo(UpdatedExercise), any())
        verify(applicationRepositoyMock).addProgressStatusAndUpdateAppStatus(AppId.toString(), statusToUpdateTheApplicationTo)
      }


    "create assessment scores with analysis exercise scores " +
      "when assessment scores does not exist and we pass analysis exercise scores" in new SaveExerciseTestFixture {
      when(assessmentScoresRepositoryMock.find(eqTo(appId))).thenReturn(Future.successful(None))
      val expectedAssessmentScores = AssessmentScoresAllExercises(appId,
        Some(AssessmentScoresExerciseExamples.Example4.copy(submittedDate = Some(now))), None, None)
      val ExpectedExercise = AssessmentScoresExerciseExamples.Example4.copy(submittedDate = Some(now))
      when(assessmentScoresRepositoryMock.saveExercise(eqTo(appId),
        eqTo(AssessmentScoresSectionType.analysisExercise), eqTo(ExpectedExercise), any())).thenReturn(Future.successful(()))

      val service = buildService(applicationRepositoyMock, assessmentScoresRepositoryMock, candidateAllocationRepositoryMock,
        eventsRepositoryMock, personalDetailsRepositoryMock, dataTimeFactoryMock)
      service.submitExercise(appId, AssessmentScoresSectionType.analysisExercise,
        AssessmentScoresExerciseExamples.Example4).futureValue

      verify(assessmentScoresRepositoryMock).saveExercise(eqTo(appId), eqTo(AssessmentScoresSectionType.analysisExercise),
        eqTo(ExpectedExercise), any())

    }
  }

  "saveExercise" should {
    "update analysis exercise scores and saved date instead of submittedDate" +
      "when assessment scores exist and we specify we want to update analysis exercise scores" in new SaveExerciseTestFixture {
      val applicationId = AssessmentScoresAllExercisesExamples.AssessorOnlyLeadershipExercise.applicationId
      val UpdatedExercise = AssessmentScoresExerciseExamples.Example4.copy(savedDate = Some(now))
      when(assessmentScoresRepositoryMock.saveExercise(eqTo(applicationId),
        eqTo(AssessmentScoresSectionType.analysisExercise), eqTo(UpdatedExercise), any())).thenReturn(Future.successful(()))

      val service = buildService(applicationRepositoyMock, assessmentScoresRepositoryMock, candidateAllocationRepositoryMock,
        eventsRepositoryMock, personalDetailsRepositoryMock, dataTimeFactoryMock)
      service.saveExercise(appId, AssessmentScoresSectionType.analysisExercise, AssessmentScoresExerciseExamples.Example4).futureValue

      verify(assessmentScoresRepositoryMock).saveExercise(eqTo(applicationId),
        eqTo(AssessmentScoresSectionType.analysisExercise), eqTo(UpdatedExercise), any())
      verify(applicationRepositoyMock, times(0)).addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])
    }
  }

  "submitFinalFeedback" should {
    "throw IllegalArgument Exception " +
      "when assessment scores exists but not all have been set" in new SaveExerciseTestFixture {
      val AppId = AssessmentScoresAllExercisesExamples.AssessorOnlyAnalysisExercise.applicationId
      when(assessmentScoresRepositoryMock.find(eqTo(AppId))).thenReturn(
        Future.successful(Some(AssessmentScoresAllExercisesExamples.AssessorOnlyAnalysisExercise)))
      val UpdatedExample = AssessmentScoresAllExercisesExamples.AssessorOnlyAnalysisExercise.copy(
        finalFeedback = Some(AssessmentScoresFinalFeedbackExamples.Example2.copy(acceptedDate = now)))
      val UpdatedExercise = AssessmentScoresExerciseExamples.Example4.copy(savedDate = Some(now))
      when(assessmentScoresRepositoryMock.saveExercise(eqTo(UpdatedExample.applicationId),
        eqTo(AssessmentScoresSectionType.analysisExercise), eqTo(UpdatedExercise), any())).thenReturn(Future.successful(()))

      val service = buildService(applicationRepositoyMock, assessmentScoresRepositoryMock, candidateAllocationRepositoryMock,
        eventsRepositoryMock, personalDetailsRepositoryMock, dataTimeFactoryMock)
      try {
        service.submitFinalFeedback(AppId, AssessmentScoresFinalFeedbackExamples.Example2).failed.futureValue
      } catch {
        case ex: Exception => ex.getClass mustBe classOf[IllegalArgumentException]
      }
    }

    "update final feedback and set application status and progress status to ASSESSMENT_CENTRE_SCORES_ACCEPTED " +
      "when all assessment exercises scores exist" in
      new SaveExerciseTestFixture {
        val AppId = AssessmentScoresAllExercisesExamples.AllExercisesButFinalFeedback.applicationId
        when(assessmentScoresRepositoryMock.find(eqTo(AppId))).thenReturn(
          Future.successful(Some(AssessmentScoresAllExercisesExamples.AllExercisesButFinalFeedback)))

        val UpdatedExample = AssessmentScoresAllExercisesExamples.AllExercisesButFinalFeedback.copy(
          finalFeedback = Some(AssessmentScoresFinalFeedbackExamples.Example1.copy(acceptedDate = now)),
          analysisExercise = AssessmentScoresAllExercisesExamples.AllExercisesButFinalFeedback.analysisExercise.map(
            _.copy(submittedDate = Some(now))),
          groupExercise = AssessmentScoresAllExercisesExamples.AllExercisesButFinalFeedback.groupExercise.map(
            _.copy(submittedDate = Some(now))),
          leadershipExercise = AssessmentScoresAllExercisesExamples.AllExercisesButFinalFeedback.leadershipExercise.map(
            _.copy(submittedDate = Some(now)))
        )
        when(assessmentScoresRepositoryMock.save(eqTo(UpdatedExample))).thenReturn(Future.successful(()))
        when(applicationRepositoyMock.addProgressStatusAndUpdateAppStatus(
          AppId.toString(), statusToUpdateTheApplicationTo)).thenReturn(Future.successful(()))

        val service = buildService(applicationRepositoyMock, assessmentScoresRepositoryMock, candidateAllocationRepositoryMock,
          eventsRepositoryMock, personalDetailsRepositoryMock, dataTimeFactoryMock)
        service.submitFinalFeedback(AppId, AssessmentScoresFinalFeedbackExamples.Example1).futureValue

        verify(assessmentScoresRepositoryMock).save(eqTo(UpdatedExample))
        verify(applicationRepositoyMock).addProgressStatusAndUpdateAppStatus(AppId.toString(), statusToUpdateTheApplicationTo)
      }


    "throw IllegalArgument Exception " +
      "when assessment scores do not exist" in new SaveExerciseTestFixture {
      when(assessmentScoresRepositoryMock.find(eqTo(appId))).thenReturn(Future.successful(None))
      val service = buildService(applicationRepositoyMock, assessmentScoresRepositoryMock, candidateAllocationRepositoryMock,
        eventsRepositoryMock, personalDetailsRepositoryMock, dataTimeFactoryMock)

      try {
        service.submitFinalFeedback(appId, AssessmentScoresFinalFeedbackExamples.Example1).failed.futureValue
      } catch {
        case ex: Exception => ex.getClass mustBe classOf[IllegalArgumentException]
      }
    }
  }


  "findAssessmentScoresWithCandidateSummaryByApplicationId" should {
    "return Assessment Scores response with empty assessment scores if there is not any" in
      new FindAssessmentScoresWithCandidateSummaryTestFixture {
        when(assessmentScoresRepositoryMock.find(appId)).thenReturn(Future.successful(None))
        val service = buildService(applicationRepositoyMock, assessmentScoresRepositoryMock, candidateAllocationRepositoryMock,
          eventsRepositoryMock, personalDetailsRepositoryMock, dataTimeFactoryMock)
        val result = service.findAssessmentScoresWithCandidateSummaryByApplicationId(appId).futureValue

        val expectedCandidate = AssessmentScoresCandidateSummary(
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
          Future.successful(Some(AssessmentScoresAllExercisesExamples.AssessorOnlyLeadershipExercise)))
        val service = buildService(applicationRepositoyMock, assessmentScoresRepositoryMock, candidateAllocationRepositoryMock,
          eventsRepositoryMock, personalDetailsRepositoryMock, dataTimeFactoryMock)
        val result = service.findAssessmentScoresWithCandidateSummaryByApplicationId(appId).futureValue

        val expectedCandidate = AssessmentScoresCandidateSummary(
          appId,
          PersonalDetailsExamples.completed.firstName,
          PersonalDetailsExamples.completed.lastName,
          EventExamples.e1WithSession.venue.description,
          today,
          UniqueIdentifier(EventExamples.e1WithSession.sessions.head.id)
        )
        val expectedResult = AssessmentScoresFindResponse(expectedCandidate,
          Some(AssessmentScoresAllExercisesExamples.AssessorOnlyLeadershipExercise))
        result mustBe expectedResult
      }
  }

  "findAssessmentScoresWithCandidateSummaryByEventId" should {
    "throw EventNotFoundException when event cannot be found" in
      new FindAssessmentScoresWithCandidateSummaryTestFixture {
        when(eventsRepositoryMock.getEvent(eventId)).thenReturn(Future.failed(new EventNotFoundException(s"No event found with id $eventId")))

        val ex = intercept[Exception] {
          val service = buildService(applicationRepositoyMock, assessmentScoresRepositoryMock, candidateAllocationRepositoryMock,
            eventsRepositoryMock, personalDetailsRepositoryMock, dataTimeFactoryMock)
          service.findAssessmentScoresWithCandidateSummaryByEventId(UniqueIdentifier(eventId)).futureValue
        }
        ex.getCause mustBe (EventNotFoundException(s"No event found with id $eventId"))
      }

    "return List Assessment Scores find response with empty assessment scores if there is not any" in
      new FindAssessmentScoresWithCandidateSummaryTestFixture {
        when(assessmentScoresRepositoryMock.find(appId)).thenReturn(Future.successful(None))
        when(candidateAllocationRepositoryMock.activeAllocationsForEvent(eventId)).thenReturn(Future.successful(candidateAllocations))
        val service = buildService(applicationRepositoyMock, assessmentScoresRepositoryMock, candidateAllocationRepositoryMock,
          eventsRepositoryMock, personalDetailsRepositoryMock, dataTimeFactoryMock)
        val result = service.findAssessmentScoresWithCandidateSummaryByEventId(UniqueIdentifier(eventId)).futureValue

        val expectedCandidate = AssessmentScoresCandidateSummary(
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
          Future.successful(Some(AssessmentScoresAllExercisesExamples.AssessorOnlyLeadershipExercise)))
        when(candidateAllocationRepositoryMock.activeAllocationsForEvent(eventId)).thenReturn(Future.successful(candidateAllocations))
        val service = buildService(applicationRepositoyMock, assessmentScoresRepositoryMock, candidateAllocationRepositoryMock,
          eventsRepositoryMock, personalDetailsRepositoryMock, dataTimeFactoryMock)
        val result = service.findAssessmentScoresWithCandidateSummaryByEventId(UniqueIdentifier(eventId)).futureValue

        val expectedCandidate = AssessmentScoresCandidateSummary(
          appId,
          PersonalDetailsExamples.completed.firstName,
          PersonalDetailsExamples.completed.lastName,
          EventExamples.e1WithSession.venue.description,
          today,
          UniqueIdentifier(EventExamples.e1WithSession.sessions.head.id)
        )
        val expectedResult = List(AssessmentScoresFindResponse(expectedCandidate,
          Some(AssessmentScoresAllExercisesExamples.AssessorOnlyLeadershipExercise)))
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

    val appId = AssessmentScoresAllExercisesExamples.AssessorOnlyLeadershipExercise.applicationId
    val now = DateTimeFactory.nowLocalTimeZone.withZone(DateTimeZone.UTC)
    when(dataTimeFactoryMock.nowLocalTimeZone).thenReturn(now)
    val today = DateTimeFactory.nowLocalDate
    when(dataTimeFactoryMock.nowLocalDate).thenReturn(today)
  }

  trait SaveTestFixture extends BaseTestFixture

  trait SaveExerciseTestFixture extends BaseTestFixture {
    when(assessmentScoresRepositoryMock.find(eqTo(appId))).thenReturn(
      Future.successful(Some(AssessmentScoresAllExercisesExamples.AssessorOnlyLeadershipExercise)))
  }

  trait FindAssessmentScoresWithCandidateSummaryTestFixture extends BaseTestFixture {
    val eventId = EventExamples.e1WithSession.id
    val candidateAllocations = List(CandidateAllocation(
      appId.toString(),
      eventId,
      EventExamples.e1WithSession.sessions.head.id,
      AllocationStatuses.CONFIRMED,
      "version1",
      None,
      LocalDate.now(),
      false
    ))
    when(eventsRepositoryMock.getEvent(eventId)).thenReturn(Future.successful(EventExamples.e1WithSession))
    when(candidateAllocationRepositoryMock.find(appId.toString())).thenReturn(Future.successful(candidateAllocations))
    when(personalDetailsRepositoryMock.find(appId.toString())).thenReturn(Future.successful(PersonalDetailsExamples.completed))
    when(eventsRepositoryMock.getEvent(eventId)).thenReturn(Future.successful(EventExamples.e1WithSession))
  }
}
