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

package model

import model.ApplicationStatus._
import play.api.libs.json.{ Format, JsString, JsSuccess, JsValue }
import reactivemongo.bson.{ BSON, BSONHandler, BSONString }
import scala.language.implicitConversions

// scalastyle:off
object ProgressStatuses {

  sealed abstract class ProgressStatus(val applicationStatus: ApplicationStatus) {
    def key = toString
    protected[ProgressStatuses] val order: Int
  }

  object ProgressStatus {
    implicit val progressStatusFormat = new Format[ProgressStatus] {
      def reads(json: JsValue) = JsSuccess(nameToProgressStatus(json.as[String]))
      def writes(progressStatus: ProgressStatus) = JsString(progressStatus.key)
    }

    implicit object BSONEnumHandler extends BSONHandler[BSONString, ProgressStatus] {
      def read(doc: BSONString) = nameToProgressStatus(doc.value)
      def write(progressStatus: ProgressStatus) = BSON.write(progressStatus.key)
    }
  }

  case object REGISTERED extends ProgressStatus(ApplicationStatus.REGISTERED) {
    val order = 0; override def key = "registered"}

  case object PERSONAL_DETAILS_COMPLETED extends ProgressStatus(ApplicationStatus.IN_PROGRESS_PERSONAL_DETAILS) {
    val order = 10; override def key = "personal_details_completed"}

  case object SCHEME_PREFERENCES_COMPLETED extends ProgressStatus(ApplicationStatus.IN_PROGRESS_SCHEME_PREFERENCES) {
    val order = 20;  override def key = "scheme_preferences_completed"}

  case object IN_PROGRESS_PARTNER_GRADUATE_PROGRAMMES_COMPLETED extends ProgressStatus(ApplicationStatus.IN_PROGRESS_PARTNER_GRADUATE_PROGRAMMES) {
    val order = 25; override def key = "partner_graduate_programmes_completed"}

  case object IN_PROGRESS_ASSISTANCE_DETAILS_COMPLETED extends ProgressStatus(ApplicationStatus.IN_PROGRESS_ASSISTANCE_DETAILS) {
    val order = 30; override def key = "assistance_details_completed"}

  case object START_DIVERSITY_QUESTIONNAIRE_COMPLETED extends ProgressStatus(ApplicationStatus.IN_PROGRESS_QUESTIONNAIRE) {
    val order = 40; override def key = "start_diversity_questionnaire"}

  case object DIVERSITY_QUESTIONNAIRE_COMPLETED extends ProgressStatus(ApplicationStatus.IN_PROGRESS_QUESTIONNAIRE) {
    val order = 50; override def key = "diversity_questions_completed"}

  case object EDUCATION_QUESTIONS_COMPLETED extends ProgressStatus(ApplicationStatus.IN_PROGRESS_QUESTIONNAIRE) {
    val order = 60; override def key = "education_questions_completed"}

  case object OCCUPATION_QUESTIONS_COMPLETED extends ProgressStatus(ApplicationStatus.IN_PROGRESS_QUESTIONNAIRE) {
    val order = 70; override def key = "occupation_questions_completed"}

  case object PREVIEW extends ProgressStatus(ApplicationStatus.IN_PROGRESS_PREVIEW) {
    val order = 80; override def key = "preview_completed"}

  case object SUBMITTED extends ProgressStatus(ApplicationStatus.SUBMITTED) {
    val order = 90; override def key = "submitted"}

  case object PHASE1_TESTS_INVITED extends ProgressStatus(ApplicationStatus.PHASE1_TESTS) { val order = 100 }
  case object PHASE1_TESTS_FIRST_REMINDER extends ProgressStatus(ApplicationStatus.PHASE1_TESTS) { val order = 110 }
  case object PHASE1_TESTS_SECOND_REMINDER extends ProgressStatus(ApplicationStatus.PHASE1_TESTS) { val order = 120 }
  case object PHASE1_TESTS_STARTED extends ProgressStatus(ApplicationStatus.PHASE1_TESTS) { val order = 130 }
  case object PHASE1_TESTS_COMPLETED extends ProgressStatus(ApplicationStatus.PHASE1_TESTS) { val order = 140 }
  case object PHASE1_TESTS_EXPIRED extends ProgressStatus(ApplicationStatus.PHASE1_TESTS) { val order = 150 }
  case object PHASE1_TESTS_RESULTS_READY extends ProgressStatus(ApplicationStatus.PHASE1_TESTS) { val order = 160 }
  case object PHASE1_TESTS_RESULTS_RECEIVED extends ProgressStatus(ApplicationStatus.PHASE1_TESTS) { val order = 170 }
  case object PHASE1_TESTS_PASSED extends ProgressStatus(ApplicationStatus.PHASE1_TESTS_PASSED) { val order = 180 }
  case object PHASE1_TESTS_FAILED extends ProgressStatus(ApplicationStatus.PHASE1_TESTS_FAILED) { val order = 190 }

  case object PHASE2_TESTS_INVITED extends ProgressStatus(ApplicationStatus.PHASE2_TESTS) { val order = 200 }
  case object PHASE2_TESTS_FIRST_REMINDER extends ProgressStatus(ApplicationStatus.PHASE2_TESTS) { val order = 210 }
  case object PHASE2_TESTS_SECOND_REMINDER extends ProgressStatus(ApplicationStatus.PHASE2_TESTS) { val order = 220 }
  case object PHASE2_TESTS_STARTED extends ProgressStatus(ApplicationStatus.PHASE2_TESTS) { val order = 230 }
  case object PHASE2_TESTS_COMPLETED extends ProgressStatus(ApplicationStatus.PHASE2_TESTS) { val order = 240 }
  case object PHASE2_TESTS_EXPIRED extends ProgressStatus(ApplicationStatus.PHASE2_TESTS) { val order = 250 }
  case object PHASE2_TESTS_RESULTS_READY extends ProgressStatus(ApplicationStatus.PHASE2_TESTS) { val order = 260 }
  case object PHASE2_TESTS_RESULTS_RECEIVED extends ProgressStatus(ApplicationStatus.PHASE2_TESTS) { val order = 270 }
  case object PHASE2_TESTS_PASSED extends ProgressStatus(ApplicationStatus.PHASE2_TESTS_PASSED) { val order = 280 }
  case object PHASE2_TESTS_FAILED extends ProgressStatus(ApplicationStatus.PHASE2_TESTS_FAILED) { val order = 290 }

  case object PHASE3_TESTS_INVITED extends ProgressStatus(ApplicationStatus.PHASE3_TESTS) { val order = 300 }
  case object PHASE3_TESTS_STARTED extends ProgressStatus(ApplicationStatus.PHASE3_TESTS) { val order = 330 }
  case object PHASE3_TESTS_COMPLETED extends ProgressStatus(ApplicationStatus.PHASE3_TESTS) { val order = 340 }

  case object WITHDRAWN extends ProgressStatus(ApplicationStatus.WITHDRAWN) {
    val order = 999; override def key = "withdrawn"}

  def nameToProgressStatus(name: String) = nameToProgressStatusMap(name.toLowerCase)

  // Reflection is generally 'A bad thing' but in this case it ensures that all progress statues are taken into account
  // Had considered an implementation with a macro, but that would need defining in another compilation unit

  import scala.reflect.runtime.universe._
  val allStatuses: Seq[ProgressStatus] = {
    val mirror = runtimeMirror(this.getClass.getClassLoader)
    val insMirror = mirror reflect this
    val originType = insMirror.symbol.typeSignature

    val members = originType.members

    members.collect(member => member.typeSignature match {
      case tpe if tpe <:< typeOf[ProgressStatus] && member.isModule =>
        val module = member.asModule
        (mirror reflectModule module).instance.asInstanceOf[ProgressStatus]
    }).toSeq
  }

  private val nameToProgressStatusMap: Map[String, ProgressStatus] = allStatuses.map { value =>
    value.key.toLowerCase -> value
  }.toMap

  implicit val progressOrdering = new Ordering[ProgressStatus] {
    override def compare(x: ProgressStatus, y: ProgressStatus): Int = x.order - y.order
  }

  //require(nameToProgressStatusMap.values.map(_.order).toSet == nameToProgressStatusMap.size, "Ordering must be unique")
}

