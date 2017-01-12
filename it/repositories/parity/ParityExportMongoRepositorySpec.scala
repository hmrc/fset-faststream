package repositories.parity

import model.{ ApplicationStatus, SchemeType }
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.JsArray
import reactivemongo.json.ImplicitBSONHandlers
import repositories.CommonRepository
import repositories.parity.ParityExportRepository.ApplicationIdNotFoundException
import testkit.MongoRepositorySpec


class ParityExportMongoRepositorySpec extends MongoRepositorySpec with CommonRepository with MockitoSugar {

  import ImplicitBSONHandlers._
  import model.Phase3TestProfileExamples._

  val collectionName: String = "application"

  "next Applications Ready For export" must {

    "return nothing if application does not have READY_FOR_EXPORT" in {
      insertApplication("app1", ApplicationStatus.PHASE3_TESTS)
      val result = parityExportMongoRepo.nextApplicationsForExport(batchSize = 1,
        statusToExport = ApplicationStatus.READY_FOR_EXPORT).futureValue
      result mustBe empty
    }

    "return application id in READY_FOR_EXPORT" in {
      insertApplication("app1", ApplicationStatus.READY_FOR_EXPORT, None)

      val result = parityExportMongoRepo.nextApplicationsForExport(batchSize = 1,
        statusToExport = ApplicationStatus.READY_FOR_EXPORT).futureValue

      result.size mustBe 1
      result.head.applicationId mustBe "app1"
    }

    "return nothing when no applications are in READY_FOR_EXPORT" in {
      val result = parityExportMongoRepo.nextApplicationsForExport(batchSize = 1,
        statusToExport = ApplicationStatus.READY_FOR_EXPORT).futureValue
      result mustBe empty
    }

    "return nothing if application does not have READY_TO_UPDATE" in {
      insertApplication("app1", ApplicationStatus.PHASE3_TESTS)
      val result = parityExportMongoRepo.nextApplicationsForExport(batchSize = 1, statusToExport = ApplicationStatus.READY_TO_UPDATE).futureValue
      result mustBe empty
    }

    "return application id in READY_TO_UPDATE" in {
      insertApplication("app1", ApplicationStatus.READY_TO_UPDATE, None)

      val result = parityExportMongoRepo.nextApplicationsForExport(batchSize = 1, statusToExport = ApplicationStatus.READY_TO_UPDATE).futureValue

      result.size mustBe 1
      result.head.applicationId mustBe "app1"
    }

    "return nothing when no applications are in READY_TO_UPDATE" in {
      val result = parityExportMongoRepo.nextApplicationsForExport(batchSize = 1, statusToExport = ApplicationStatus.READY_TO_UPDATE).futureValue
      result mustBe empty
    }

    "limit number of next applications to the batch size limit" in {
      val batchSizeLimit = 5
      1 to 6 foreach { id =>
        insertApplication(s"app$id", ApplicationStatus.READY_FOR_EXPORT)
      }
      val result = parityExportMongoRepo.nextApplicationsForExport(batchSizeLimit,
        statusToExport = ApplicationStatus.READY_FOR_EXPORT).futureValue
      result.size mustBe batchSizeLimit
    }

    "return less number of applications than batch size limit" in {
      val batchSizeLimit = 5
      1 to 2 foreach { id =>
        insertApplication(s"app$id", ApplicationStatus.READY_FOR_EXPORT)
      }
      val result = parityExportMongoRepo.nextApplicationsForExport(batchSizeLimit,
        statusToExport = ApplicationStatus.READY_FOR_EXPORT).futureValue
      result.size mustBe 2
    }
  }

  "get application for export" must {

    "return a full application as a JsValue when valid" in {
      insertApplication("app1", ApplicationStatus.READY_FOR_EXPORT)

      val result = parityExportMongoRepo.getApplicationForExport("app1").futureValue
      (result \ "applicationId").as[String] mustBe "app1"
      (result \ "userId").as[String] must not be empty
      (result \ "applicationStatus").as[String] must not be empty
      (result \ "scheme-preferences" \ "schemes").as[JsArray].value.head.as[String] mustBe SchemeType.Commercial.toString
    }

    "throw an exception when an applicationId is invalid" in {
        parityExportMongoRepo.getApplicationForExport("app1").failed.futureValue mustBe a[ApplicationIdNotFoundException]
    }
  }
}
