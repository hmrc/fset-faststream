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

package model.testdata.candidate

import model.ProgressStatuses.ProgressStatus
import model.command.testdata.CreateCandidateRequest.{CreateCandidateRequest, PhaseXTestDataRequest}
import model.persisted.{PassmarkEvaluation, SchemeEvaluationResult}
import model.{ProgressStatuses, SchemeId}

import java.time.OffsetDateTime

abstract class PhaseXTestData(
  start: Option[OffsetDateTime] = None,
  expiry: Option[OffsetDateTime] = None,
  completion: Option[OffsetDateTime] = None,
  scores: List[Double] = Nil,
  passmarkEvaluation: Option[PassmarkEvaluation] = None) extends TestDates {
}

case class Phase1TestData(
  start: Option[OffsetDateTime] = None,
  expiry: Option[OffsetDateTime] = None,
  completion: Option[OffsetDateTime] = None,
  scores: List[Double] = Nil,
  passmarkEvaluation: Option[PassmarkEvaluation] = None
) extends PhaseXTestData(start, expiry, completion, scores, passmarkEvaluation)

case class Phase2TestData(
  start: Option[OffsetDateTime] = None,
  expiry: Option[OffsetDateTime] = None,
  completion: Option[OffsetDateTime] = None,
  scores: List[Double] = Nil,
  passmarkEvaluation: Option[PassmarkEvaluation] = None
) extends PhaseXTestData(start, expiry, completion, scores, passmarkEvaluation)

case class Phase3TestData(
  start: Option[OffsetDateTime] = None,
  expiry: Option[OffsetDateTime] = None,
  completion: Option[OffsetDateTime] = None,
  scores: List[Double] = Nil,
  generateNullScoresForFewQuestions: Option[Boolean] = None,
  receivedBeforeInHours: Option[Int] = None,
  passmarkEvaluation: Option[PassmarkEvaluation] = None
) extends PhaseXTestData(start, expiry, completion, scores, passmarkEvaluation)


abstract class PhaseXTestDataFactory {

  case class PhaseXTestDataConfig(
    invitedStatus: ProgressStatus,
    startedStatus: ProgressStatus,
    completedStatus: ProgressStatus,
    resultsReceived: ProgressStatus)

  case class PhaseXTestDataComponents(
    start: Option[OffsetDateTime],
    expiry: Option[OffsetDateTime],
    completion: Option[OffsetDateTime],
    scores: List[Double],
    passmarkEvaluation: Option[PassmarkEvaluation])

  def getConfig(): PhaseXTestDataConfig

  def build(candidateRequest: CreateCandidateRequest, phaseXTestDataRequest: Option[PhaseXTestDataRequest], schemeTypes: List[SchemeId]) = {

    val progressStatusMaybe = candidateRequest.statusData.progressStatus.map(ProgressStatuses.nameToProgressStatus)
    progressStatusMaybe.flatMap { progressStatus =>
      if (ProgressStatuses.ProgressStatusOrder.isEqualOrAfter(progressStatus,
        getConfig().invitedStatus).getOrElse(false)) {
        Some(PhaseXTestDataComponents(
          start = getStart(phaseXTestDataRequest, progressStatus),
          expiry = getExpiry(phaseXTestDataRequest, progressStatus),
          completion = getCompletion(phaseXTestDataRequest, progressStatus),
          scores = getScores(phaseXTestDataRequest, candidateRequest, progressStatus),
          passmarkEvaluation = getPassmarkEvaluation(phaseXTestDataRequest, schemeTypes, progressStatus)
        ))
      } else {
        None
      }
    }
  }

  private def getStart(testDataRequest: Option[PhaseXTestDataRequest], progressStatus: ProgressStatus) = {
    if (ProgressStatuses.ProgressStatusOrder.isEqualOrAfter(progressStatus,
      getConfig().startedStatus).getOrElse(false)) {
      testDataRequest.flatMap(_.start.map(OffsetDateTime.parse)) //.getOrElse(DateTime.now()))
    } else {
      None
    }
  }

  private def getExpiry(testDataRequest: Option[PhaseXTestDataRequest], progressStatus: ProgressStatus) = {
    if (ProgressStatuses.ProgressStatusOrder.isEqualOrAfter(progressStatus,
      getConfig().invitedStatus).getOrElse(false)) {
      testDataRequest.map(_.expiry.map(OffsetDateTime.parse).getOrElse(OffsetDateTime.now.plusDays(7)))
    } else {
      None
    }
  }

  private def getCompletion(testDataRequest: Option[PhaseXTestDataRequest], progressStatus: ProgressStatus) = {
    if (ProgressStatuses.ProgressStatusOrder.isEqualOrAfter(progressStatus,
      getConfig().completedStatus).getOrElse(false)) {
      testDataRequest.flatMap(_.completion.map(OffsetDateTime.parse)) //.getOrElse(DateTime.now()))
    } else {
      None
    }
  }

  def getScores(testDataRequest: Option[PhaseXTestDataRequest], candidateRequest: CreateCandidateRequest,
    progressStatus: ProgressStatus): List[Double]

  private def getPassmarkEvaluation(testDataRequest: Option[PhaseXTestDataRequest], schemeTypes: List[SchemeId],
    progressStatus: ProgressStatus) = {
    if (ProgressStatuses.ProgressStatusOrder.isEqualOrAfter(progressStatus,
      getConfig().resultsReceived).getOrElse(false)) {
      testDataRequest.flatMap(_.passmarkEvaluation).orElse(
        Some(PassmarkEvaluation(passmarkVersion = "2", previousPhasePassMarkVersion = Some("1"),
          schemeTypes.map { schemeId => SchemeEvaluationResult(schemeId.value, "Green") },
          resultVersion = "2", previousPhaseResultVersion = Some("1")
        )))
    } else {
      None
    }
  }
}

case class Phase1TestDataFactory[T <: PhaseXTestData]() extends PhaseXTestDataFactory {
  override def getConfig() = PhaseXTestDataConfig(ProgressStatuses.PHASE1_TESTS_INVITED, ProgressStatuses.PHASE1_TESTS_STARTED,
    ProgressStatuses.PHASE1_TESTS_COMPLETED, ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED)

  def build(candidateRequest: CreateCandidateRequest, schemeTypes: List[SchemeId]): Option[Phase1TestData] = {
    val componentsMaybe = build(candidateRequest, candidateRequest.phase1TestData, schemeTypes)
    componentsMaybe.map(components =>
      Phase1TestData(
        start = components.start,
        expiry = components.expiry,
        completion = components.completion,
        scores = components.scores,
        passmarkEvaluation = components.passmarkEvaluation
      )
    )
  }

  override def getScores(testDataRequest: Option[PhaseXTestDataRequest], candidateRequest: CreateCandidateRequest,
    progressStatus: ProgressStatus) = {
    if (ProgressStatuses.ProgressStatusOrder.isEqualOrAfter(progressStatus,
      getConfig().completedStatus).getOrElse(false)) {
      testDataRequest.map(_.scores.map(_.toDouble)).getOrElse(
        if (candidateRequest.assistanceDetails.flatMap(_.setGis).getOrElse(false)) {
          List(20.0, 21.00)
        } else {
          List(20.00, 21.00, 22.00, 23.00)
        }
      )
    } else {
      Nil
    }
  }
}

object Phase1TestData extends Phase1TestDataFactory {
  def apply(candidateRequest: CreateCandidateRequest, schemeTypes: List[SchemeId]): Option[Phase1TestData] = {
    Phase1TestDataFactory().build(candidateRequest, schemeTypes)
  }
}

case class Phase2TestDataFactory() extends PhaseXTestDataFactory {
  override def getConfig() = PhaseXTestDataConfig(ProgressStatuses.PHASE2_TESTS_INVITED, ProgressStatuses.PHASE2_TESTS_STARTED,
    ProgressStatuses.PHASE2_TESTS_COMPLETED, ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED)

  def build(candidateRequest: CreateCandidateRequest, schemeTypes: List[SchemeId]): Option[Phase2TestData] = {
    val componentsMaybe = build(candidateRequest, candidateRequest.phase2TestData, schemeTypes)
    componentsMaybe.map(components =>
      Phase2TestData(
        start = components.start,
        expiry = components.expiry,
        completion = components.completion,
        scores = components.scores,
        passmarkEvaluation = components.passmarkEvaluation
      )
    )
  }

  override def getScores(testDataRequest: Option[PhaseXTestDataRequest], candidateRequest: CreateCandidateRequest,
    progressStatus: ProgressStatus) = {
    if (ProgressStatuses.ProgressStatusOrder.isEqualOrAfter(progressStatus,
      getConfig().completedStatus).getOrElse(false)) {
      testDataRequest.map(_.scores.map(_.toDouble)).getOrElse(
        List(20.0, 21.00)
      )
    } else {
      Nil
    }
  }
}

object Phase2TestData extends Phase2TestDataFactory {
  def apply(candidateRequest: CreateCandidateRequest, schemeTypes: List[SchemeId]): Option[Phase2TestData] = {
    Phase2TestDataFactory().build(candidateRequest, schemeTypes)
  }
}

case class Phase3TestDataFactory() extends PhaseXTestDataFactory {
  override def getConfig() = PhaseXTestDataConfig(ProgressStatuses.PHASE3_TESTS_INVITED, ProgressStatuses.PHASE3_TESTS_STARTED,
    ProgressStatuses.PHASE3_TESTS_COMPLETED, ProgressStatuses.PHASE3_TESTS_RESULTS_RECEIVED)

  def build(candidateRequest: CreateCandidateRequest, schemeTypes: List[SchemeId]): Option[Phase3TestData] = {
    val componentsMaybe = build(candidateRequest, candidateRequest.phase3TestData, schemeTypes)
    componentsMaybe.map(components =>
      Phase3TestData(
        start = components.start,
        expiry = components.expiry,
        completion = components.completion,
        scores = components.scores,
        generateNullScoresForFewQuestions = candidateRequest.phase3TestData.flatMap(_.generateNullScoresForFewQuestions),
        receivedBeforeInHours = candidateRequest.phase3TestData.flatMap(_.receivedBeforeInHours),
        passmarkEvaluation = components.passmarkEvaluation
      )
    )
  }

  override def getScores(testDataRequest: Option[PhaseXTestDataRequest], candidateRequest: CreateCandidateRequest,
    progressStatus: ProgressStatus) = {
    if (ProgressStatuses.ProgressStatusOrder.isEqualOrAfter(progressStatus,
      getConfig().completedStatus).getOrElse(false)) {
      testDataRequest.map(_.scores.map(_.toDouble)).getOrElse(
        List(20.0)
      )
    } else {
      Nil
    }
  }
}

object Phase3TestData extends Phase2TestDataFactory {
  def apply(candidateRequest: CreateCandidateRequest, schemeTypes: List[SchemeId]): Option[Phase3TestData] = {
    Phase3TestDataFactory().build(candidateRequest, schemeTypes)
  }
}
