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

package repositories.application

import model.Commands.{ CreateApplicationRequest }
import model.command.ProgressResponse
import model.Exceptions.NotFoundException
import model.PersistedObjects.{ ApplicationProgressStatus, ApplicationProgressStatuses, ApplicationUser }
import model.{ ApplicationStatusOrder, Commands }
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONBoolean, BSONDocument, BSONObjectID }
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait DiagnosticReportingRepository {

  def findByUserId(userId: String): Future[ApplicationUser]
}

class DiagnosticReportingMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[CreateApplicationRequest, BSONObjectID]("application", mongo,
    Commands.Implicits.createApplicationRequestFormats, ReactiveMongoFormats.objectIdFormats) with DiagnosticReportingRepository {

  def findByUserId(userId: String): Future[ApplicationUser] = {
    val query = BSONDocument("userId" -> userId)
    collection.find(query).one[BSONDocument] map {
      case Some(doc) =>
        val appId = doc.getAs[String]("applicationId").get
        val userId = doc.getAs[String]("userId").get
        val frameworkId = doc.getAs[String]("frameworkId").get
        val applicationStatus = doc.getAs[String]("applicationStatus").get

        val progressStatusRoot = doc.getAs[BSONDocument]("progress-status")
        // The registered status is not in the global list of statuses
        val statusesMap = ApplicationStatusOrder.statusMaps(ProgressResponse("unused")).map(s => s._3 -> s._2).toMap ++ Map("registered" -> 0)
        val statusesOrdered = statusesMap.keys.toList.sortWith((s1, s2) => statusesMap(s1) <= statusesMap(s2))

        val statuses = doc.getAs[BSONDocument]("progress-status").map { root =>
          Some(root.elements.filterNot(_._1 == "questionnaire").collect {
            case (name, BSONBoolean(value)) => ApplicationProgressStatus(name, value)
          }.toList)
        }.getOrElse(Some(List(ApplicationProgressStatus("registered", true))))

        val questionnaireStatuses = doc.getAs[BSONDocument]("progress-status").flatMap { root =>
          root.getAs[BSONDocument]("questionnaire").map { doc =>
            Some(doc.elements.collect {
              case (name, BSONBoolean(value)) => ApplicationProgressStatus(name, value)
            }.toList)
          }.getOrElse(None)
        }

        ApplicationUser(appId, userId, frameworkId, applicationStatus, ApplicationProgressStatuses(statuses, questionnaireStatuses))
      case _ => throw new NotFoundException()
    }
  }
}
