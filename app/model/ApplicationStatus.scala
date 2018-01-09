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

package model

import play.api.libs.json._
import reactivemongo.bson.{ BSON, BSONHandler, BSONString }
import services.testdata.candidate.ApplicationStatusOnlyForTest

import scala.language.implicitConversions

object ApplicationStatus extends Enumeration with ApplicationStatusOnlyForTest {
  type ApplicationStatus = Value
  // Please note the enum order is important and must reflect the actual application flow.
  // OnlineTestEvaluationRepository.validEvaluationPhaseStatuses depends on this
  val WITHDRAWN, CREATED, IN_PROGRESS, SUBMITTED = Value
  val PHASE1_TESTS, PHASE1_TESTS_PASSED, PHASE1_TESTS_FAILED = Value
  val PHASE2_TESTS, PHASE2_TESTS_PASSED, PHASE2_TESTS_FAILED = Value
  val PHASE3_TESTS, PHASE3_TESTS_PASSED_WITH_AMBER, PHASE3_TESTS_PASSED, PHASE3_TESTS_FAILED = Value
  val PHASE3_TESTS_PASSED_NOTIFIED, PHASE1_TESTS_PASSED_NOTIFIED, ARCHIVED, FAST_PASS_ACCEPTED = Value
  val SIFT, FAILED_AT_SIFT = Value
  val ASSESSMENT_CENTRE, FSB, ELIGIBLE_FOR_JOB_OFFER = Value
  implicit def toString(applicationStatus: ApplicationStatus): String = applicationStatus.toString

  implicit val applicationStatusFormat = new Format[ApplicationStatus] {
    def reads(json: JsValue) = JsSuccess(ApplicationStatus.withName(json.as[String].toUpperCase()))
    def writes(myEnum: ApplicationStatus) = JsString(myEnum.toString)
  }

  implicit object BSONEnumHandler extends BSONHandler[BSONString, ApplicationStatus] {
    def read(doc: BSONString) = ApplicationStatus.withName(doc.value.toUpperCase())
    def write(stats: ApplicationStatus) = BSON.write(stats.toString)
  }
}
