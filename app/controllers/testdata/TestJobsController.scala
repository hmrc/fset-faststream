/*
 * Copyright 2018 HM Revenue & Customs
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

import play.api.mvc.{ Action, AnyContent }
import scheduler.assessment.EvaluateAssessmentScoreJob
import scheduler.onlinetesting._
import scheduler._
import scheduler.fsb.EvaluateFsbJob
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object TestJobsController extends TestJobsController

class TestJobsController extends BaseController {

  def expireOnlineTestJob(phase: String): Action[AnyContent] = Action.async { implicit request =>
    phase.toUpperCase match {
      case "PHASE1" => ExpirePhase1TestJob.tryExecute().map(_ => Ok(s"$phase expiry job started"))
      case "PHASE2" => ExpirePhase2TestJob.tryExecute().map(_ => Ok(s"$phase expiry job started"))
      case "PHASE3" => ExpirePhase3TestJob.tryExecute().map(_ => Ok(s"$phase expiry job started"))
      case _ => Future.successful(BadRequest(s"No such phase: $phase. Options are [phase1, phase2, phase3]"))
    }
  }
  def evaluatePhase1OnlineTestsCandidate: Action[AnyContent] = Action.async { implicit request =>
    EvaluatePhase1ResultJob.tryExecute().map { _ =>
      Ok("Evaluate phase 1 result job started")
    }
  }

  def evaluatePhase2EtrayCandidate: Action[AnyContent] = Action.async { implicit request =>
    EvaluatePhase2ResultJob.tryExecute().map { _ =>
      Ok("Evaluate phase 2 result job started")
    }
  }

  def evaluatePhase3VideoInterviewCandidate: Action[AnyContent] = Action.async { implicit request =>
    EvaluatePhase3ResultJob.tryExecute().map { _ =>
      Ok("Evaluate phase 3 result job started")
    }
  }

  def processSuccessPhase1TestJob: Action[AnyContent] = Action.async { implicit request =>
    SuccessPhase1TestJob.tryExecute().map { _ =>
      Ok("Success phase 1 test job started")
    }
  }

  def progressCandidatesToSift: Action[AnyContent] = Action.async { implicit request =>
    ProgressToSiftJob.tryExecute().map { _ =>
      Ok("Progress to sift result job started")
    }
  }

  def processFailedAtSift: Action[AnyContent] = Action.async { implicit request =>
    SiftFailureJob.tryExecute().map { _ =>
      Ok("Process failed applications at sift job started")
    }
  }

  def progressCandidatesToAssessmentCentre: Action[AnyContent] = Action.async { implicit request =>
    ProgressToAssessmentCentreJob.tryExecute().map { _ =>
      Ok("Progress to assessment centre result job started")
    }
  }

  def progressCandidatesToFsbOrOfferJob: Action[AnyContent] = Action.async { implicit request =>
    ProgressToFsbOrOfferJob.tryExecute().map { _ =>
      Ok("Progress to fsb or offer job started")
    }
  }

  def evaluateAssessmentScoresCandidate: Action[AnyContent] = Action.async { implicit request =>
    EvaluateAssessmentScoreJob.tryExecute().map { _ =>
      Ok("Evaluate assessment score job started")
    }
  }

  def evaluateFsbResults = Action.async { implicit request =>
    EvaluateFsbJob.tryExecute().map { _ =>
      Ok("Evaluate FSB Results Job started")
    }
  }

  def notifyAssessorsOfNewEvents: Action[AnyContent] = Action.async { implicit request =>
    NotifyAssessorsOfNewEventsJob.tryExecute().map { _ =>
      Ok("Notify assessors of newly created events started")
    }
  }

  def allFailedAtFsb: Action[AnyContent] = Action.async { implicit request =>
    FsbOverallFailureJob.tryExecute().map { _ =>
      Ok("FSB overall failure job started")
    }
  }

  def notifyOnFinalFailure: Action[AnyContent] = Action.async { implicit request =>
    NotifyOnFinalFailureJob.tryExecute().map { _ =>
      Ok("Notify on final failure job started")
    }
  }
}
