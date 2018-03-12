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

package security

import java.util.UUID

import connectors.exchange.ProgressExamples
import connectors.exchange.CivilServiceExperienceDetailsExamples._
import controllers.UnitSpec
import models.ApplicationData.ApplicationStatus
import models.ApplicationData.ApplicationStatus.{ CREATED, _ }
import models._
import models.CachedDataExample._
import play.api.i18n.Lang
import play.api.mvc.RequestHeader
import play.api.test.FakeRequest
import play.api.test.Helpers._
import security.Roles.{ CsrAuthorization, WithdrawComponent }

class RolesSpec extends UnitSpec {
  import RolesSpec._

  val request = FakeRequest(GET, "")

  "hasFastPassBeenApproved" must {
    val user = activeUser(ApplicationStatus.SUBMITTED)
    "return true if the candidate fastPass has been accepted" in {
      val appData = CreatedApplication.copy(civilServiceExperienceDetails = Some(CivilServantExperienceFastPassApproved))
      implicit val rh = mock[RequestHeader]
      RoleUtils.hasFastPassBeenApproved(user.copy(application = Some(appData)))(rh) mustBe true
    }
    "return false if the candidate fastPass has not been accepted" in {
      val appData = CreatedApplication.copy(civilServiceExperienceDetails = Some(CivilServantExperienceFastPassRejectd))
      implicit val rh = mock[RequestHeader]
      RoleUtils.hasFastPassBeenApproved(user.copy(application = Some(appData)))(rh) mustBe false
    }
    "return false if the acceptance flag is not present" in {
      val appData = CreatedApplication.copy(civilServiceExperienceDetails = Some(CivilServantExperience))
      implicit val rh = mock[RequestHeader]
      RoleUtils.hasFastPassBeenApproved(user.copy(application = Some(appData)))(rh) mustBe false
    }
    "return false if the are no civil servant details" in {
      val appData = CreatedApplication.copy(civilServiceExperienceDetails = None)
      implicit val rh = mock[RequestHeader]
      RoleUtils.hasFastPassBeenApproved(user.copy(application = Some(appData)))(rh) mustBe false
    }
  }

  "Withdraw Component" must {
    "be enabled only for specific roles" in {
      val disabledStatuses = List(IN_PROGRESS, WITHDRAWN, CREATED, PHASE3_TESTS_PASSED_NOTIFIED)
      val enabledStatuses = ApplicationStatus.values.toList.diff(disabledStatuses)

      assertValidAndInvalidStatuses(WithdrawComponent, enabledStatuses, disabledStatuses)
    }
  }

  /*"Assessment Centre Failed to attend role" must {
    "be authorised only for specific roles" in {
      val enabledStatuses = List(FAILED_TO_ATTEND)
      val disabledStatuses = ApplicationStatus.values.toList.diff(enabledStatuses)

      assertValidAndInvalidStatuses(AssessmentCentreFailedToAttendRole, enabledStatuses, disabledStatuses)
    }
  }*/

  def assertValidAndInvalidStatuses(
    role: CsrAuthorization,
    valid: List[ApplicationStatus.Value], invalid: List[ApplicationStatus.Value]
  ) = {
    valid.foreach { validStatus =>
      withClue(s"$validStatus is not accepted by $role") {
        role.isAuthorized(activeUser(validStatus))(request) mustBe(true)
      }
    }

    invalid.foreach { invalidStatus =>
      withClue(s"$invalidStatus is accepted by $role") {
        role.isAuthorized(activeUser(invalidStatus))(request) mustBe(false)
      }
    }
  }
}

object RolesSpec {
  val id = UniqueIdentifier(UUID.randomUUID().toString)

  def activeUser(applicationStatus: ApplicationStatus, progress: Progress = ProgressExamples.FullProgress) = CachedData(CachedUser(
    id,
    "John", "Biggs", None, "aaa@bbb.com", isActive = true, "locked"
  ), Some(ApplicationData(id, id, applicationStatus, ApplicationRoute.Faststream, progress, None, None, None)))

  def registeredUser(applicationStatus: ApplicationStatus) = CachedData(CachedUser(
    id,
    "John", "Biggs", None, "aaa@bbb.com", isActive = true, "locked"
  ), None)
}
