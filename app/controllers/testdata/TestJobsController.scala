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

package controllers.testdata

import javax.inject.{Inject, Singleton}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import scheduler._
import scheduler.assessment.EvaluateAssessmentCentreJobImpl
import scheduler.fsb.EvaluateFsbJobImpl
import scheduler.onlinetesting._
import scheduler.sift._
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TestJobsController @Inject() (cc:ControllerComponents,
                                    sendPhase1InvitationJob: SendPhase1InvitationJob,
                                    sendPhase2InvitationJob: SendPhase2InvitationJob,
                                    sendPhase3InvitationJob: SendPhase3InvitationJob,
                                    siftNumericalTestInvitationJob: SiftNumericalTestInvitationJobImpl,
                                    expirePhase1TestJob: ExpirePhase1TestJob,
                                    expirePhase2TestJob: ExpirePhase2TestJob,
                                    expirePhase3TestJob: ExpirePhase3TestJob,
                                    evaluatePhase1ResultJob: EvaluatePhase1ResultJob,
                                    evaluatePhase2ResultJob: EvaluatePhase2ResultJob,
                                    evaluatePhase3ResultJob: EvaluatePhase3ResultJob,
                                    successPhase1TestJob: SuccessPhase1TestJob,
                                    successPhase3TestJob: SuccessPhase3TestJob,
                                    failedPhase1TestJob: FailedPhase1TestJob,
                                    successPhase3SdipFsTestJob: SuccessPhase3SdipFsTestJob,
                                    progressToSiftJob: ProgressToSiftJobImpl,
                                    firstSiftReminderJob: FirstSiftReminderJobImpl,
                                    secondSiftReminderJob: SecondSiftReminderJobImpl,
                                    processSiftNumericalResultsReceivedJob: ProcessSiftNumericalResultsReceivedJobImpl,
                                    siftExpiryJob: SiftExpiryJobImpl,
                                    siftFailureJob:  SiftFailureJob,
                                    progressToAssessmentCentreJob: ProgressToAssessmentCentreJobImpl,
                                    progressToFsbOrOfferJob: ProgressToFsbOrOfferJobImpl,
                                    evaluateAssessmentCentreJob: EvaluateAssessmentCentreJobImpl,
                                    notifyAssessorsOfNewEventsJob: NotifyAssessorsOfNewEventsJobImpl,
                                    fsbOverallFailureJob: FsbOverallFailureJob,
                                    evaluateFsbJob: EvaluateFsbJobImpl,
                                    notifyOnFinalFailureJob: NotifyOnFinalFailureJobImpl,
                                    notifyOnFinalSuccessJob: NotifyOnFinalSuccessJobImpl,
                                    skipPhasesJob: SkipPhasesJobImpl,
                                    skipPhase3Job: SkipPhase3JobImpl,
                                    fixSdipFsP3SkippedCandidatesJob: FixSdipFsP3SkippedCandidatesJobImpl
                                   ) extends BackendController(cc) {

  implicit val ec: ExecutionContext = cc.executionContext

  def testInvitationJob(phase: String): Action[AnyContent] = Action.async { implicit _ =>
    phase.toUpperCase match {
      case "PHASE1" => sendPhase1InvitationJob.tryExecute().map(_ => Ok(s"$phase test invitation job started"))
      case "PHASE2" => sendPhase2InvitationJob.tryExecute().map(_ => Ok(s"$phase test invitation job started"))
      case "PHASE3" => sendPhase3InvitationJob.tryExecute().map(_ => Ok(s"$phase test invitation job started"))
      case "SIFT"   => siftNumericalTestInvitationJob.tryExecute().map(_ => Ok(s"$phase test invitation job started"))
      case _ => Future.successful(BadRequest(s"No such phase: $phase. Options are [phase1, phase2, phase3]"))
    }
  }

  def expireOnlineTestJob(phase: String): Action[AnyContent] = Action.async { implicit _ =>
    phase.toUpperCase match {
      case "PHASE1" => expirePhase1TestJob.tryExecute().map(_ => Ok(s"$phase expiry job started"))
      case "PHASE2" => expirePhase2TestJob.tryExecute().map(_ => Ok(s"$phase expiry job started"))
      case "PHASE3" => expirePhase3TestJob.tryExecute().map(_ => Ok(s"$phase expiry job started"))
      case _ => Future.successful(BadRequest(s"No such phase: $phase. Options are [phase1, phase2, phase3]"))
    }
  }

  def evaluatePhase1OnlineTestsCandidate: Action[AnyContent] = Action.async { implicit _ =>
    evaluatePhase1ResultJob.tryExecute().map { _ =>
      Ok("Evaluate phase 1 result job started")
    }
  }

  def evaluatePhase2EtrayCandidate: Action[AnyContent] = Action.async { implicit _ =>
    evaluatePhase2ResultJob.tryExecute().map { _ =>
      Ok("Evaluate phase 2 result job started")
    }
  }

  def evaluatePhase3VideoInterviewCandidate: Action[AnyContent] = Action.async { implicit _ =>
    evaluatePhase3ResultJob.tryExecute().map { _ =>
      Ok("Evaluate phase 3 result job started")
    }
  }

  def processSkipPhasesJob: Action[AnyContent] = Action.async { implicit _ =>
    skipPhasesJob.tryExecute().map { _ =>
      Ok("Skip phases job started")
    }
  }

  def processSkipPhase3Job: Action[AnyContent] = Action.async { implicit _ =>
    skipPhase3Job.tryExecute().map { _ =>
      Ok("Skip phase 3 job started")
    }
  }

  def processFixSdipFsP3SkippedCandidatesJob: Action[AnyContent] = Action.async { implicit _ =>
    fixSdipFsP3SkippedCandidatesJob.tryExecute().map { _ =>
      Ok("Fix SdipFaststream P3 skipped candidates job started")
    }
  }

  def processSuccessPhase1TestJob: Action[AnyContent] = Action.async { implicit _ =>
    successPhase1TestJob.tryExecute().map { _ =>
      Ok("Success phase 1 test job started")
    }
  }

  def processFailedPhase1TestJob: Action[AnyContent] = Action.async { implicit _ =>
    failedPhase1TestJob.tryExecute().map { _ =>
      Ok("Failed phase 1 test job started")
    }
  }

  def processSuccessPhase3TestJob: Action[AnyContent] = Action.async { implicit _ =>
    successPhase3TestJob.tryExecute().map { _ =>
      Ok("Success phase 3 test job started")
    }
  }

  def processSuccessPhase3SdipTestJob: Action[AnyContent] = Action.async { implicit _ =>
    successPhase3SdipFsTestJob.tryExecute().map { _ =>
      Ok("Success phase 3 sdip test job started")
    }
  }

  def progressCandidatesToSift: Action[AnyContent] = Action.async { implicit _ =>
    progressToSiftJob.tryExecute().map { _ =>
      Ok("Progress to sift result job started")
    }
  }

  def firstSiftReminder: Action[AnyContent] = Action.async { implicit _ =>
    firstSiftReminderJob.tryExecute().map { _ =>
      Ok("First sift reminder job started")
    }
  }

  def secondSiftReminder: Action[AnyContent] = Action.async { implicit _ =>
    secondSiftReminderJob.tryExecute().map { _ =>
      Ok("Second sift reminder job started")
    }
  }

  def processSiftNumericalResultsReceived: Action[AnyContent] = Action.async { implicit _ =>
    processSiftNumericalResultsReceivedJob.tryExecute().map { _ =>
      Ok("Process sift numerical results received job started")
    }
  }

  def processExpiredAtSift: Action[AnyContent] = Action.async { implicit _ =>
    siftExpiryJob.tryExecute().map { _ =>
      Ok("Sift expiry job started")
    }
  }

  def processFailedAtSift: Action[AnyContent] = Action.async { implicit _ =>
    siftFailureJob.tryExecute().map { _ =>
      Ok("Process failed applications at sift job started")
    }
  }

  def progressCandidatesToAssessmentCentre: Action[AnyContent] = Action.async { implicit _ =>
    progressToAssessmentCentreJob.tryExecute().map { _ =>
      Ok("Progress to assessment centre result job started")
    }
  }

  def progressCandidatesToFsbOrOfferJob: Action[AnyContent] = Action.async { implicit _ =>
    progressToFsbOrOfferJob.tryExecute().map { _ =>
      Ok("Progress to fsb or offer job started")
    }
  }

  def evaluateAssessmentCentreCandidate: Action[AnyContent] = Action.async { implicit _ =>
    evaluateAssessmentCentreJob.tryExecute().map { _ =>
      Ok("Evaluate assessment centre job started")
    }
  }

  def notifyAssessorsOfNewEvents: Action[AnyContent] = Action.async { implicit _ =>
    notifyAssessorsOfNewEventsJob.tryExecute().map { _ =>
      Ok("Notify assessors of newly created events started")
    }
  }

  def allFailedAtFsb: Action[AnyContent] = Action.async { implicit _ =>
    fsbOverallFailureJob.tryExecute().map { _ =>
      Ok("FSB overall failure job started")
    }
  }

  def evaluateFsbResults = Action.async { implicit _ =>
    evaluateFsbJob.tryExecute().map { _ =>
      Ok("Evaluate FSB Results Job started")
    }
  }

  def notifyOnFinalFailure: Action[AnyContent] = Action.async { implicit _ =>
    notifyOnFinalFailureJob.tryExecute().map { _ =>
      Ok("Notify on final failure job started")
    }
  }

  def notifyOnFinalSuccess: Action[AnyContent] = Action.async { implicit _ =>
    notifyOnFinalSuccessJob.tryExecute().map { _ =>
      Ok("Notify on final success job started")
    }
  }
}
