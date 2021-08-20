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

package repositories

import akka.stream.scaladsl.{Keep, Sink}
import factories.DateTimeFactoryImpl
import model.Exceptions.ApplicationNotFound
import play.api.libs.json.JsValue
import reactivemongo.bson.{BSONBoolean, BSONDocument}
import reactivemongo.play.json.ImplicitBSONHandlers
import repositories.application.{DiagnosticReportingMongoRepository, GeneralApplicationMongoRepository}
import testkit.MongoRepositorySpec

class DiagnosticReportRepositorySpec extends MongoRepositorySpec {
  import ImplicitBSONHandlers._

  override val collectionName: String = CollectionNames.APPLICATION
  
  def diagnosticReportRepo = new DiagnosticReportingMongoRepository(mongo)
  def helperRepo = new GeneralApplicationMongoRepository(new DateTimeFactoryImpl, appConfig, mongo)


  "Find by user id" should {
    "return an empty list if there is nobody with this userId" in {
      val result = diagnosticReportRepo.findByApplicationId("123").failed.futureValue
      result mustBe an[ApplicationNotFound]
    }

    "return user's application with the specific Id" in {
      helperRepo.collection.insert(ordered = false).one(userWithAllDetails("user1", "app1", "FastStream-2016")).futureValue
      helperRepo.collection.insert(ordered = false).one(userWithAllDetails("user1", "app2", "SDIP-2016")).futureValue
      helperRepo.collection.insert(ordered = false).one(userWithAllDetails("user2", "app3", "FastStream-2016")).futureValue

      val result = diagnosticReportRepo.findByApplicationId("app1").futureValue
      result.length mustBe 1
      (result.head \ "applicationId").as[String] mustBe "app1"
      (result.head \ "progress-status" \ "registered").as[Boolean] mustBe true
      (result.head \ "progress-status" \ "questionnaire" \ "start_diversity_questionnaire").as[Boolean] mustBe true
      (result.head \\ "personal-details") mustBe Nil
    }
  }

  "Find all users" should {
    "return user's application with the specific Id" in {
      helperRepo.collection.insert(ordered = false).one(userWithAllDetails("user1", "app1", "FastStream-2016")).futureValue
      helperRepo.collection.insert(ordered = false).one(userWithAllDetails("user1", "app2", "SDIP-2016")).futureValue
      helperRepo.collection.insert(ordered = false).one(userWithAllDetails("user2", "app3", "FastStream-2016")).futureValue

      // An operator with exactly one output, emitting data elements whenever downstream operators are ready to receive them
      val source = diagnosticReportRepo.findAll(materializer)
      // An operator with exactly one input, requesting and accepting data elements, possibly slowing down the upstream producer of elements.
      val sink = Sink.fold[List[JsValue], JsValue](Nil){(acc, v) => acc :+ v}
      // Connect the Source to the Sink, obtaining a RunnableGraph
      // The RunnableGraph is a flow that has both ends "attached" to a Source and Sink respectively, and is ready to be run
      // It is important to remember that even after constructing the RunnableGraph by connecting all the source, sink and
      // different operators, no data will flow through it until it is materialized (run)
      val runnable = source.toMat(sink)(Keep.right)
      // Materialize the flow and get the value of the Sink (the List[JsValue]), which we sort by applicationId
      val result = runnable.run()(materializer).futureValue.sortBy(x => (x \ "applicationId").as[String])

      result.length mustBe 3
      (result(0) \ "applicationId").as[String] mustBe "app1"
      (result(0) \ "progress-status" \ "registered").as[Boolean] mustBe true
      (result(0) \ "progress-status" \ "questionnaire" \ "start_diversity_questionnaire").as[Boolean] mustBe true
      (result(0) \\ "personal-details") mustBe Nil
      (result(1) \ "applicationId").as[String] mustBe "app2"
      (result(1) \ "progress-status" \ "registered").as[Boolean] mustBe true
      (result(1) \ "progress-status" \ "questionnaire" \ "start_diversity_questionnaire").as[Boolean] mustBe true
      (result(1) \\ "personal-details") mustBe Nil
      (result(2) \ "applicationId").as[String] mustBe "app3"
      (result(2) \ "progress-status" \ "registered").as[Boolean] mustBe true
      (result(2) \ "progress-status" \ "questionnaire" \ "start_diversity_questionnaire").as[Boolean] mustBe true
      (result(2) \\ "personal-details") mustBe Nil
    }
  }

  def userWithAllDetails(userId: String, appId: String, frameworkId: String) = BSONDocument(
    "applicationId" -> appId,
    "userId" -> userId,
    "frameworkId" -> frameworkId,
    "applicationStatus" -> "AWAITING_ALLOCATION",
    "personal-details" -> BSONDocument(
      "firstName" -> "Testy",
      "lastName" -> "McTestface",
      "preferredName" -> "Reginald",
      "dateOfBirth" -> "1987-12-22"
    ),
    "progress-status" -> BSONDocument(
      "registered" -> BSONBoolean(true),
      "personal_details_completed" -> BSONBoolean(true),
      "schemes_and_locations_completed" -> BSONBoolean(true),
      "assistance_details_completed" -> BSONBoolean(true),
      "preview_completed" -> BSONBoolean(true),
      "questionnaire" -> BSONDocument(
        "start_diversity_questionnaire" -> BSONBoolean(true),
        "diversity_questions_completed" -> BSONBoolean(true),
        "education_questions_completed" -> BSONBoolean(true),
        "occupation_questions_completed" -> BSONBoolean(true)
      ),
      "submitted" -> BSONBoolean(true),
      "online_test_invited" -> BSONBoolean(true),
      "online_test_started" -> BSONBoolean(true),
      "online_test_completed" -> BSONBoolean(true),
      "awaiting_online_test_allocation" -> BSONBoolean(true)
    ))
}
