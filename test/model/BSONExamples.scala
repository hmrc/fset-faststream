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

package model

import org.mongodb.scala.bson.{BsonArray, BsonDocument}

object BSONExamples {
  val SubmittedSdipCandidateWithEdipAndOtherInternshipCompleted = {
    BsonDocument(
      "_id" -> "58454b6261ae24a4609f0e4f",
      "applicationId" -> "a665043b-8317-4d28-bdf6-086859ac17ff",
      "userId" -> "459b5e72-e004-48ff-9f00-adbddf59d9c4",
      "frameworkId" -> "FastStream-2016",
      "applicationStatus" -> "SUBMITTED",
      "applicationRoute" -> "Sdip",
      "progress-status" -> BsonDocument(
        "personal-details" -> true,
        "IN_PROGRESS" -> true,
        "scheme-preferences" -> true,
        "assistance-details" -> true,
        "questionnaire" -> BsonDocument(
          "start_questionnaire" -> true,
          "education_questionnaire" -> true,
          "diversity_questionnaire" -> true,
          "occupation_questionnaire" -> true
        ),
        "preview" -> true,
        "SUBMITTED" -> true
      ),
      "personal-details" -> BsonDocument(
        "firstName" -> "Breana1",
        "lastName" -> "Bailey1",
        "preferredName" -> "PrefBreana1",
        "dateOfBirth" -> "1981-05-21",
        "edipCompleted" -> true,
        "edipYear" -> "2019",
        "otherInternshipCompleted" -> true,
        "otherInternshipName" -> "Other internship name",
        "otherInternshipYear" -> "2020"
      ),
      "progress-status-timestamp" -> BsonDocument(
        "IN_PROGRESS" -> "2016-12-05T11:11:31.787Z",
        "SUBMITTED" -> "2016-12-05T11:11:31.869Z"
      ),
      "civil-service-experience-details" -> BsonDocument(
        "applicable" -> true,
        "civilServantAndInternshipTypes" -> List("CivilServant"),
        "civilServantDepartment" -> "Accountant in Bankruptcy"
      ),
      "scheme-preferences" -> BsonDocument(
        "schemes" -> BsonArray(
          "Sdip"
        ),
        "orderAgreed" -> true,
        "eligible" -> true
      ),
      "assistance-details" -> BsonDocument(
        "hasDisability" -> "Yes",
        "needsSupportForPhoneInterviewDescription" -> "I need good headphones",
        "needsSupportForPhoneInterview" -> true,
        "needsSupportAtVenueDescription" -> "I need a comfortable chair because of my back problem",
        "needsSupportAtVenue" -> true,
        "guaranteedInterview" -> false
      )
    )
  }

  val SubmittedFsCandidate = {
    BsonDocument(
      "_id" -> "58454b6261ae24a4609f0e4f",
      "applicationId" -> "a665043b-8317-4d28-bdf6-086859ac17ff",
      "userId" -> "459b5e72-e004-48ff-9f00-adbddf59d9c4",
      "frameworkId" -> "FastStream-2016",
      "applicationStatus" -> "SUBMITTED",
      "applicationRoute" -> ApplicationRoute.Faststream.toString,
      "progress-status" -> BsonDocument(
        "personal-details" -> true,
        "IN_PROGRESS" -> true,
        "scheme-preferences" -> true,
        "assistance-details" -> true,
        "questionnaire" -> BsonDocument(
          "start_questionnaire" -> true,
          "education_questionnaire" -> true,
          "diversity_questionnaire" -> true,
          "occupation_questionnaire" -> true
        ),
        "preview" -> true,
        "SUBMITTED" -> true
      ),
      "personal-details" -> BsonDocument(
        "firstName" -> "Breana1",
        "lastName" -> "Bailey1",
        "preferredName" -> "PrefBreana1",
        "dateOfBirth" -> "1981-05-21"
      ),
      "progress-status-timestamp" -> BsonDocument(
        "IN_PROGRESS" -> "2016-12-05T11:11:31.787Z",
        "SUBMITTED" -> "2016-12-05T11:11:31.869Z"
      ),
      "civil-service-experience-details" -> BsonDocument(
        "applicable" -> true,
        "civilServantAndInternshipTypes" -> List("CivilServant", "EDIP", "SDIP", "OtherInternship"),
        "civilServantDepartment" -> "Accountant in Bankruptcy",
        "edipYear" -> "2018",
        "sdipYear" -> "2019",
        "otherInternshipName" -> "Other internship name",
        "otherInternshipYear" -> "2020",
        "fastPassReceived" -> true,
        "certificateNumber" -> "1234567"
      ),
      "scheme-preferences" -> BsonDocument(
        "schemes" -> BsonArray(
          "Commercial"
        ),
        "orderAgreed" -> true,
        "eligible" -> true
      ),
      "assistance-details" -> BsonDocument(
        "hasDisability" -> "Yes",
        "needsSupportForPhoneInterviewDescription" -> "I need good headphones",
        "needsSupportForPhoneInterview" -> true,
        "needsSupportAtVenueDescription" -> "I need a comfortable chair because of my back problem",
        "needsSupportAtVenue" -> true,
        "guaranteedInterview" -> false
      )
    )
  }
}
