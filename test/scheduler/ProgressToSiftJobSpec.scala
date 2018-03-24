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

package scheduler

import model.command.ApplicationForSiftExamples
import config.WaitingScheduledJobConfig
import model.command.ApplicationForSift
import model.{ ProgressStatuses, SchemeId, SerialUpdateResult }
import services.sift.ApplicationSiftService
import testkit.ScalaMockUnitWithAppSpec
import testkit.ScalaMockImplicits._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext

class ProgressToSiftJobSpec extends ScalaMockUnitWithAppSpec {

  implicit val ec: ExecutionContext = ExecutionContext.global

  val mockApplicationSiftService = mock[ApplicationSiftService]

  object TestProgressToSiftJob extends ProgressToSiftJob {
    override val siftService: ApplicationSiftService = mockApplicationSiftService
    override lazy val batchSize = 10
    val config = BasicJobConfig[WaitingScheduledJobConfig]("", "")
  }

  "tryExecute" must {
    "send notification if SIFT_ENTERED status" in {
      val applications = List(ApplicationForSiftExamples.phase3TestNotified("applicationId"))
      val expected = SerialUpdateResult(failures = Nil, successes = applications)
      (mockApplicationSiftService.nextApplicationsReadyForSiftStage _).expects(10).returningAsync(applications)
      (mockApplicationSiftService.progressApplicationToSiftStage _).expects(applications).returningAsync(expected)
      (mockApplicationSiftService.progressStatusForSiftStage(_: Seq[SchemeId])).expects(*).returning(ProgressStatuses.SIFT_ENTERED)
      (mockApplicationSiftService.saveSiftExpiryDate _).expects("applicationId", *).returningAsync
      (mockApplicationSiftService.sendSiftEnteredNotification(_: String)(_: HeaderCarrier)).expects("applicationId", *).returningAsync
      TestProgressToSiftJob.tryExecute().futureValue mustBe unit
    }

    "not send notification if status is not SIFT_ENTERED" in {
      val applications = List(ApplicationForSiftExamples.phase3TestNotified("applicationId"))
      val expected = SerialUpdateResult(failures = Nil, successes = applications)
      (mockApplicationSiftService.nextApplicationsReadyForSiftStage _).expects(10).returningAsync(applications)
      (mockApplicationSiftService.progressApplicationToSiftStage _).expects(applications).returningAsync(expected)
      (mockApplicationSiftService.progressStatusForSiftStage(_: Seq[SchemeId])).expects(*).returning(ProgressStatuses.SIFT_READY)
      TestProgressToSiftJob.tryExecute().futureValue mustBe unit
    }

    "send only one notification for a candidate with multiple eligible schemes" in {
      val applications = List(
        ApplicationForSiftExamples.phase3TestNotifiedWithSchemes("applicationId", schemes = Seq(SchemeId("Scheme1"), SchemeId("Scheme2")))
      )
      val expected = SerialUpdateResult(failures = Nil, successes = applications)
      (mockApplicationSiftService.nextApplicationsReadyForSiftStage _).expects(10).returningAsync(applications)
      (mockApplicationSiftService.progressApplicationToSiftStage _).expects(applications).returningAsync(expected)
      (mockApplicationSiftService.progressStatusForSiftStage(_: Seq[SchemeId])).expects(*).returning(ProgressStatuses.SIFT_ENTERED)
      (mockApplicationSiftService.saveSiftExpiryDate _).expects("applicationId", *).returningAsync
      (mockApplicationSiftService.sendSiftEnteredNotification(_: String)(_: HeaderCarrier)).expects("applicationId", *).returningAsync
      TestProgressToSiftJob.tryExecute().futureValue mustBe unit
    }
  }

}
