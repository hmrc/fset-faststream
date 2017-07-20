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

package services.assessmentcentre

import common.FutureEx
import model.{ ProgressStatuses, SerialUpdateResult }
import model.command.ApplicationForFsac
import repositories.application.GeneralApplicationRepository
import repositories.assessmentcentre.AssessmentCentreMongoRepository

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object AssessmentCentreService extends AssessmentCentreService {
  val applicationRepo = repositories.applicationRepository
  val assessmentCentreRepo: AssessmentCentreMongoRepository = repositories.assessmentCentreRepository
}

trait AssessmentCentreService {

  def applicationRepo: GeneralApplicationRepository
  def assessmentCentreRepo: AssessmentCentreMongoRepository

  def nextApplicationForAssessmentCentre(batchSize: Int): Future[Seq[ApplicationForFsac]] = {
    assessmentCentreRepo.nextApplicationForAssessmentCentre(batchSize)
  }

  def progressApplicationToAssessmentCentre(applications: Seq[ApplicationForFsac]): Future[SerialUpdateResult[ApplicationForFsac]] = {
      val updates = FutureEx.traverseSerial(applications) { application =>
      FutureEx.futureToEither(application,
        for {
          _ <- assessmentCentreRepo.progressApplicationToAssessmentCentre(application)
          result <- applicationRepo.addProgressStatusAndUpdateAppStatus(application.applicationId,
            ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION)
        } yield result
      )
    }

    updates.map(SerialUpdateResult.fromEither)
  }
}
