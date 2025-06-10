/*
 * Copyright 2024 HM Revenue & Customs
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

package services.onlinetesting.phase2

import config.{Phase2TestsConfig, PsiTestIds}
import factories.UUIDFactory
import model.ApplicationRoute._
import model.ApplicationStatus._
import model.EvaluationResults._
import model.ProgressStatuses.ProgressStatus
import model.exchange.passmarksettings._
import model.persisted.{ApplicationReadyForEvaluation, PassmarkEvaluation, SchemeEvaluationResult}
import model.{ApplicationRoute, _}
import org.mockito.Mockito.when
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.collection.immutable.Document
import org.scalatest.prop._
import repositories.{CollectionNames, CommonRepository}
import testkit.MongoRepositorySpec

import java.time.OffsetDateTime
import scala.concurrent.Future

class Phase2TestEvaluationSpec extends MongoRepositorySpec with CommonRepository
  with TableDrivenPropertyChecks {

  val collectionName: String = CollectionNames.APPLICATION
  override val additionalCollections = List(CollectionNames.PHASE2_PASS_MARK_SETTINGS)

  def phase2TestEvaluationService = {
    when(mockAppConfig.onlineTestsGatewayConfig).thenReturn(mockOnlineTestsGatewayConfig)

    def testIds(idx: Int): PsiTestIds =
      PsiTestIds(s"inventoryId$idx", s"assessmentId$idx", s"reportId$idx", s"normId$idx")

    val tests = Map[String, PsiTestIds](
      "test1" -> testIds(5),
      "test2" -> testIds(6)
    )

    val mockPhase2TestsConfig: Phase2TestsConfig = mock[Phase2TestsConfig]
    when(mockOnlineTestsGatewayConfig.phase2Tests).thenReturn(mockPhase2TestsConfig)
    when(mockPhase2TestsConfig.tests).thenReturn(tests)

    new EvaluatePhase2ResultService(
      phase2EvaluationRepo,
      phase2PassMarkSettingRepo,
      applicationRepository,
      mockAppConfig,
      UUIDFactory
    )
  }

  trait TestFixture extends Schemes {

    // format: OFF
    val phase2PassMarkSettingsTable = Table[SchemeId, Double, Double, Double, Double](
      ("Scheme Name",                           "Test1 Fail", "Test1 Pass", "Test2 Fail", "Test2 Pass"),
      (Commercial,                                20.0,         80.0,         20.0,         80.0),
      (Digital,             20.001,       20.001,       20.001,       20.001),
      (DiplomaticAndDevelopment,                  20.01,        20.02,        20.01,        20.02),
      (DiplomaticAndDevelopmentEconomics,         30.0,         70.0,         30.0,         70.0),
      (Finance,                                   25.01,        25.02,        25.01,        25.02),
      (GovernmentCommunicationService,            30.0,         70.0,         30.0,         70.0),
      (GovernmentEconomicsService,                30.0,         70.0,         30.0,         70.0),
      (GovernmentOperationalResearchService,      30.0,         70.0,         30.0,         70.0),
      (GovernmentPolicy,                          30.0,         70.0,         30.0,         70.0),
      (GovernmentSocialResearchService,           30.0,         70.0,         30.0,         70.0),
      (GovernmentStatisticalService,              30.0,         70.0,         30.0,         70.0),
      (HousesOfParliament,                        30.0,         79.999,       30.0,         79.999),
      (HumanResources,                            30.0,         50.0,         30.0,         50.0),
      (OperationalDelivery,                       30.0,         30.0,         30.0,         30.0),
      (ProjectDelivery,                           30.0,         70.0,         30.0,         70.0),
      (Property,                                  40.0,         70.0,         40.0,         70.0),
      (ScienceAndEngineering,                     69.00,        69.00,        69.00,        69.00)
    )
    // format: ON

    var phase2PassMarkSettings: Phase2PassMarkSettingsPersistence = _

    var applicationReadyForEvaluation: ApplicationReadyForEvaluation = _

    var passMarkEvaluation: PassmarkEvaluation = _

    var phase1PassMarkEvaluation: PassmarkEvaluation = _

    def applicationEvaluation(applicationId: String, test1Score: Double, test2Score: Double, selectedSchemes: SchemeId*)
                             (implicit applicationRoute: ApplicationRoute = ApplicationRoute.Faststream): TestFixture = {
      applicationReadyForEvaluation = insertApplicationWithPhase2TestResults(applicationId, test1Score, test2Score,
        phase1PassMarkEvaluation, applicationRoute = applicationRoute)(selectedSchemes: _*)
      phase2TestEvaluationService.evaluate(applicationReadyForEvaluation, phase2PassMarkSettings).futureValue
      this
    }

    def mustResultIn(expApplicationStatus: ApplicationStatus.ApplicationStatus, expProgressStatus: Option[ProgressStatus],
                     expSchemeResults: (SchemeId, Result)*): TestFixture = {
      passMarkEvaluation = phase2EvaluationRepo.getPassMarkEvaluation(applicationReadyForEvaluation.applicationId).futureValue
      val applicationDetails = applicationRepository.findStatus(applicationReadyForEvaluation.applicationId).futureValue
      val applicationStatus = ApplicationStatus.withName(applicationDetails.status)
      val progressStatus = applicationDetails.latestProgressStatus

      val schemeResults = passMarkEvaluation.result.map { schemeEvaluationResult =>
        // Scala 3 pattern binding
        val SchemeEvaluationResult(schemeType, resultStr) = schemeEvaluationResult
        schemeType -> Result(resultStr)
      }
      phase2PassMarkSettings.version mustBe passMarkEvaluation.passmarkVersion
      applicationStatus mustBe expApplicationStatus
      progressStatus mustBe expProgressStatus
      schemeResults must contain theSameElementsAs expSchemeResults
      passMarkEvaluation.previousPhasePassMarkVersion mustBe Some(phase1PassMarkEvaluation.passmarkVersion)
      this
    }

    def applicationReEvaluationWithSettings(newSchemeSettings: (SchemeId, Double, Double, Double, Double)*): TestFixture = {
      val schemePassMarkSettings = phase2PassMarkSettingsTable.filter(schemeSetting =>
        !newSchemeSettings.map(_._1).contains(schemeSetting._1)) ++ newSchemeSettings
      phase2PassMarkSettings = createPhase2PassMarkSettings(schemePassMarkSettings).futureValue
      phase2TestEvaluationService.evaluate(applicationReadyForEvaluation, phase2PassMarkSettings).futureValue
      this
    }

    private def createPhase2PassMarkSettings(phase2PassMarkSettingsTable:
                                             TableFor5[SchemeId, Double, Double, Double, Double]): Future[Phase2PassMarkSettingsPersistence] = {
      val schemeThresholds = phase2PassMarkSettingsTable.map {
        case (schemeName, test1FailThreshold, test1PassThreshold, test2FailThreshold, test2PassThreshold) =>
          Phase2PassMark(
            schemeName,
            Phase2PassMarkThresholds(
              PassMarkThreshold(test1FailThreshold, test1PassThreshold),
              PassMarkThreshold(test2FailThreshold, test2PassThreshold)
            )
          )
      }.toList

      val phase2PassMarkSettings = Phase2PassMarkSettingsPersistence(
        schemeThresholds,
        "version-1",
        OffsetDateTime.now,
        "user-1"
      )
      phase2PassMarkSettingRepo.create(phase2PassMarkSettings).flatMap { _ =>
        phase2PassMarkSettingRepo.getLatestVersion.map(_.get)
      }
    }

    val appCollection: MongoCollection[Document] = mongo.database.getCollection(collectionName)

    def createUser(userId: String, appId: String) = {
      import org.mongodb.scala.SingleObservableFuture
      appCollection.insertOne(Document("applicationId" -> appId, "userId" -> userId,
        "applicationStatus" -> ApplicationStatus.CREATED.toBson)).toFuture().map( _ => ())
    }

    Future.sequence(List(
      createUser("user-1", "application-1"),
      createUser("user-2", "application-2"),
      createUser("user-3", "application-3"),
      createPhase2PassMarkSettings(phase2PassMarkSettingsTable).map(phase2PassMarkSettings = _)
    )).futureValue
  }
}
