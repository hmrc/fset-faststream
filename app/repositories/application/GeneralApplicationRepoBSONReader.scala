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

package repositories.application

import model.{ ApplicationRoute, SchemeId }
import model.ApplicationRoute._
import model.ApplicationStatus.ApplicationStatus
import model.Candidate
import model.EvaluationResults.Result
import model.persisted._
import org.joda.time.LocalDate
import reactivemongo.bson.{ BSONDocument, _ }
import repositories._

trait GeneralApplicationRepoBSONReader extends BaseBSONReader {

  implicit val toApplicationForNotification: BSONDocumentReader[ApplicationForNotification] = bsonReader {
    (doc: BSONDocument) => {
      val applicationId = doc.getAs[String]("applicationId").get
      val userId = doc.getAs[String]("userId").get
      val applicationStatus = doc.getAs[ApplicationStatus]("applicationStatus").get
      val personalDetailsRoot = doc.getAs[BSONDocument]("personal-details").get
      val preferredName = personalDetailsRoot.getAs[String]("preferredName").get
      ApplicationForNotification(applicationId, userId, preferredName, applicationStatus)
    }
  }

  implicit val toCandidate: BSONDocumentReader[Candidate] = bsonReader {
    (doc: BSONDocument) => {
      val userId = doc.getAs[String]("userId").getOrElse("")
      val applicationId = doc.getAs[String]("applicationId")
      // If the application does not have applicationRoute, it is legacy data
      // as it needs to be interpreted as Faststream
      val applicationRoute = doc.getAs[ApplicationRoute]("applicationRoute").getOrElse(ApplicationRoute.Faststream)
      val applicationStatus = doc.getAs[String]("applicationStatus")

      val psRoot = doc.getAs[BSONDocument]("personal-details")
      val firstName = psRoot.flatMap(_.getAs[String]("firstName"))
      val lastName = psRoot.flatMap(_.getAs[String]("lastName"))
      val preferredName = psRoot.flatMap(_.getAs[String]("preferredName"))
      val dateOfBirth = psRoot.flatMap(_.getAs[LocalDate]("dateOfBirth"))

      Candidate(userId, applicationId, None, firstName, lastName, preferredName, dateOfBirth, None, None, None,
        Some(applicationRoute), applicationStatus)
    }
  }
}
