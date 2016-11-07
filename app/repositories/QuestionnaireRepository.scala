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

import model.PersistedObjects
import model.PersistedObjects.{ PersistedAnswer, PersistedQuestion }
import model.report.QuestionnaireReportItem
import play.api.libs.json._
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.{ DB, ReadPreference }
import reactivemongo.bson.Producer.nameValue2Producer
import reactivemongo.bson._
import services.reporting.SocioEconomicScoreCalculator
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.postfixOps

trait QuestionnaireRepository {
  def addQuestions(applicationId: String, questions: List[PersistedQuestion]): Future[Unit]
  def findQuestions(applicationId: String): Future[Map[String, String]]
  def findForOnlineTestPassMarkReport: Future[Map[String, QuestionnaireReportItem]]
  def findAllForDiversityReport: Future[Map[String, QuestionnaireReportItem]]
}

class QuestionnaireMongoRepository(socioEconomicCalculator: SocioEconomicScoreCalculator)(implicit mongo: () => DB)
  extends ReactiveRepository[PersistedAnswer, BSONObjectID]("questionnaire", mongo,
    PersistedObjects.Implicits.answerFormats, ReactiveMongoFormats.objectIdFormats) with QuestionnaireRepository {

  // Use the BSON collection instead of in the inbuilt JSONCollection when performance matters
  lazy val bsonCollection = mongo().collection[BSONCollection](this.collection.name)

  override def addQuestions(applicationId: String, questions: List[PersistedQuestion]): Future[Unit] = {

    val appId = "applicationId" -> applicationId

    collection.update(
      BSONDocument(appId),
      BSONDocument("$set" -> questions.map(q => s"questions.${q.question}" -> q.answer).foldLeft(document ++ appId)((d, v) => d ++ v)),
      upsert = true
    ) map {
        case _ => ()
      }
  }

  override def findQuestions(applicationId: String): Future[Map[String, String]] = {
    find(applicationId).map { questions =>
      (for {
        q <- questions
      } yield {
        val answer = q.answer.answer.getOrElse("")
        q.question -> answer
      }).toMap[String, String]
    }
  }

  override def findForOnlineTestPassMarkReport: Future[Map[String, QuestionnaireReportItem]] = {
    // We need to ensure that the candidates have completed the last page of the questionnaire
    // however, only the first question on the employment page is mandatory, as if the answer is
    // unemployed, they don't need to answer other questions
    val firstEmploymentQuestion = "When you were 14, what kind of work did your highest-earning parent or guardian do?"
    val query = BSONDocument(s"questions.$firstEmploymentQuestion" -> BSONDocument("$exists" -> BSONBoolean(true)))
    findAllAsReportItem(query)
  }

  override def findAllForDiversityReport: Future[Map[String, QuestionnaireReportItem]] = {
    findAllAsReportItem(BSONDocument())
  }

  protected def findAllAsReportItem(query: BSONDocument): Future[Map[String, QuestionnaireReportItem]] = {
    implicit val reader = bsonReader(docToReport)
    val queryResult = bsonCollection.find(query)
      .cursor[(String, QuestionnaireReportItem)](ReadPreference.nearest).collect[List]()
    queryResult.map(_.toMap)
  }

  private[repositories] def find(applicationId: String): Future[List[PersistedQuestion]] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val projection = BSONDocument("questions" -> 1, "_id" -> 0)

    case class Questions(questions: Map[String, PersistedAnswer])

    implicit object SearchFormat extends Format[Questions] {
      def reads(json: JsValue): JsResult[Questions] = JsSuccess(Questions(
        (json \ "questions").as[Map[String, PersistedAnswer]]
      ))

      def writes(s: Questions): JsValue = ???
    }

    collection.find(query, projection).one[Questions].map {
      case Some(q) => q.questions.map((q: (String, PersistedAnswer)) => PersistedQuestion(q._1, q._2)).toList
      case None => List()
    }
  }

  private def bsonReader[T](f: BSONDocument => T): BSONDocumentReader[T] = {
    new BSONDocumentReader[T] {
      def read(bson: BSONDocument) = f(bson)
    }
  }

  private def docToReport(document: BSONDocument): (String, QuestionnaireReportItem) = {
    val questionsDoc = document.getAs[BSONDocument]("questions")

    def getAnswer(question: String): Option[String] = {
      val questionDoc = questionsDoc.flatMap(_.getAs[BSONDocument](question))
      questionDoc.flatMap(_.getAs[String]("answer")).orElse(
        questionDoc.flatMap(_.getAs[Boolean]("unknown")).map { unknown => if (unknown) { "I don't know/prefer not to say"} else {""}})
    }
    val applicationId = document.getAs[String]("applicationId").get
    val gender = getAnswer("What is your gender identity?")
    val sexualOrientation = getAnswer("What is your sexual orientation?")
    val ethnicity = getAnswer("What is your ethnic group?")

    val university = getAnswer("What is the name of the university you received your degree from?")

    val employmentStatus = getAnswer("When you were 14, what kind of work did your highest-earning parent or guardian do?")
    val isEmployed = employmentStatus.exists (s => !s.startsWith("Unemployed") && !s.startsWith("Unknown"))

    val parentEmploymentStatus = if (isEmployed) Some("Employed") else employmentStatus
    val parentOccupation = if (isEmployed) employmentStatus else None

    val parentEmployedOrSelf = getAnswer("Did they work as an employee or were they self-employed?")
    val parentCompanySize = getAnswer("Which size would best describe their place of work?")

    val qAndA = questionsDoc.toList.flatMap(_.elements).map {
      case (question, _) =>
        val answer = getAnswer(question).getOrElse("Unknown")
        (question, answer)
    }.toMap

    val socioEconomicScore = employmentStatus.map(_ => socioEconomicCalculator.calculate(qAndA)).getOrElse("")

    (applicationId, QuestionnaireReportItem(
      gender,
      sexualOrientation,
      ethnicity,
      parentEmploymentStatus,
      parentOccupation,
      parentEmployedOrSelf,
      parentCompanySize,
      socioEconomicScore,
      university
    ))
  }
}
