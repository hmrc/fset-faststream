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
import config.MicroserviceAppConfig
import org.joda.time.DateTime
import org.joda.time.Seconds._
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{ Millis, Span }
import org.scalatestplus.play.{ OneAppPerTest, PlaySpec }
import play.api.{ Application, Play }
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.modules.reactivemongo.{ MongoDbConnection, ReactiveMongoComponent }
import reactivemongo.api.DefaultDB
import reactivemongo.api.indexes.IndexType
import reactivemongo.bson.BSONDocument
import reactivemongo.play.json.ImplicitBSONHandlers
import reactivemongo.play.json.collection.JSONCollection
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.play.test.WithFakeApplication

import scala.concurrent.{ Await, ExecutionContext }
import scala.concurrent.duration._
import scala.language.postfixOps

abstract class MongoRepositorySpec extends PlaySpec with MockitoSugar with Inside with ScalaFutures with IndexesReader
  with BeforeAndAfterEach with BeforeAndAfterAll {
  import ImplicitBSONHandlers._

  val timeout: FiniteDuration = 60 seconds
  val collectionName: String
  val additionalCollections: List[String] = Nil
  val unit: Unit = ()
  val AppId = "AppId"
  val UserId = "UserId"
  val TestAccountId = "TestAccountId"

  val FrameworkId = "FrameworkId"

  val timesApproximatelyEqual: (DateTime, DateTime) => Boolean = (time1: DateTime, time2: DateTime) => secondsBetween(time1, time2)
    .isLessThan(seconds(5))

  implicit final val app: Application = new GuiceApplicationBuilder()
    .disable[PlayModule]
    .build

  override implicit def patienceConfig = PatienceConfig(timeout = scaled(Span(timeout.toMillis, Millis)))

  implicit val context: ExecutionContext = play.api.libs.concurrent.Execution.Implicits.defaultContext

  lazy val mongo: ReactiveMongoComponent = app.injector.instanceOf(classOf[ReactiveMongoComponent])

  lazy val appConfig: MicroserviceAppConfig = app.injector.instanceOf(classOf[MicroserviceAppConfig])

  override def beforeAll(): Unit = {
    Play.start(app)
  }

  override def afterAll(): Unit = {
    Play.stop(app)
  }

  override def beforeEach(): Unit = {
    val collection = mongo.mongoConnector.db().collection[JSONCollection](collectionName)
    Await.ready(collection.delete.one(Json.obj()), timeout)
  }
}

trait IndexesReader {
  this: ScalaFutures =>

  def indexesWithFields(repo: ReactiveRepository[_, _])(implicit ec: ExecutionContext): List[IndexDetails] = {
    val indexesManager = repo.collection.indexesManager
    val indexes = indexesManager.list().futureValue
    indexes.map( index => IndexDetails(index.key, index.unique) )
  }

  case class IndexDetails(key: Seq[(String, IndexType)], unique: Boolean)
}
