package repositories.assistancedetails

import model.Exceptions.AssistanceDetailsNotFound
import model.persisted.AssistanceDetailsExamples
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.collection.immutable.Document
import repositories.CollectionNames
import testkit.MongoRepositorySpec

class AssistanceDetailsRepositorySpec extends MongoRepositorySpec {

  override val collectionName: String = CollectionNames.APPLICATION

  def repository = new AssistanceDetailsMongoRepository(mongo)
  val applicationCollection: MongoCollection[Document] = mongo.database.getCollection(collectionName)
  def insert(doc: Document) = applicationCollection.insertOne(doc).toFuture()

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
      result mustBe AssistanceDetailsNotFound(s"AssistanceDetails not found for ${applicationId(4)}")
    }
  }

  private def userId(i: Int) = s"UserId$i"
  private def applicationId(i: Int) = s"AppId$i"

  private def minimumApplicationBSON(applicationId: String, userId: String) = Document(
    "applicationId" -> applicationId,
    "userId" -> userId,
    "frameworkId" -> FrameworkId
  )

  private def applicationBSONWithFullAssistanceDetails(applicationId: String, userId: String) = Document(
    "applicationId" -> applicationId,
    "userId" -> userId,
    "frameworkId" -> FrameworkId,
    "assistance-details" -> Document(
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
