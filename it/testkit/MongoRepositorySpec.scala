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

import com.kenshoo.play.metrics.PlayModule
import org.joda.time.DateTime
import org.joda.time.Seconds._
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{ Millis, Span }
import org.scalatestplus.play.PlaySpec
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import reactivemongo.api.DefaultDB
import reactivemongo.bson.BSONDocument
import reactivemongo.json.ImplicitBSONHandlers
import reactivemongo.json.collection.JSONCollection
import play.modules.reactivemongo.MongoDbConnection
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps

abstract class MongoRepositorySpec extends PlaySpec with MockitoSugar with Inside with Inspectors with ScalaFutures with IndexesReader
  with MongoDbConnection {
  import ImplicitBSONHandlers._

  val timeout: FiniteDuration = 60 seconds
  val collectionName: String
  val additionalCollections: List[String] = Nil
  val unit: Unit = ()
  val AppId = "AppId"
  val UserId = "UserId"

  val FrameworkId = "FrameworkId"

  val timesApproximatelyEqual: (DateTime, DateTime) => Boolean = (time1: DateTime, time2: DateTime) => secondsBetween(time1, time2)
    .isLessThan(seconds(5))

  implicit final def app: Application = new GuiceApplicationBuilder()
    .disable[PlayModule]
    .build

  override implicit def patienceConfig = PatienceConfig(timeout = scaled(Span(timeout.toMillis, Millis)))

  implicit val context: ExecutionContext = play.api.libs.concurrent.Execution.Implicits.defaultContext

  implicit def mongo: () => DefaultDB = {
    new MongoDbConnection {}.mongoConnector.db
  }

  //override def withFixture(test: NoArgTest): Outcome = {
  //  Helpers.running(app) {
  //    Future.traverse(collectionName :: additionalCollections)(clearCollection).futureValue
  //    super.withFixture(test)
  //  }
  //}

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
