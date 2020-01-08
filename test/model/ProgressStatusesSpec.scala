/*
 * Copyright 2020 HM Revenue & Customs
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

package model

import services.testdata.candidate.ApplicationStatusOnlyForTest
import testkit.UnitSpec

class ProgressStatusesSpec extends UnitSpec {

  "Progress statuses" should {
    "be assigned to all application statuses" in {
      object JustForTest extends Enumeration with ApplicationStatusOnlyForTest {
        type ApplicationStatus = Value
      }

      val excludedApplicationStatuses = JustForTest.values.map(_.toString).toList
      val allAppStatusesAssignedToProgressStatuses: Seq[String] = ProgressStatuses.allStatuses.map(_.applicationStatus.toString).sorted
      val allAppStatuses: Seq[String] = ApplicationStatus.values.map(_.toString).toSeq diff excludedApplicationStatuses

      allAppStatuses.foreach { appStatus =>
        allAppStatusesAssignedToProgressStatuses must contain(appStatus)
      }

      allAppStatuses.size mustBe allAppStatuses.size
    }
  }
}
