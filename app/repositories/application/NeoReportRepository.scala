/*
 * Copyright 2023 HM Revenue & Customs
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

import config.{MicroserviceAppConfig, PsiTestIds}
import connectors.launchpadgateway.exchangeobjects.in.reviewed.*
import factories.DateTimeFactory
import model.*
import model.ApplicationRoute.ApplicationRoute
import model.ApplicationStatus.ApplicationStatus
import model.EvaluationResults.Withdrawn
import model.assessmentscores.{AdaptsScores, RelatesScores, StrivesScores, ThinksScores}
import model.command.{CandidateDetailsReportItem, CsvExtract, WithdrawApplication}
import model.persisted.fsb.ScoresAndFeedback
import model.persisted.{FSACIndicator, SchemeEvaluationResult}
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.bson.{BsonArray, BsonDocument, BsonRegularExpression}
import org.mongodb.scala.model.Projections
import org.mongodb.scala.{MongoCollection, ObservableFuture, SingleObservableFuture, bsonDocumentToDocument}
import play.api.Logging
import repositories.*
import services.reporting.SocioEconomicCalculator
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.Codecs

import java.time.{OffsetDateTime, ZoneOffset}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait NeoReportRepository extends Schemes {

  implicit class RichOptionDocument(doc: Option[BsonDocument]) {
    def getStringOpt(key: String) = Try(doc.map(_.get(key).asString().getValue)).toOption.flatten
    def getBooleanOpt(key: String) = Try(doc.map(_.get(key).asBoolean().getValue.toString)).toOption.flatten
    def getDoubleOpt(key: String) = Try(doc.map(_.get(key).asDouble().getValue.toString)).toOption.flatten
    def getIntOpt(key: String) = Try(doc.map(_.get(key).asInt32().getValue.toString)).toOption.flatten
    import repositories.formats.MongoJavatimeFormats.Implicits.jtOffsetDateTimeFormat // Needed for ISODate
    def getDateTimeOpt(key: String) =
      doc.flatMap ( doc => Try(Codecs.fromBson[OffsetDateTime](doc.get(key).asDateTime()).toString).toOption )
    def getAsBoolean(key: String) = doc.exists(ad => Try(ad.get(key).asBoolean().getValue).getOrElse(false))
    def getAsIntOpt(key: String) = Try(doc.map(_.get(key).asInt32().getValue)).toOption.flatten
  }

  val fsacCompetencyHeaders =
    "Written advice exercise,Stakeholder communication exercise,Personal development conversation,overallScore,"

  val disabilityCategoriesHeaders: String = "Learning difference,Social/communication conditions,Long-term illness,Mental health condition," +
  "Physical impairment,Deaf,Blind,Development condition,No known impairment,Condition not listed,Prefer not to say,Other,"

  def testTitles(testName: String) = s"${testName}inventoryId,${testName}orderId,${testName}tScore,${testName}rawScore,"

  val assistanceDetailsHeaders: String = "Do you have a disability," +
    disabilityCategoriesHeaders

  val applicationDetailsHeader: String = "applicationId,testAccountId,Submitted,Cur 1st Pref,First name,Last name," +
    "Preferred Name,Date of Birth," +
    assistanceDetailsHeaders +
    testTitles("Phase1test1") +
    testTitles("Phase1test2") +
    fsacCompetencyHeaders +
    "NI number,Civil servant department"

  val contactDetailsHeader = "Email,Address line1,Address line2,Address line3,Address line4,Postcode,Country,Phone"

  val questionnaireDetailsHeader: String = "Sex at Birth,Sexual Orientation,Ethnic Group," +
    "Eligible for free school meals?," +
    "Lower socio-economic background?," +
    "Parent guardian completed Uni?,Parents job at 14,Employee?,Size,Supervise employees"

  val mediaHeader = "How did you hear about us?"

  val siftAnswersHeader: String = "nationality,secondNationality"

  val assessmentScoresExerciseAverageFields: Seq[(String, String)] = Seq(
    "exercise1" -> "overallAverage",
    "exercise2" -> "overallAverage",
    "exercise3" -> "overallAverage"
  )

  val assessmentScoresExerciseAverageFieldsMap: Map[String, String] = assessmentScoresExerciseAverageFields.toMap

  val fsacBarScoresHeadersMap: Map[String, String] = Map(
    "exercise1" ->
      (RelatesScores.exercise1Headers ++
        ThinksScores.exercise1Headers ++
        StrivesScores.exercise1Headers
        ).mkString(","),
    "exercise2" ->
      (RelatesScores.exercise2Headers ++
      ThinksScores.exercise2Headers ++
      StrivesScores.exercise2Headers ++
      AdaptsScores.exercise2Headers
        ).mkString(","),
    "exercise3" ->
      (RelatesScores.exercise3Headers ++
        StrivesScores.exercise3Headers ++
        AdaptsScores.exercise3Headers
        ).mkString(",")
  )

  def assessmentScoresHeaders: String = {
    assessmentScoresExerciseAverageFields.map(_._1).map { exercise =>
      s"$exercise - ${assessmentScoresExerciseAverageFieldsMap(exercise)},${fsacBarScoresHeadersMap(exercise)}"
    }.mkString(",")
  }

  def findApplicationsFor(appRoutes: Seq[ApplicationRoute], appStatuses: Seq[ApplicationStatus]): Future[Seq[CandidateIds]]

  def applicationDetailsStream(applicationIds: Seq[String])(implicit mat: Materializer): Source[CandidateDetailsReportItem, _]

  def findContactDetails(userIds: Seq[String]): Future[CsvExtract[String]]

  def findQuestionnaireDetails(applicationIds: Seq[String]): Future[CsvExtract[String]]

  def findMediaDetails(userIds: Seq[String]): Future[CsvExtract[String]]

  def findSiftAnswers(applicationIds: Seq[String]): Future[CsvExtract[String]]

  def findReviewerAssessmentScores(applicationIds: Seq[String]): Future[CsvExtract[String]]
}

@Singleton
class NeoReportMongoRepository @Inject() (val dateTimeFactory: DateTimeFactory,
                                          appConfig: MicroserviceAppConfig,
                                          schemeRepository: SchemeRepository,
                                          mongoComponent: MongoComponent)(implicit ec: ExecutionContext)
  extends NeoReportRepository with CommonBSONDocuments with DiversityQuestionsText with Logging with Schemes {

  val applicationDetailsCollection = mongoComponent.database.getCollection(CollectionNames.APPLICATION)
  val contactDetailsCollection = mongoComponent.database.getCollection(CollectionNames.CONTACT_DETAILS)
  val questionnaireCollection = mongoComponent.database.getCollection(CollectionNames.QUESTIONNAIRE)
  val mediaCollection = mongoComponent.database.getCollection(CollectionNames.MEDIA)
  val assessorAssessmentScoresCollection = mongoComponent.database.getCollection(CollectionNames.ASSESSOR_ASSESSMENT_SCORES)
  val reviewerAssessmentScoresCollection = mongoComponent.database.getCollection(CollectionNames.REVIEWER_ASSESSMENT_SCORES)
  val candidateAllocationCollection = mongoComponent.database.getCollection(CollectionNames.CANDIDATE_ALLOCATION)
  val eventCollection = mongoComponent.database.getCollection(CollectionNames.ASSESSMENT_EVENTS)
  val siftAnswersCollection = mongoComponent.database.getCollection(CollectionNames.SIFT_ANSWERS)

  private val Y = Some("Yes")
  private val N = Some("No")

  override def applicationDetailsStream(applicationIds: Seq[String])(implicit mat: Materializer): Source[CandidateDetailsReportItem, _] = {

    def processDocument(doc: Document): CandidateDetailsReportItem = {
      try {
        val applicationId = extractAppIdOpt(doc).get
        logger.debug(s"processing applicationId = $applicationId")

        val onlineTestResults: Map[String, List[Option[String]]] = onlineTests(doc)

        val csvContent = makeRow(
          List(extractAppIdOpt(doc)) :::
            List(doc.get("testAccountId").map(_.asString().getValue)) :::
            progressStatusTimestamp(doc, ProgressStatuses.SUBMITTED) :::
            List(currentFirstPreference(doc).map(_.schemeId.value)) :::
            personalDetails(doc, isAnalystReport = false) :::
            assistanceDetails(doc) :::
            onlineTestResults(Tests.Phase1Test1.toString) :::
            onlineTestResults(Tests.Phase1Test2.toString) :::
            fsacExerciseAverages(doc) :::
            onboardingQuestions(doc) :::
            List(repositories.getCivilServiceExperienceDetailsForNeo(doc))
            : _*
        )
        CandidateDetailsReportItem(
          extractAppId(doc),
          extractUserId(doc), csvContent
        )
      } catch {
        case ex: Throwable =>
          val appId = extractAppId(doc)
          logger.error(s"Streamed neo candidate report generation exception $appId", ex)
          CandidateDetailsReportItem(appId, "", s"$appId ERROR: ${ex.getMessage}")
      }
    }

    val query = Document("applicationId" -> Document("$in" -> applicationIds))
    val projection = Projections.excludeId()
    Source.fromPublisher {
      applicationDetailsCollection.find(query).projection(projection)
        .transform((doc: Document) => processDocument(doc),
          (e: Throwable) => throw e
        )
    }
  }

  private def progressStatusTimestamp(doc: Document, progressStatus: ProgressStatuses.ProgressStatus): List[Option[String]] = {

    def timestampFor(status: ProgressStatuses.ProgressStatus) = {
      import repositories.formats.MongoJavatimeFormats.Implicits.jtOffsetDateTimeFormat // Needed for ISODate
      val statusTimestampsOpt = subDocRoot("progress-status-timestamp")(doc)
      statusTimestampsOpt.flatMap(doc =>
        Try(Codecs.fromBson[OffsetDateTime](doc.get(status.toString).asDateTime())).toOption.map( _.toString ) // Handle NPE
      )
    }

    List(timestampFor(progressStatus))
  }

  override def findApplicationsFor(appRoutes: Seq[ApplicationRoute],
                                   appStatuses: Seq[ApplicationStatus]): Future[Seq[CandidateIds]] = {
    val projection = Projections.include("applicationId", "userId")
    val query = Document( "$and" -> BsonArray(
      Document("applicationRoute" -> Document("$in" -> appRoutes.map(_.toBson))),
      Document("applicationStatus" -> Document("$in" -> appStatuses.map(_.toBson)))
    ))

    applicationDetailsCollection.find[Document](query).projection(projection).toFuture().map { docList =>
      docList.map { doc =>
        val appId = getAppId(doc)
        val userId = getUserId(doc)
        CandidateIds(userId, appId)
      }
    }
  }

  override def findContactDetails(userIds: Seq[String]): Future[CsvExtract[String]] = {
    val projection = Projections.excludeId()
    val query = Document("userId" -> Document("$in" -> userIds))

    contactDetailsCollection.find(query).projection(projection).toFuture().map { docs =>
      val csvRecords = docs.map { doc =>
        val contactDetailsOpt = subDocRoot("contact-details")(doc)
        val addressOpt = contactDetailsOpt.flatMap(contactDetails => subDocRoot("address")(contactDetails))

        val csvRecord = makeRow(
          contactDetailsOpt.getStringOpt("email"),
          addressOpt.getStringOpt("line1"),
          addressOpt.getStringOpt("line2"),
          addressOpt.getStringOpt("line3"),
          addressOpt.getStringOpt("line4"),
          contactDetailsOpt.getStringOpt("postCode"),
          contactDetailsOpt.getStringOpt("country"),
          contactDetailsOpt.getStringOpt("phone")
        )
        extractUserId(doc) -> csvRecord
      }
      CsvExtract(contactDetailsHeader, csvRecords.toMap)
    }
  }

  override def findQuestionnaireDetails(applicationIds: Seq[String]): Future[CsvExtract[String]] = {
    val query = Document("applicationId" -> Document("$in" -> applicationIds))
    val projection = Projections.excludeId()

    def getAnswer(question: String, doc: Option[BsonDocument]) = {
      val questionDocOpt = doc.flatMap(doc => subDocRoot(question)(doc))
      val isUnknown = questionDocOpt.exists(q => Try(q.get("unknown").asBoolean().getValue).toOption.contains(true))
      if (isUnknown) {
        Some("Unknown")
      } else {
        questionDocOpt.flatMap(q => Try(q.get("answer").asString().getValue).toOption match {
          case None => Try(q.get("otherDetails").asString().getValue).toOption
          case Some(answer) if List("Other", "Other ethnic group").contains(answer) => Try(q.get("otherDetails").asString().getValue).toOption
          case Some(answer) => Some(answer)
        })
      }
    }

    questionnaireCollection.find(query).projection(projection).toFuture().map { docs =>

      val csvRecords = docs.map { doc =>
        val questionsDocOpt = subDocRoot("questions")(doc)

        val csvRecord = makeRow(
          getAnswer(sex, questionsDocOpt),
          getAnswer(sexualOrientation, questionsDocOpt),
          getAnswer(ethnicGroup, questionsDocOpt),
          getAnswer(eligibleForFreeSchoolMeals, questionsDocOpt),
          getAnswer(lowerSocioEconomicBackground, questionsDocOpt),
          getAnswer(parentOrGuardianQualificationsAtAge18, questionsDocOpt),
          getAnswer(highestEarningParentOrGuardianTypeOfWorkAtAge14, questionsDocOpt),
          getAnswer(employeeOrSelfEmployed, questionsDocOpt),
          getAnswer(sizeOfPlaceOfWork, questionsDocOpt),
          getAnswer(superviseEmployees, questionsDocOpt)
        )
        extractAppId(doc) -> csvRecord
      }
      CsvExtract(questionnaireDetailsHeader, csvRecords.toMap)
    }
  }

  override def findSiftAnswers(applicationIds: Seq[String]): Future[CsvExtract[String]] = {
    val query = Document("applicationId" -> Document("$in" -> applicationIds))
    val projection = Projections.excludeId()

    siftAnswersCollection.find(query).projection(projection).toFuture().map { docs =>
      val csvRecords = docs.map { doc =>

        val generalAnswersOpt = subDocRoot("generalAnswers")(doc)

        val csvRecord = makeRow(
          List(
            generalAnswersOpt.getStringOpt("nationality"),
            generalAnswersOpt.getStringOpt("secondNationality")
          ) : _*
        )
        extractAppId(doc) -> csvRecord
      }.toMap
      CsvExtract(siftAnswersHeader, csvRecords)
    }
  }

  override def findMediaDetails(userIds: Seq[String]): Future[CsvExtract[String]] = {
    val query = Document("userId" -> Document("$in" -> userIds))
    val projection = Projections.excludeId()

    commonFindMediaDetails(query, projection)
  }

  private def commonFindMediaDetails(query: Document, projection: Bson) = {
    mediaCollection.find(query).projection(projection).toFuture().map { docs =>
      val csvRecords = docs.map { doc =>
        val csvRecord = makeRow(
          doc.get("media").map(_.asString().getValue)
        )
        extractUserId(doc) -> csvRecord
      }
      CsvExtract(mediaHeader, csvRecords.toMap)
    }
  }

  //scalastyle:off method.length
  override def findReviewerAssessmentScores(applicationIds: Seq[String]): Future[CsvExtract[String]] = {

    val exerciseNames = assessmentScoresExerciseAverageFields.map(_._1)
    val projection = Projections.excludeId()
    val query = Document("applicationId" -> Document("$in" -> applicationIds))

    reviewerAssessmentScoresCollection.find(query).projection(projection).toFuture().map { docs =>
      val csvRecords = docs.map { doc =>
        val csvStr = exerciseNames.flatMap { exerciseName =>
          val exerciseOpt = subDocRoot(exerciseName)(doc)

          val relatesScoresOpt = exerciseOpt.flatMap(doc => subDocRoot("relatesScores")(doc)).map { doc =>
            Codecs.fromBson[RelatesScores](doc)
          }
          val thinksScoresOpt = exerciseOpt.flatMap(doc => subDocRoot("thinksScores")(doc)).map { doc =>
            Codecs.fromBson[ThinksScores](doc)
          }
          val strivesScoresOpt = exerciseOpt.flatMap(doc => subDocRoot("strivesScores")(doc)).map { doc =>
            Codecs.fromBson[StrivesScores](doc)
          }
          val adaptsScoresOpt = exerciseOpt.flatMap(doc => subDocRoot("adaptsScores")(doc)).map { doc =>
            Codecs.fromBson[AdaptsScores](doc)
          }

          val fsacScores = exerciseName match {
            case "exercise1" =>
              List(
                relatesScoresOpt.flatMap(_.b19communicatesEffectively),
                relatesScoresOpt.flatMap(_.b20influencesOthers),
                thinksScoresOpt.flatMap(_.b17thinksAnalytically),
                thinksScoresOpt.flatMap(_.b18widerContext),
                strivesScoresOpt.flatMap(_.b21alignsWithGoals),
                strivesScoresOpt.flatMap(_.b22tacklesRootCause)
              )
            case "exercise2" =>
              List(
                relatesScoresOpt.flatMap(_.b3selfAware),
                relatesScoresOpt.flatMap(_.b4communicatesEffectively),
                thinksScoresOpt.flatMap(_.b1widerContext),
                thinksScoresOpt.flatMap(_.b2patternsAndInterrelationships),
                strivesScoresOpt.flatMap(_.b8strivesToSucceed),
                strivesScoresOpt.flatMap(_.b9goalOriented),
                adaptsScoresOpt.flatMap(_.b5novelApproaches),
                adaptsScoresOpt.flatMap(_.b6openToChange),
                adaptsScoresOpt.flatMap(_.b7learningAgility)
              )
            case "exercise3" =>
              List(
                relatesScoresOpt.flatMap(_.b10selfAwareAndManages),
                relatesScoresOpt.flatMap(_.b11communicatesEffectively),
                strivesScoresOpt.flatMap(_.b15motivation),
                strivesScoresOpt.flatMap(_.b16commitment),
                adaptsScoresOpt.flatMap(_.b12consolidatesLearning),
                adaptsScoresOpt.flatMap(_.b14respondsFlexibily)
              )
          }

          List(
          ) ++
            assessmentScoresExerciseAverageFieldsMap(exerciseName).split(",").map { field =>
              exerciseOpt.getDoubleOpt(field)
            } ++
            fsacScores.map(_.map(_.toString))
        }
        val csvRecord = makeRow(csvStr: _*)
        extractAppId(doc) -> csvRecord
      }
      CsvExtract(assessmentScoresHeaders, csvRecords.toMap)
    }
  }//scalastyle:on

  private def isOxbridge(code: Option[String]): Option[String] = {
    code match {
      case Some("O33-OXF") | Some("C05-CAM") => Y
      case Some(_) => N
      case None => None
    }
  }

  private def isRussellGroup(code: Option[String]): Option[String] = {
    val russellGroupUnis = List(
      "B32-BIRM", "B78-BRISL", "C05-CAM", "C15-CARDF", "D86-DUR", "E56-EDINB", "E84-EXETR", "G28-GLASG", "I50-IMP", "K60-KCL",
      "L23-LEEDS", "L41-LVRPL", "L72-LSE", "M20-MANU", "N21-NEWC", "N84-NOTTM", "O33-OXF", "Q75-QBELF", "S18-SHEFD",
      "S27-SOTON", "Q50-QMUL", "U80-UCL", "W20-WARWK", "Y50-YORK"
    )
    code.flatMap(c => if (russellGroupUnis.contains(c)) Y else N)
  }

  private def fsbScoresAndFeedback(doc: Document): List[Option[String]] = {
    val scoresAndFeedbackOpt = (for {
      fsb <- subDocRoot("testGroups")(doc).map(_.get("FSB"))
    } yield {
      Try(Codecs.fromBson[ScoresAndFeedback](fsb.asDocument().get("scoresAndFeedback"))).toOption
    }).flatten
    scoresAndFeedbackOpt.map { saf =>
      List(Some(saf.overallScore.toString), Some(saf.feedback))
    }.getOrElse( List(None, None) )
  }

  private def getSchemeResults(results: List[BsonDocument]) = results.map {
    result => Try(result.get("schemeId").asString().getValue).getOrElse("") + ": " + Try(result.get("result").asString().getValue).getOrElse("")
  }

  private def currentFirstPreference(doc: Document): Option[SchemeEvaluationResult] = {
    val testEvalResults = doc.get("currentSchemeStatus").map( bsonValue => Codecs.fromBson[List[SchemeEvaluationResult]](bsonValue))
    val currentFirstPref = testEvalResults.map(listSchemeEvalResult =>
      listSchemeEvalResult.filter(_.result == EvaluationResults.Green.toString).head
    )
    currentFirstPref
  }

  private def fsacExerciseAverages(doc: Document): List[Option[String]] = {
    val testGroupsOpt = subDocRoot("testGroups")(doc)
    val testSectionOpt = testGroupsOpt.flatMap( testGroups => subDocRoot("FSAC")(testGroups) )
    val testsEvaluationOpt = testSectionOpt.flatMap( evaluation => subDocRoot("evaluation")(evaluation) )

    val exerciseAvgOpt = testsEvaluationOpt.flatMap( evaluation => subDocRoot("exercise-average")(evaluation))
    List(
      "exercise1Average",
      "exercise2Average",
      "exercise3Average",
      "overallScore"
    ).map(key => exerciseAvgOpt.getDoubleOpt(key) )
  }

  private def extractDateTime(doc: BsonDocument, key: String) = {
    import repositories.formats.MongoJavatimeFormats.Implicits.jtOffsetDateTimeFormat // Needed for ISODate
    Codecs.fromBson[OffsetDateTime](doc.get(key).asDateTime())
  }

  private def onlineTests(doc: Document): Map[String, List[Option[String]]] = {

    def extractDataFromTest(test: Option[BsonDocument]) = {
      val testResults = test.flatMap ( doc => subDocRoot("testResult")(doc) )

      List(
        test.getStringOpt("inventoryId"),
        test.getStringOpt("orderId"),
        testResults.getDoubleOpt("tScore"),
        testResults.getDoubleOpt("rawScore")
      )
    }

    def getInventoryId(config: Map[String, PsiTestIds], testName: String, phase: String) =
      config.getOrElse(testName, throw new Exception(s"No inventoryId found for $phase $testName")).inventoryId

    // Phase1 data
    val phase1TestConfig = appConfig.onlineTestsGatewayConfig.phase1Tests.tests
    val test1InventoryId = getInventoryId(phase1TestConfig, "test1", "phase1")
    val test2InventoryId = getInventoryId(phase1TestConfig, "test2", "phase1")

    val testGroupsOpt = subDocRoot("testGroups")(doc)

    import scala.jdk.CollectionConverters.*
    val phase1Tests = for {
      testGroups <- testGroupsOpt
      phase1Tests <- subDocRoot("PHASE1")(testGroups).map(_.get("tests").asArray())
    } yield phase1Tests.getValues.asScala.toList.map ( _.asDocument() )

    val phase1Test1 = phase1Tests.flatMap( tests =>
      tests.find { test =>
        test.getString("inventoryId").getValue == test1InventoryId && test.getBoolean("usedForResults").getValue
      }
    )
    val phase1Test2 = phase1Tests.flatMap( tests =>
      tests.find { test =>
        test.getString("inventoryId").getValue == test2InventoryId && test.getBoolean("usedForResults").getValue
      }
    )

    Map(
      Tests.Phase1Test1.toString -> extractDataFromTest(phase1Test1),
      Tests.Phase1Test2.toString -> extractDataFromTest(phase1Test2)
    )
  }
  
  object Tests {
    sealed abstract class Test {
      def name: String
    }

    case object Phase1Test1 extends Test {
      override def name = "phase1test1"
    }

    case object Phase1Test2 extends Test {
      override def name = "phase1test2"
    }
  }

  //scalastyle:off
  private def markDisabilityCategories(disabilityCategories: List[String]) = {
    val disabilityCategoriesList = List(
      "Learning difference such as dyslexia, dyspraxia or AD(H)D",
      "Social/communication conditions such as a speech and language impairment or an autistic spectrum condition",
      "Long-term illness or health condition such as cancer, HIV, diabetes, chronic heart disease, or epilepsy",
      "Mental health condition, challenge or disorder, such as depression, schizophrenia or anxiety",
      "Physical impairment (a condition that substantially limits one or more basic physical activities such as walking, climbing stairs, lifting or carrying)",
      "D/deaf or have a hearing impairment",
      "Blind or have a visual impairment uncorrected by glasses",
      "Development condition that you have had since childhood which affects motor, cognitive, social and emotional skills, and speech and language",
      "No known impairment, health condition or learning difference",
      "An impairment, health condition or learning difference not listed above",
      "Prefer Not to Say",
      "Other"
    )
    disabilityCategoriesList.map ( dc => if (disabilityCategories.contains(dc)) Y else N )
  }//scalastyle:on

  private def assistanceDetails(doc: Document): List[Option[String]] = {
    val assistanceDetailsOpt = subDocRoot("assistance-details")(doc)

    import scala.jdk.CollectionConverters.*

    val markedDisabilityCategories = markDisabilityCategories(
      assistanceDetailsOpt.map { ad =>
        if (ad.containsKey("disabilityCategories")) {
          ad.get("disabilityCategories").asArray().getValues.asScala.toList.map(_.asString().getValue)
        } else {
          Nil
        }
      }.getOrElse(Nil)
    )

    List(
      assistanceDetailsOpt.getStringOpt("hasDisability")
    ) ++ markedDisabilityCategories
  }

  private def personalDetails(doc: Document, isAnalystReport: Boolean) = {
    val columns = if (isAnalystReport) { List("dateOfBirth") } else { List("firstName", "lastName","preferredName", "dateOfBirth") }
    val personalDetailsOpt = subDocRoot("personal-details")(doc)
    columns.map ( columnName => personalDetailsOpt.map(_.get(columnName).asString().getValue) )
  }

  private def onboardingQuestions(doc: Document): List[Option[String]] = {
    val columns = List("niNumber")
    val onboardingQuestionsOpt = subDocRoot("onboarding-questions")(doc)
    columns.map ( columnName => Try(onboardingQuestionsOpt.map(_.get(columnName).asString().getValue)).toOption.flatten )
  }

  private def makeRow(values: Option[String]*) =
    values.map { s =>
      val ret = s.getOrElse(" ").replace("\r", " ").replace("\n", " ").replace("\"", "'")
      "\"" + ret + "\""
    }.mkString(",")
}
