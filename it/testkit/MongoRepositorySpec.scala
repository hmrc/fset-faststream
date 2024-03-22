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

import org.apache.pekko.stream.Materializer
import config.MicroserviceAppConfig
import org.mongodb.scala.Document
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Span}
import org.scalatestplus.play.PlaySpec
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, Logging, Play}
import repositories.CollectionNames
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps

abstract class MongoRepositorySpec extends PlaySpec with MockitoSugar with Inside with ScalaFutures with IndexesReader
  with BeforeAndAfterEach with BeforeAndAfterAll with Logging {

  val timeout: FiniteDuration = 60 seconds
  val collectionName: String
  val additionalCollections: List[String] = Nil
  val unit: Unit = ()
  val AppId = "AppId"
  val UserId = "UserId"
  val TestAccountId = "TestAccountId"

  val FrameworkId = "FrameworkId"

  val timesApproximatelyEqual: (OffsetDateTime, OffsetDateTime) => Boolean = (time1: OffsetDateTime, time2: OffsetDateTime) =>
    ChronoUnit.SECONDS.between(time1, time2) < 5

  implicit final val app: Application = new GuiceApplicationBuilder().build()

  override implicit def patienceConfig = PatienceConfig(timeout = scaled(Span(timeout.toMillis, Millis)))

  implicit lazy val executionContext = app.injector.instanceOf[ExecutionContext]

  implicit lazy val materializer = app.injector.instanceOf[Materializer]

  lazy val mongo: MongoComponent = app.injector.instanceOf(classOf[MongoComponent])

  lazy val appConfig: MicroserviceAppConfig = app.injector.instanceOf(classOf[MicroserviceAppConfig])

  override def beforeAll(): Unit = {
    Play.start(app)
  }

  override def afterAll(): Unit = {
    Play.stop(app)
  }

  override def beforeEach(): Unit = {
    val collection = mongo.database.getCollection(collectionName)
    collectionName match {
      case CollectionNames.FILE_UPLOAD =>
        val chunksCollection = mongo.database.getCollection(s"$collectionName.chunks")
        val filesCollection = mongo.database.getCollection(s"$collectionName.files")
        Await.ready(chunksCollection.deleteMany(Document.empty).toFuture(), timeout)
        Await.ready(filesCollection.deleteMany(Document.empty).toFuture(), timeout)
      case _ => Await.ready(collection.deleteMany(Document.empty).toFuture(), timeout)
    }
  }
}

trait IndexesReader {
  this: ScalaFutures =>

  def indexDetails(repo: PlayMongoRepository[_])(implicit ec: ExecutionContext): Future[Seq[IndexDetails]] = {
    import scala.jdk.CollectionConverters._

    repo.collection.listIndexes().toFuture().map { _.map {
      doc =>
        val name = doc("name").asString().getValue
        val indexKeys = doc("key").asDocument().keySet().asScala.toSeq

        val mappedIndexKeys = indexKeys.map{ key =>
          val indexType = doc("key").asDocument().getInt32(key).getValue match {
            case 1 => "Ascending"
            case -1 => "Descending"
            case _ => "Undefined"
          }
          key -> indexType
        }

        val isUnique = doc.getOrElse("unique", false).asBoolean().getValue
        IndexDetails(name, mappedIndexKeys, isUnique)
      }
    }
  }

  case class IndexDetails(name: String, keys: Seq[(String, String)], unique: Boolean)
}
