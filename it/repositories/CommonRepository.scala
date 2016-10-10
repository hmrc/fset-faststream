package repositories

import model.ApplicationStatus.ApplicationStatus
import model.OnlineTestCommands.{ Phase1Test, Phase1TestProfile }
import model.Phase1TestExamples._
import model.SchemeType._
import model.persisted.{ ApplicationPhase1ReadyForEvaluation, AssistanceDetails, TestResult }
import model.{ ApplicationStatus, ProgressStatuses, SelectedSchemes }
import org.joda.time.{ DateTime, DateTimeZone }
import org.scalatest.concurrent.ScalaFutures
import reactivemongo.bson.BSONDocument
import repositories.application.GeneralApplicationMongoRepository
import repositories.assistancedetails.AssistanceDetailsMongoRepository
import repositories.onlinetesting.Phase1TestMongoRepository
import repositories.schemepreferences.SchemePreferencesMongoRepository
import testkit.MongoRepositorySpec


trait CommonRepository {
  this: MongoRepositorySpec with ScalaFutures =>

  import reactivemongo.json.ImplicitBSONHandlers._
  def applicationRepository: GeneralApplicationMongoRepository
  def schemePreferencesRepository: SchemePreferencesMongoRepository
  def assistanceDetailsRepository: AssistanceDetailsMongoRepository
  def phase1TestRepository: Phase1TestMongoRepository


  implicit val now = DateTime.now().withZone(DateTimeZone.UTC)

  def selectedSchemes(schemeTypes: List[SchemeType]) = SelectedSchemes(schemeTypes, orderAgreed = true, eligible = true)


  def insertApplicationWithPhase1TestResults(appId: String, sjq: Double, bq: Option[Double] = None, isGis: Boolean = false
                                            )(schemes:SchemeType*): ApplicationPhase1ReadyForEvaluation = {
    val sjqTest = firstTest.copy(cubiksUserId = 1, testResult = Some(TestResult("Ready", "norm", Some(sjq), None, None, None)))
    val bqTest = secondTest.copy(cubiksUserId = 2, testResult = Some(TestResult("Ready", "norm", bq, None, None, None)))
    val phase1Tests = if(isGis) List(sjqTest) else List(sjqTest, bqTest)
    insertApplication(appId, ApplicationStatus.PHASE1_TESTS, Some(phase1Tests))
    ApplicationPhase1ReadyForEvaluation(appId, ApplicationStatus.PHASE1_TESTS, isGis, Phase1TestProfile(now, phase1Tests),
      selectedSchemes(schemes.toList))
  }

  def insertApplication(appId: String, applicationStatus: ApplicationStatus, tests: Option[List[Phase1Test]] = None,
                        isGis: Boolean = false, schemes: List[SchemeType] = List(Commercial)): Unit = {
    val gis = if (isGis) Some(true) else None
    applicationRepository.collection.insert(BSONDocument(
      "applicationId" -> appId,
      "userId" -> appId,
      "applicationStatus" -> applicationStatus
    )).futureValue

    val ad = AssistanceDetails("No", None, gis, needsSupportForOnlineAssessment = false, None, needsSupportAtVenue = false, None)
    assistanceDetailsRepository.update(appId, appId, ad).futureValue

    schemePreferencesRepository.save(appId, selectedSchemes(schemes)).futureValue

    tests.foreach { t =>
      phase1TestRepository.insertOrUpdatePhase1TestGroup(appId, Phase1TestProfile(now, t)).futureValue
      t.foreach { oneTest =>
        oneTest.testResult.foreach { result =>
          phase1TestRepository.insertPhase1TestResult(appId, oneTest, result).futureValue
        }
      }
      if (t.exists(_.testResult.isDefined)) {
        phase1TestRepository.updateProgressStatus(appId, ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED).futureValue
      }
    }
    applicationRepository.collection.update(
      BSONDocument("applicationId" -> appId),
      BSONDocument("$set" -> BSONDocument("applicationStatus" -> applicationStatus))).futureValue
  }

}
