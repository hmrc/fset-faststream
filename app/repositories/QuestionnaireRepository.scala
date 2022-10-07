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

import model.persisted.{QuestionnaireAnswer, QuestionnaireQuestion}
import model.report.QuestionnaireReportItem
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.{IndexModel, IndexOptions, Projections, UpdateOptions}
import play.api.libs.json._
import services.reporting.SocioEconomicScoreCalculator
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.Try

trait QuestionnaireRepository {
  def addQuestions(applicationId: String, questions: List[QuestionnaireQuestion]): Future[Unit]
  def findQuestions(applicationId: String): Future[Map[String, QuestionnaireAnswer]]
  def findForOnlineTestPassMarkReport(applicationIds: Seq[String]): Future[Map[String, QuestionnaireReportItem]]
  def findAllForDiversityReport: Future[Map[String, QuestionnaireReportItem]]
  def findQuestionsByIds(applicationIds: Seq[String]): Future[Map[String, QuestionnaireReportItem]]
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
                                              mongoComponent: MongoComponent)
  extends PlayMongoRepository[QuestionnaireAnswer](
    collectionName = CollectionNames.QUESTIONNAIRE,
    mongoComponent = mongoComponent,
    domainFormat = QuestionnaireAnswer.answerFormats,
    indexes = Seq(
      IndexModel(ascending("applicationId"), IndexOptions().unique(true))
    )
  ) with QuestionnaireRepository with ReactiveRepositoryHelpers with BaseBSONReader {

  override def addQuestions(applicationId: String, questions: List[QuestionnaireQuestion]): Future[Unit] = {
    val appId = "applicationId" -> applicationId

    val validator = singleUpsertValidator(applicationId, actionDesc = "adding questions")

    val update = questions.map(q => Document(s"questions.${q.question}" -> q.answer.toBson)).foldLeft(Document(appId))((d, v) => d ++ v)

    collection.updateOne(
      Document(appId),
      Document("$set" -> update),
      UpdateOptions().upsert(insertIfNoRecordFound)
    ).toFuture() map validator
  }

  override def findQuestions(applicationId: String): Future[Map[String, QuestionnaireAnswer]] = {
    find(applicationId).map { questions =>
      (for {
        q <- questions
      } yield {
        q.question -> q.answer
      }).toMap[String, QuestionnaireAnswer]
    }
  }

  override def findForOnlineTestPassMarkReport(applicationIds: Seq[String]): Future[Map[String, QuestionnaireReportItem]] = {
    // We need to ensure that the candidates have completed the last page of the questionnaire
    // however, only the first question on the employment page is mandatory, as if the answer is
    // unemployed, they don't need to answer other questions
    val query = Document("applicationId" -> Document("$in" -> applicationIds)) ++
    Document(s"questions.$EmploymentStatusQuestionText" -> Document("$exists" -> true))

    findAllAsReportItem(query)
  }

  override def findAllForDiversityReport: Future[Map[String, QuestionnaireReportItem]] = {
    findAllAsReportItem(Document.empty)
  }

  override def findQuestionsByIds(applicationIds: Seq[String]): Future[Map[String, QuestionnaireReportItem]] = {
    val query = Document("applicationId" -> Document("$in" -> applicationIds))
    findAllAsReportItem(query)
  }

  protected def findAllAsReportItem(query: Document): Future[Map[String, QuestionnaireReportItem]] = {
    val queryResult = collection.find[BsonDocument](query).toFuture() map ( _.map ( doc =>  docToReport(doc) ))
    queryResult.map(_.toMap)
  }

  // This record is only created after submitting Page 4: Before you continue Diversity questions
  override def removeQuestions(applicationId: String): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)
    collection.deleteOne(query).toFuture().map(_ => ())
  }

  private[repositories] def find(applicationId: String): Future[List[QuestionnaireQuestion]] = {
    val query = Document("applicationId" -> applicationId)
    val projection = Projections.include("questions")

    case class Questions(questions: Map[String, QuestionnaireAnswer])

    implicit object SearchFormat extends Format[Questions] {
      def reads(json: JsValue): JsResult[Questions] = JsSuccess(Questions(
        (json \ "questions").as[Map[String, QuestionnaireAnswer]]
      ))

      def writes(s: Questions): JsValue = ???
    }

    collection.find[BsonDocument](query).projection(projection).headOption().map {
      case Some(q) => Codecs.fromBson[Questions](q).questions.map { case (question, answer) => QuestionnaireQuestion(question, answer) }.toList
      case None => List()
    }
  }

  //scalastyle:off method.length
  private def docToReport(document: Document): (String, QuestionnaireReportItem) = {

    val questionsDocOpt = document.get("questions").map(_.asDocument())

    def getAnswer(question: String): Option[String] = {
      val questionDocOpt = Try(questionsDocOpt.map(_.get(question).asDocument())).toOption.flatten
      Try(questionDocOpt.map(_.get("answer").asString().getValue)).toOption.flatten
      .orElse {
        Try(questionDocOpt.map(_.get("unknown").asBoolean().getValue)).toOption.flatten
          .map(unknown => if (unknown) { DontKnowAnswerText } else { "" })
      }
    }

    val applicationId = document.get("applicationId").get.asString().getValue
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

    import scala.collection.JavaConverters._
    val qAndA = questionsDocOpt.map( _.keySet().asScala.toList).map{ _.map { question =>
      val answer = getAnswer(question).getOrElse(UnknownAnswerText)
      question -> answer
    }.toMap}.getOrElse(Map.empty[String, String])

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
