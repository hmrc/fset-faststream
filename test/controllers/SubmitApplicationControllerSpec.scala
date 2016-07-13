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

package controllers

import config.TestFixtureBase
import connectors.EmailClient
import mocks.application.{ AssistanceDetailsInMemoryRepository, DocumentRootInMemoryRepository, PersonalDetailsInMemoryRepository }
import mocks.{ ContactDetailsInMemoryRepository, _ }
import org.mockito.Matchers.{ eq => eqTo }
import org.scalatestplus.play.PlaySpec
import play.api.mvc._
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import repositories._
import repositories.application.{ AssistanceDetailsRepository, GeneralApplicationRepository, PersonalDetailsRepository }
import services.AuditService

import scala.language.postfixOps

class SubmitApplicationControllerSpec extends PlaySpec with Results {

  "Submit Application" should {

    "audit when correct data is in the storage" in pending
    //new TestFixture { TODO
    //  val action = TestSubmitApplicationController.submitApplication("000-000", "111-111")(submitApplicationRequest("000-000", "111-111"))
    //  status(action.run) must be(200)
    //  verify(mockAuditService).logEvent(eqTo("ApplicationSubmitted"))(any[HeaderCarrier], any[RequestHeader])
    //}

    "return 400 when no data in the storage" in pending
    // Original test didn't actually exercise any controller code - so this test was never in fact implemented.
    // Reverted to TODO, like the other 'todo' test, since it needs implementing correctly.

    "return 200 when correct data is in the storage" in pending
    //    { TODO
    //      TestSubmitApplicationController.frameworkPrefRepository.savePreferences(appId, ApplicationValidatorSpec.preferences.get)
    //      TestSubmitApplicationController.cdRepository.update(userId, ContactDetails(
    //        Address("", None, None, None),
    //        "aaa",
    //        "aaa",
    //        None
    //      ))
    //      status(action.run) must be(200)
    //    }

  }

  trait TestFixture extends TestFixtureBase {
    object TestSubmitApplicationController extends SubmitApplicationController {
      override val appRepository: GeneralApplicationRepository = DocumentRootInMemoryRepository
      override val pdRepository: PersonalDetailsRepository = PersonalDetailsInMemoryRepository
      override val adRepository: AssistanceDetailsRepository = AssistanceDetailsInMemoryRepository
      override val frameworkPrefRepository: FrameworkPreferenceRepository = FrameworkPreferenceInMemoryRepository
      override val frameworkRegionsRepository: FrameworkRepository = FrameworkInMemoryRepository
      override val cdRepository: ContactDetailsRepository = ContactDetailsInMemoryRepository
      override val auditService: AuditService = mockAuditService
      override val emailClient: EmailClient = EmailClientStub
    }

    def submitApplicationRequest(userId: String, applicationId: String) = {
      FakeRequest(Helpers.PUT, controllers.routes.SubmitApplicationController.submitApplication(userId, applicationId).url, FakeHeaders(), "")
    }
  }
}
