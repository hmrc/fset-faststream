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

package models

sealed trait AssessmentStatus
case object ASSESSMENT_BOOKED_CONFIRMED extends AssessmentStatus
case object ASSESSMENT_CONFIRMATION_EXPIRED extends AssessmentStatus
case object ASSESSMENT_PENDING_CONFIRMATION extends AssessmentStatus
case object ASSESSMENT_FAILED extends AssessmentStatus
case object ASSESSMENT_PASSED extends AssessmentStatus
case object ASSESSMENT_NOT_ATTENDED extends AssessmentStatus
case object ASSESSMENT_FAILED_RETRY extends AssessmentStatus
case object ASSESSMENT_PASSED_ON_BOARD extends AssessmentStatus
case object ASSESSMENT_STATUS_UNKNOWN extends AssessmentStatus


