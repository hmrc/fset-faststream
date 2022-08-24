/*
 * Copyright 2022 HM Revenue & Customs
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

package scheduler.sift

import config.WaitingScheduledJobConfig

import javax.inject.{Inject, Singleton}
import model.EmptyRequestHeader
import play.api.mvc.RequestHeader
import play.api.{Configuration, Logging}
import uk.gov.hmrc.mongo.MongoComponent
//import play.modules.reactivemongo.ReactiveMongoComponent
import scheduler.BasicJobConfig
import scheduler.clustering.SingleInstanceScheduledJob
import services.NumericalTestService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

// Cubiks specific class - redundant
@Singleton
class RetrieveSiftNumericalResultsJobImpl @Inject() (val numericalTestService: NumericalTestService,
                                                     val mongoComponent: MongoComponent,
                                                     val config: RetrieveSiftNumericalResultsJobConfig
                                                    ) extends RetrieveSiftNumericalResultsJob {
}

// TODO: cubiks specific can delete
trait RetrieveSiftNumericalResultsJob extends SingleInstanceScheduledJob[BasicJobConfig[WaitingScheduledJobConfig]] with Logging {
  val numericalTestService: NumericalTestService

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    val intro = "Retrieving xml results for candidates in SIFT"
    logger.info(intro)
/*
    numericalTestService.nextTestGroupWithReportReady.flatMap {
      case Some(testGroup) =>
        logger.info(s"$intro - processing candidate with applicationId: ${testGroup.applicationId}")

        implicit val hc: HeaderCarrier = HeaderCarrier()
        implicit val rh: RequestHeader = EmptyRequestHeader
        numericalTestService.retrieveTestResult(testGroup)
      case None =>
        logger.info(s"$intro - found no candidates")
        Future.successful(())
    }*/
    Future.successful(())
  }
}

class RetrieveSiftNumericalResultsJobConfig @Inject() (config: Configuration) extends BasicJobConfig[WaitingScheduledJobConfig](
  config = config,
  configPrefix = "scheduling.retrieve-sift-numerical-results-job",
  name = "RetrieveSiftNumericalResultsJob"
)
