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

package testkit

import org.joda.time.DateTime
import org.joda.time.Seconds._
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{ Millis, Span }
import play.api.test.{ FakeApplication, Helpers }
import play.modules.reactivemongo.ReactiveMongoPlugin
import reactivemongo.api.DefaultDB
import reactivemongo.bson.BSONDocument
import reactivemongo.json.ImplicitBSONHandlers
import reactivemongo.json.collection.JSONCollection
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.language.postfixOps

abstract class MongoRepositorySpec extends UnitWithAppSpec with Inside with Inspectors with IndexesReader {
  import ImplicitBSONHandlers._

  val timeout = 60 seconds
  val collectionName: String
  val additionalCollections: List[String] = Nil

  val FrameworkId = "FrameworkId"

  val timesApproximatelyEqual = (time1: DateTime, time2: DateTime) => secondsBetween(time1, time2)
    .isLessThan(seconds(5))

  override implicit def patienceConfig = PatienceConfig(timeout = scaled(Span(timeout.toMillis, Millis)))

  implicit val context = play.api.libs.concurrent.Execution.Implicits.defaultContext

  implicit def mongo: () => DefaultDB = {
    ReactiveMongoPlugin.mongoConnector.db
  }

  override implicit lazy val app: FakeApplication = FakeApplication(
    additionalConfiguration = additionalConfig, withoutPlugins = Seq(
      "uk.gov.hmrc.play.health.HealthPlugin",
      "com.kenshoo.play.metrics.MetricsPlugin")
  )

  override def withFixture(test: NoArgTest) = {
    Helpers.running(app) {
      Future.traverse(collectionName :: additionalCollections)(clearCollection).futureValue
      super.withFixture(test)
    }
  }

  private def clearCollection(name: String) = {
    val collection = mongo().collection[JSONCollection](name)
    collection.remove(BSONDocument.empty)
  }
}

trait IndexesReader {
  this: ScalaFutures =>

  def indexesWithFields(repo: ReactiveRepository[_, _])(implicit ec: ExecutionContext): Seq[Seq[String]] = {
    val indexesManager = repo.collection.indexesManager
    val indexes = indexesManager.list().futureValue
    indexes.map(_.key.map(_._1))
  }
}
