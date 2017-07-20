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
import model.AllocationStatuses
import model.assessmentscores.{ AssessmentScoresAllExercises, AssessmentScoresAllExercisesExamples, AssessmentScoresExerciseExamples }
import model.command.AssessmentScoresCommands.{ AssessmentExerciseType, AssessmentScoresFindResponse, RecordCandidateScores }
import model.command.PersonalDetailsExamples
import model.persisted.{ CandidateAllocation, EventExamples }
import org.joda.time.DateTimeZone
import org.mockito.Mockito.when
import repositories.{ AssessmentScoresRepository, CandidateAllocationMongoRepository }
import repositories.events.EventsRepository
import repositories.personaldetails.PersonalDetailsRepository
import services.{ BaseServiceSpec }
import org.mockito.ArgumentMatchers.{ eq => eqTo }
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._

import scala.concurrent.Future

class AssessmentScoresServiceSpec extends BaseServiceSpec {

  "saveExercise" should {
    "update analysis exercise scores " +
      "when assessment scores exist and we specify we want to update analysis exercise scores" in new TestFixture {
      val updatedExample1 = AssessmentScoresAllExercisesExamples.Example1.copy(
        analysisExercise = Some(AssessmentScoresExerciseExamples.Example4.copy(submittedDate = Some(now))))
      when(assessmentScoresRepositoryMock.save(eqTo(updatedExample1))).thenReturn(Future.successful(()))

      service.saveExercise(appId, AssessmentExerciseType.analysisExercise,
        AssessmentScoresExerciseExamples.Example4).futureValue

      verify(assessmentScoresRepositoryMock).save(eqTo(updatedExample1))
    }

    "update group exercise scores " +
      "when assessment scores exist and we specify we want to update group exercise scores" in new TestFixture {
      val updatedExample1 = AssessmentScoresAllExercisesExamples.Example1.copy(
        groupExercise = Some(AssessmentScoresExerciseExamples.Example4.copy(submittedDate = Some(now))))
      when(assessmentScoresRepositoryMock.save(eqTo(updatedExample1))).thenReturn(Future.successful(()))

      service.saveExercise(appId, AssessmentExerciseType.groupExercise,
        AssessmentScoresExerciseExamples.Example4).futureValue

      verify(assessmentScoresRepositoryMock).save(eqTo(updatedExample1))
    }

    "update leadership exercise scores " +
      "when assessment scores exist and we specify we want to update leadership exercise scores" in new TestFixture {
      val updatedExample1 = AssessmentScoresAllExercisesExamples.Example1.copy(
        leadershipExercise = Some(AssessmentScoresExerciseExamples.Example4.copy(submittedDate = Some(now))))
      when(assessmentScoresRepositoryMock.save(eqTo(updatedExample1))).thenReturn(Future.successful(()))

      service.saveExercise(appId, AssessmentExerciseType.leadershipExercise,
        AssessmentScoresExerciseExamples.Example4).futureValue

      verify(assessmentScoresRepositoryMock).save(eqTo(updatedExample1))
    }

    "create assessment scores with analysis exercise scores " +
      "when assessment scores does not exist and we pass analysis exercise scores" in new TestFixture {
      when(assessmentScoresRepositoryMock.find(eqTo(appId))).thenReturn(Future.successful(None))
      val expectedAssessmentScores = AssessmentScoresAllExercises(appId,
        Some(AssessmentScoresExerciseExamples.Example4.copy(submittedDate = Some(now))), None, None)
      when(assessmentScoresRepositoryMock.save(eqTo(expectedAssessmentScores))).thenReturn(Future.successful(()))

      service.saveExercise(appId, AssessmentExerciseType.analysisExercise,
        AssessmentScoresExerciseExamples.Example4).futureValue

      verify(assessmentScoresRepositoryMock).save(eqTo(expectedAssessmentScores))

    }
  }


  "findAssessmentScoresWithCandidateSummary" should {
    "return Assessment Scores response with empty assessment scores if there is not any" in new TestFixture {
      val eventId = EventExamples.e1.id
      val candidateAllocations = List(CandidateAllocation(appId.toString(), eventId, "sessionId", AllocationStatuses.CONFIRMED, "version1"))
      when(candidateAllocationRepositoryMock.find(appId.toString())).thenReturn(Future.successful(candidateAllocations))
      when(personalDetailsRepositoryMock.find(appId.toString())).thenReturn(Future.successful(PersonalDetailsExamples.completed))
      when(assessmentScoresRepositoryMock.find(appId)).thenReturn(Future.successful(None))

      when(eventsRepositoryMock.getEvent(eventId)).thenReturn(Future.successful(EventExamples.e1))

      val result = service.findAssessmentScoresWithCandidateSummary(appId).futureValue

      val expectedCandidate = RecordCandidateScores(
        PersonalDetailsExamples.completed.firstName,
        PersonalDetailsExamples.completed.lastName,
        EventExamples.e1.venue.description,
        today)
      val expectedResult = AssessmentScoresFindResponse(expectedCandidate, None)
      result mustBe expectedResult
    }
  }

  "save" should {
    "" in new TestFixture {

    }
  }

  trait TestFixture {
    val assessmentScoresRepositoryMock = mock[AssessmentScoresRepository]
    val candidateAllocationRepositoryMock = mock[CandidateAllocationMongoRepository]
    val eventsRepositoryMock = mock[EventsRepository]
    val personalDetailsRepositoryMock = mock[PersonalDetailsRepository]

    val dataTimeFactoryMock = mock[DateTimeFactory]

    val service = new AssessmentScoresService {
      override val assessmentScoresRepository = assessmentScoresRepositoryMock
      override val candidateAllocationRepository = candidateAllocationRepositoryMock
      override val eventsRepository = eventsRepositoryMock
      override val personalDetailsRepository = personalDetailsRepositoryMock

      override val dateTimeFactory = dataTimeFactoryMock
    }

    val now = DateTimeFactory.nowLocalTimeZone.withZone(DateTimeZone.UTC)
    when(dataTimeFactoryMock.nowLocalTimeZone).thenReturn(now)
    val today = DateTimeFactory.nowLocalDate
    when(dataTimeFactoryMock.nowLocalDate).thenReturn(today)


    val appId = AssessmentScoresAllExercisesExamples.Example1.applicationId
    when(assessmentScoresRepositoryMock.find(eqTo(appId))).thenReturn(
      Future.successful(Some(AssessmentScoresAllExercisesExamples.Example1)))
  }

}
