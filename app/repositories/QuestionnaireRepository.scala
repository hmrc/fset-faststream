/*
 * Copyright 2022 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import model.persisted.{QuestionnaireAnswer, QuestionnaireQuestion}
import model.report.QuestionnaireReportItem
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Ascending
import reactivemongo.api.{Cursor, ReadPreference}
import reactivemongo.bson._
import reactivemongo.play.json.ImplicitBSONHandlers._
import services.reporting.SocioEconomicScoreCalculator
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.postfixOps

trait QuestionnaireRepository {
  def addQuestions(applicationId: String, questions: List[QuestionnaireQuestion]): Future[Unit]
  def findQuestions(applicationId: String): Future[Map[String, QuestionnaireAnswer]]
  def findForOnlineTestPassMarkReport(applicationIds: List[String]): Future[Map[String, QuestionnaireReportItem]]
  def findAllForDiversityReport: Future[Map[String, QuestionnaireReportItem]]
  def findQuestionsByIds(applicationIds: List[String]): Future[Map[String, QuestionnaireReportItem]]
  def removeQuestions(applicationId: String): Future[Unit]

  val GenderQuestionText = "What is your gender identity?"
  val SexualOrientationQuestionText = "What is your sexual orientation?"
  val EthnicityQuestionText = "What is your ethnic group?"
  val EnglishLanguageQuestionText = "Is English your first language?"
  val UniversityQuestionText = "What is the name of the university you received your degree from?"
  val socioEconomicQuestionText = "Do you consider yourself to come from a lower socio-economic background?"
  val EmploymentStatusQuestionText = "When you were 14, what kind of work did your highest-earning parent or guardian do?"
  val ParentEmployedOrSelfEmployedQuestionText = "Did they work as an employee or were they self-employed?"
  val ParentCompanySizeQuestionText = "Which size would best describe their place of work?"

  val DontKnowAnswerText = "I don't know/prefer not to say"
  val EmployedAnswerText = "Employed"
  val UnemployedAnswerText = "Unemployed"
  val UnknownAnswerText = "Unknown"
}

@Singleton
class QuestionnaireMongoRepository @Inject() (socioEconomicCalculator: SocioEconomicScoreCalculator,
                                              mongoComponent: ReactiveMongoComponent)
  extends ReactiveRepository[QuestionnaireAnswer, BSONObjectID](
    CollectionNames.QUESTIONNAIRE,
    mongoComponent.mongoConnector.db,
    QuestionnaireAnswer.answerFormats,
    ReactiveMongoFormats.objectIdFormats) with QuestionnaireRepository
    with ReactiveRepositoryHelpers with BaseBSONReader {

  override def indexes: Seq[Index] = Seq(
    Index(Seq(("applicationId", Ascending)), unique = true)
  )

  override def addQuestions(applicationId: String, questions: List[QuestionnaireQuestion]): Future[Unit] = {

    val appId = "applicationId" -> applicationId

    val validator = singleUpsertValidator(applicationId, actionDesc = "adding questions")

    collection.update(ordered = false).one(
      BSONDocument(appId),
      BSONDocument("$set" -> questions.map(q => s"questions.${q.question}" -> q.answer).foldLeft(document ++ appId)((d, v) => d ++ v)),
      upsert = true
    ) map validator
  }

  override def findQuestions(applicationId: String): Future[Map[String, QuestionnaireAnswer]] = {
    find(applicationId).map { questions =>
      (for {
        q <- questions
      } yield {
        val answer = q.answer
        q.question -> answer
      }).toMap[String, QuestionnaireAnswer]
    }
  }

  override def findForOnlineTestPassMarkReport(applicationIds: List[String]): Future[Map[String, QuestionnaireReportItem]] = {
    // We need to ensure that the candidates have completed the last page of the questionnaire
    // however, only the first question on the employment page is mandatory, as if the answer is
    // unemployed, they don't need to answer other questions
    val query =
      BSONDocument(s"questions.$EmploymentStatusQuestionText" -> BSONDocument("$exists" -> BSONBoolean(true))) ++
      BSONDocument("applicationId" -> BSONDocument("$in" -> applicationIds))

    findAllAsReportItem(query)
  }

  override def findAllForDiversityReport: Future[Map[String, QuestionnaireReportItem]] = {
    findAllAsReportItem(BSONDocument.empty)
  }

  override def findQuestionsByIds(applicationIds: List[String]): Future[Map[String, QuestionnaireReportItem]] = {
    val query = BSONDocument("applicationId" -> BSONDocument("$in" -> applicationIds))
    findAllAsReportItem(query)
  }

  protected def findAllAsReportItem(query: BSONDocument): Future[Map[String, QuestionnaireReportItem]] = {
    implicit val reader = bsonReader(docToReport)
    val queryResult = bsonCollection.find(query, projection = Option.empty[JsObject])
      .cursor[(String, QuestionnaireReportItem)](ReadPreference.nearest)
      .collect[List](maxDocs = -1, Cursor.FailOnError[List[(String, QuestionnaireReportItem)]]())
    queryResult.map(_.toMap)
  }

  override def removeQuestions(applicationId: String): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    collection.delete().one(query, limit = Some(1)).map(_ => ())
  }

  private[repositories] def find(applicationId: String): Future[List[QuestionnaireQuestion]] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val projection = BSONDocument("questions" -> 1, "_id" -> 0)

    case class Questions(questions: Map[String, QuestionnaireAnswer])

    implicit object SearchFormat extends Format[Questions] {
      def reads(json: JsValue): JsResult[Questions] = JsSuccess(Questions(
        (json \ "questions").as[Map[String, QuestionnaireAnswer]]
      ))

      def writes(s: Questions): JsValue = ???
    }

    collection.find(query, Some(projection)).one[Questions].map {
      case Some(q) => q.questions.map((q: (String, QuestionnaireAnswer)) => QuestionnaireQuestion(q._1, q._2)).toList
      case None => List()
    }
  }

  //scalastyle:off method.length
  private def docToReport(document: BSONDocument): (String, QuestionnaireReportItem) = {
    val questionsDoc = document.getAs[BSONDocument]("questions")

    def getAnswer(question: String): Option[String] = {
      val questionDoc = questionsDoc.flatMap(_.getAs[BSONDocument](question))
      questionDoc.flatMap(_.getAs[String]("answer")).orElse(
        questionDoc.flatMap(_.getAs[Boolean]("unknown")).map { unknown => if (unknown) { DontKnowAnswerText } else {""}})
    }

    val applicationId = document.getAs[String]("applicationId").get
    val gender = getAnswer(GenderQuestionText)
    val sexualOrientation = getAnswer(SexualOrientationQuestionText)
    val ethnicity = getAnswer(EthnicityQuestionText)
    val englishLanguage = getAnswer(EnglishLanguageQuestionText)

    val university = getAnswer(UniversityQuestionText)

    val socioEconomic = getAnswer(socioEconomicQuestionText)

    val employmentStatus = getAnswer(EmploymentStatusQuestionText)
    val isEmployed = employmentStatus.exists (s => !s.startsWith(UnemployedAnswerText) && !s.startsWith(UnknownAnswerText))

    val parentEmploymentStatus = if (isEmployed) Some(EmployedAnswerText) else employmentStatus
    val parentOccupation = if (isEmployed) employmentStatus else None

    val parentEmployedOrSelf = getAnswer(ParentEmployedOrSelfEmployedQuestionText)
    val parentCompanySize = getAnswer(ParentCompanySizeQuestionText)

    //TODO: Ian mongo 3.2 -> 3.4
    val qAndA = questionsDoc.toList.flatMap(_.elements).map { bsonElement =>
      val question = bsonElement.name
      val answer = getAnswer(question).getOrElse(UnknownAnswerText)
      question -> answer
    }.toMap

    val socioEconomicScore = employmentStatus.map(_ => socioEconomicCalculator.calculate(qAndA)).getOrElse("")

    applicationId -> QuestionnaireReportItem(
      gender,
      sexualOrientation,
      ethnicity,
      englishLanguage,
      parentEmploymentStatus,
      parentOccupation,
      parentEmployedOrSelf,
      parentCompanySize,
      socioEconomic,
      socioEconomicScore,
      university
    )
  }
}
