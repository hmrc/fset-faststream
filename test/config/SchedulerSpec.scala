/*
 * Copyright 2016 HM Revenue & Customs
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

package config

import scheduler.onlinetesting.SendPhase1InvitationJob
import testkit.{ ShortTimeout, UnitWithAppSpec }

class SchedulerDisabledSpec extends UnitWithAppSpec with ShortTimeout {

  override def additionalConfig =
    super.additionalConfig ++ Map("scheduling.online-testing.send-phase1-invitation-job.enabled" -> "false")

  "Scheduler" should {
    "not contain send invitation job if it is disabled" in {
      val scheduler = new Scheduler {
        override val appConfig: MicroserviceAppConfig = new MicroserviceAppConfig {}
      }

      scheduler.scheduledJobs must not contain SendPhase1InvitationJob
    }
  }
}

class SchedulerEnabledSpec extends UnitWithAppSpec with ShortTimeout {

  override def additionalConfig =
    super.additionalConfig ++ Map("scheduling.online-testing.send-phase1-invitation-job.enabled" -> "true")

  "Scheduler" should {
    "contain send invitation job if it is enabled" in {
      val scheduler = new Scheduler {
        override val appConfig: MicroserviceAppConfig = new MicroserviceAppConfig {}
      }
      scheduler.scheduledJobs must contain(SendPhase1InvitationJob)
    }
  }
}
