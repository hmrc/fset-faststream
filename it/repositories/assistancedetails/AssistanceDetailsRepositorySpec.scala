package repositories.assistancedetails

import model.Exceptions.AssistanceDetailsNotFound
import model.persisted.AssistanceDetailsExamples
import reactivemongo.bson.BSONDocument
import reactivemongo.play.json.ImplicitBSONHandlers
import repositories.CollectionNames
import testkit.MongoRepositorySpec

class AssistanceDetailsRepositorySpec extends MongoRepositorySpec {
  import ImplicitBSONHandlers._
  
  override val collectionName = CollectionNames.APPLICATION

  def repository = new AssistanceDetailsMongoRepository

  "update" should {

    "create new assistance details if they do not exist" in {
      val result = (for {
        _ <- insert(minimumApplicationBSON(applicationId(1), userId(1)))
        _ <- repository.update(applicationId(1), userId(1), AssistanceDetailsExamples.DisabilityGisAndAdjustments)
        ad <- repository.find(applicationId(1))
      } yield ad).futureValue

      result mustBe AssistanceDetailsExamples.DisabilityGisAndAdjustments
    }

    "update assistance details when they exist and find them successfully" in {
      val result = (for {
        _ <- insert(applicationBSONWithFullAssistanceDetails(applicationId(3), userId(3)))
        _ <- repository.update(applicationId(3), userId(3), AssistanceDetailsExamples.OnlyDisabilityNoGisNoAdjustments )
        ad <- repository.find(applicationId(3))
      } yield ad).futureValue

      result mustBe AssistanceDetailsExamples.OnlyDisabilityNoGisNoAdjustments
    }
  }

  "find" should {
    "return an exception when application does not exist" in {
      val result = repository.find(applicationId(4)).failed.futureValue
      result mustBe AssistanceDetailsNotFound(applicationId(4))
    }
  }

  private def insert(doc: BSONDocument) = repository.collection.insert(doc)

  private def userId(i: Int) = "UserId" + i
  private def applicationId(i: Int) = "AppId" + i

  private def minimumApplicationBSON(applicationId: String, userId: String) = BSONDocument(
    "applicationId" -> applicationId,
    "userId" -> userId,
    "frameworkId" -> FrameworkId
  )

  private def applicationBSONWithFullAssistanceDetails(applicationId: String, userId: String) = BSONDocument(
    "applicationId" -> applicationId,
    "userId" -> userId,
    "frameworkId" -> FrameworkId,
    "assistance-details" -> BSONDocument(
      "hasDisability" -> "Yes",
      "disabilityImpact" -> "No",
      "disabilityCategories" -> List("Other"),
      "otherDisabilityDescription" -> "My disability",
      "guaranteedInterview" -> true,
      "needsSupportForOnlineAssessment" -> true,
      "needsSupportForOnlineAssessmentDescription" -> "needsSupportForOnlineAssessmentDescription",
      "needsSupportAtVenue" -> true,
      "needsSupportAtVenueDescription" -> "needsSupportAtVenueDescription"
    )
  )
}
