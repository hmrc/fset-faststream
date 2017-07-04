/*
 * Copyright 2017 HM Revenue & Customs
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

package services.allocation

import model.exchange.AssessorSkill
import model.persisted.eventschedules.SkillType
import repositories.AssessorAllocationMongoRepository
import services.BaseServiceSpec
import model.{ AllocationStatuses, command, persisted }
import org.mockito.ArgumentMatchers.{ eq => eqTo }
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._

import scala.concurrent.Future

class AssessorAllocationServiceSpec extends BaseServiceSpec {

  "Allocate assessors" must {
    "save allocations if none already exist" in new TestFixture {
      when(mockAllocationRepository.allocationsForEvent(any[String])).thenReturn(Future.successful(Nil))
      when(mockAllocationRepository.save(any[Seq[persisted.AssessorAllocation]])).thenReturn(Future.successful(unit))
      val allocations = command.AssessorAllocations(
        version = "version1",
        eventId = "eventId1",
        allocations = command.AssessorAllocation("id", AllocationStatuses.CONFIRMED,
          allocatedAs = AssessorSkill(SkillType.ASSESSOR, "Assessor")) :: Nil
      )
      val result = service.allocate(allocations).futureValue
      result mustBe unit
      verify(mockAllocationRepository).save(any[Seq[persisted.AssessorAllocation]])
      verify(mockAllocationRepository, times(0)).delete(any[Seq[persisted.AssessorAllocation]])
    }

    "delete existing allocations and save new ones" in new TestFixture {
      when(mockAllocationRepository.allocationsForEvent(any[String])).thenReturn(Future.successful(
        persisted.AssessorAllocation("id", "eventId", AllocationStatuses.CONFIRMED, SkillType.CHAIR, "version1") :: Nil
      ))
      when(mockAllocationRepository.save(any[Seq[persisted.AssessorAllocation]])).thenReturn(Future.successful(unit))
      when(mockAllocationRepository.delete(any[Seq[persisted.AssessorAllocation]])).thenReturn(Future.successful(unit))
      val allocations = command.AssessorAllocations(
        version = "version1",
        eventId = "eventId1",
        allocations = command.AssessorAllocation("id", AllocationStatuses.CONFIRMED,
          allocatedAs = AssessorSkill(SkillType.ASSESSOR, "Assessor")) :: Nil
      )
      val result = service.allocate(allocations).futureValue
      result mustBe unit
      verify(mockAllocationRepository).delete(any[Seq[persisted.AssessorAllocation]])
      verify(mockAllocationRepository).save(any[Seq[persisted.AssessorAllocation]])
    }
  }

  trait TestFixture {
    val mockAllocationRepository = mock[AssessorAllocationMongoRepository]
    val service = new AssessorAllocationService {
      def allocationRepo: AssessorAllocationMongoRepository = mockAllocationRepository
    }
  }

}
