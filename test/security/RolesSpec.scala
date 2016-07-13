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

package security

import java.util.UUID

import connectors.exchange.{ AssessmentCentre, AssessmentScores }
import models.ApplicationData.ApplicationStatus
import models.ApplicationData.ApplicationStatus.{ CREATED, _ }
import models._
import org.scalatest.MustMatchers
import org.scalatestplus.play.PlaySpec
import play.api.i18n.Lang
import play.api.test.FakeRequest
import play.api.test.Helpers._
import security.Roles.{ AssessmentCentreFailedToAttendRole, CsrAuthorization, WithdrawComponent }

class RolesSpec extends PlaySpec with MustMatchers {
  import RolesSpec._

  val request = FakeRequest(GET, "")

  "Withdraw Component" should {
    "be enable only for specific roles" in {
      val disabledStatuses = List(IN_PROGRESS, WITHDRAWN, CREATED, ONLINE_TEST_FAILED, ONLINE_TEST_FAILED_NOTIFIED,
        ASSESSMENT_CENTRE_FAILED, ASSESSMENT_CENTRE_FAILED_NOTIFIED)
      val enabledStatuses = ApplicationStatus.values.toList.diff(disabledStatuses)

      assertValidAndInvalidStatuses(WithdrawComponent, enabledStatuses, disabledStatuses)
    }
  }

  "Assessment Centre Failed to attend role" should {
    "be authorised only for specific roles" in {
      val enabledStatuses = List(FAILED_TO_ATTEND)
      val disabledStatuses = ApplicationStatus.values.toList.diff(enabledStatuses)

      assertValidAndInvalidStatuses(AssessmentCentreFailedToAttendRole, enabledStatuses, disabledStatuses)
    }
  }

  def assertValidAndInvalidStatuses(
    role: CsrAuthorization,
    valid: List[ApplicationStatus.Value], invalid: List[ApplicationStatus.Value]
  ) = {
    valid.foreach { validStatus =>
      withClue(s"$validStatus is not accepted by $role") {
        role.isAuthorized(activeUser(validStatus))(request, Lang("en-GB")) must be(true)
      }
    }

    invalid.foreach { invalidStatus =>
      withClue(s"$invalidStatus is accepted by $role") {
        role.isAuthorized(activeUser(invalidStatus))(request, Lang("en-GB")) must be(false)
      }
    }
  }
}

object RolesSpec {
  val id = UniqueIdentifier(UUID.randomUUID().toString)

  def activeUser(applicationStatus: ApplicationStatus) = CachedData(CachedUser(
    id,
    "John", "Biggs", None, "aaa@bbb.com", isActive = true, "locked"
  ), Some(ApplicationData(id, id, applicationStatus,
    Progress(true, true, true, true, true, true, true, true, true, true,
      OnlineTestProgress(true, true, true, true, true, true, true, true, true, true),
      true, AssessmentScores(true, true), AssessmentCentre(true, true)))))

  def registeredUser(applicationStatus: ApplicationStatus) = CachedData(CachedUser(
    id,
    "John", "Biggs", None, "aaa@bbb.com", isActive = true, "locked"
  ), None)

}
