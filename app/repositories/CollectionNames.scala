/*
 * Copyright 2023 HM Revenue & Customs
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

package repositories

object CollectionNames {

  val suffixForThisCampaign = "24"

  val APPLICATION = s"application$suffixForThisCampaign"
  val ASSESSOR_ASSESSMENT_SCORES = s"assessor-assessment-scores$suffixForThisCampaign"
  val REVIEWER_ASSESSMENT_SCORES = s"reviewer-assessment-scores$suffixForThisCampaign"
  val ASSESSMENT_CENTRE_PASS_MARK_SETTINGS = s"assessment-centre-pass-mark-settings$suffixForThisCampaign"
  val ASSESSOR = s"assessor$suffixForThisCampaign"
  val ASSESSOR_ALLOCATION = s"assessor-allocation$suffixForThisCampaign"
  val ASSESSOR_EVENTS_SUMMARY_JOBS = s"assessor-events-summary-jobs$suffixForThisCampaign"
  val CAMPAIGN_MANAGEMENT_AFTER_DEADLINE_CODE = s"campaign-management-after-deadline-code$suffixForThisCampaign"
  val CANDIDATE_ALLOCATION = s"candidate-allocation$suffixForThisCampaign"
  val ASSESSMENT_EVENTS = s"assessment-events$suffixForThisCampaign"
  val FILE_UPLOAD = s"file-upload$suffixForThisCampaign"
  val CONTACT_DETAILS = s"contact-details$suffixForThisCampaign"
  val EVENT = s"event$suffixForThisCampaign"
  val LOCKS = s"locks$suffixForThisCampaign"
  val MEDIA = s"media$suffixForThisCampaign"
  val QUESTIONNAIRE = s"questionnaire$suffixForThisCampaign"
  val PHASE1_PASS_MARK_SETTINGS = s"phase1-pass-mark-settings$suffixForThisCampaign"
  val PHASE2_PASS_MARK_SETTINGS = s"phase2-pass-mark-settings$suffixForThisCampaign"
  val PHASE3_PASS_MARK_SETTINGS = s"phase3-pass-mark-settings$suffixForThisCampaign"
  val SIFT_ANSWERS = s"sift-answers$suffixForThisCampaign"
}
