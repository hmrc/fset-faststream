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

import factories.DateTimeFactory
import model.ApplicationStatuses
import org.joda.time.{DateTime, LocalDate}
import reactivemongo.bson.BSONDocument
import reactivemongo.json.ImplicitBSONHandlers
import repositories.application.{CandidateAllocationMongoRepository, GeneralApplicationMongoRepository}
import services.GBTimeZoneService
import testkit.MongoRepositorySpec


class CandidateAllocationRepositorySpec extends MongoRepositorySpec {

  import ImplicitBSONHandlers._

  override val collectionName = "application"
  
  def candidateAllocationRepo = new CandidateAllocationMongoRepository(DateTimeFactory)
  def helperRepo = new GeneralApplicationMongoRepository(GBTimeZoneService)

  "Next unconfirmed candidate to send a reminder" should {
    "return the unconfirmed candidate who's expiration date is today" in {
      createApplication("user1", "app1", ApplicationStatuses.AllocationUnconfirmed, "John", Some(LocalDate.now()))
      val candidate = candidateAllocationRepo.nextUnconfirmedCandidateToSendReminder(3).futureValue

      candidate must not be empty
    }

    "return the unconfirmed candidate who's expiration date is tomorrow" in {
      createApplication("user1", "app1", ApplicationStatuses.AllocationUnconfirmed, "John", Some(LocalDate.now().plusDays(3)))
      val candidate = candidateAllocationRepo.nextUnconfirmedCandidateToSendReminder(3).futureValue

      candidate must not be empty
    }

    "return nothing when the candidate's expiration date is in more than 1 day" in {
      createApplication("user1", "app1", ApplicationStatuses.AllocationUnconfirmed, "John", Some(LocalDate.now().plusDays(4)))
      val candidate = candidateAllocationRepo.nextUnconfirmedCandidateToSendReminder(3).futureValue

      candidate must be(empty)
    }

    "return one candidate which expire date is between 0 to 3 day from now" in {
      createApplication("user1", "app1", ApplicationStatuses.AllocationUnconfirmed, "Bob", Some(LocalDate.now()))
      createApplication("user2", "app2", ApplicationStatuses.AllocationUnconfirmed, "Carol", Some(LocalDate.now().plusDays(1)))
      createApplication("user3", "app3", ApplicationStatuses.AllocationUnconfirmed, "Eve", Some(LocalDate.now().minusDays(2)))
      createApplication("user4", "app4", ApplicationStatuses.AllocationUnconfirmed, "Alice", Some(LocalDate.now().plusDays(3)))
      createApplication("user5", "app5", ApplicationStatuses.AllocationUnconfirmed, "John", Some(LocalDate.now().plusDays(4)))

      (1 to 100).foreach { _ =>
        val allocation = candidateAllocationRepo.nextUnconfirmedCandidateToSendReminder(3).futureValue
        allocation must not be empty
        allocation.get.candidateDetails.preferredName must not be "John"
      }
    }

    "return nothing when all candidates have confirmed the allocation" in {
      createApplication("user1", "app1", ApplicationStatuses.AllocationConfirmed, "Alice", Some(LocalDate.now().plusDays(3)))
      createApplication("user2", "app2", ApplicationStatuses.AllocationConfirmed, "Bob", Some(LocalDate.now()))
      val candidate = candidateAllocationRepo.nextUnconfirmedCandidateToSendReminder(3).futureValue

      candidate must be(empty)
    }
  }

  "Save allocation reminder sent date" should {
    "save the current date" in {
      createApplication("user1", "app1", ApplicationStatuses.AllocationUnconfirmed, "John", Some(LocalDate.now()))

      candidateAllocationRepo.saveAllocationReminderSentDate("app1", DateTime.now()).futureValue

      val candidate = candidateAllocationRepo.nextUnconfirmedCandidateToSendReminder(3).futureValue
      candidate must be(empty)
    }

    "mark candidate as contacted even if the date was sent in the past" in {
      createApplication("user1", "app1", ApplicationStatuses.AllocationUnconfirmed, "John", Some(LocalDate.now()))

      candidateAllocationRepo.saveAllocationReminderSentDate("app1", DateTime.now().minusDays(3)).futureValue

      val candidate = candidateAllocationRepo.nextUnconfirmedCandidateToSendReminder(3).futureValue
      candidate must be(empty)
    }
  }

  def createApplication(userId: String, applicationId: String, applicationStatus: String,
                        preferredName: String, allocationExpireDate: Option[LocalDate]) = allocationExpireDate match {
    case Some(expireDate) =>
      helperRepo.collection.insert(BSONDocument(
        "userId" -> userId,
        "applicationId" -> applicationId,
        "personal-details" -> BSONDocument("preferredName" -> preferredName),
        "applicationStatus" -> applicationStatus,
        "allocation-expire-date" -> expireDate
      )).futureValue
    case None =>
      helperRepo.collection.insert(BSONDocument(
        "userId" -> userId,
        "applicationId" -> applicationId,
        "personal-details" -> BSONDocument("preferredName" -> preferredName),
        "applicationStatus" -> applicationStatus
      )).futureValue
  }
}

