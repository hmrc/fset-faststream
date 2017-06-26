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

package controllers

import config.TestFixtureBase
import controllers.reference.SkillTypeController
import model.exchange.AssessorSkill
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import testkit.UnitWithAppSpec

import scala.concurrent.Future

class SkillTypeControllerSpec extends UnitWithAppSpec {

  "all skills" must {
    "return all skills" in new TestFixture {
      val res: Future[Result] = controller.allSkills.apply(FakeRequest())
      status(res) mustBe OK
      Json.fromJson[List[AssessorSkill]](Json.parse(contentAsString(res))).get mustBe AssessorSkill.AllSkillsWithLabels
    }
  }

  trait TestFixture extends TestFixtureBase {
    val controller = SkillTypeController
  }

}
