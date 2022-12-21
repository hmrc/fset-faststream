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

package command

import model.AllocationStatuses
import model.command.AssessorAllocations
import model.exchange.AssessorSkill
import model.persisted.eventschedules.SkillType
import model.persisted.AssessorAllocation
import testkit.UnitSpec

class AllocationSpec extends UnitSpec {

  "Assessor allocations" should {
    "deal with Seq implemented as a List with the same version" in new TestFixture {
      val assessorAllocation = newAssessorAllocation("v1")
      val qacAllocation = newQacAllocation("v1")
      val allocations = List[AssessorAllocation](assessorAllocation, qacAllocation)

      val result = AssessorAllocations(eventId = "eventId", assessorAllocations = allocations)

      val assessorAllocation2 = model.command.AssessorAllocation(id = "id1", status = AllocationStatuses.CONFIRMED,
        allocatedAs = AssessorSkill.SkillMap(assessorAllocation.allocatedAs))

      val qacAllocation2 = model.command.AssessorAllocation(id = "id2", status = AllocationStatuses.CONFIRMED,
        allocatedAs = AssessorSkill.SkillMap(qacAllocation.allocatedAs))

      val expected = AssessorAllocations(version = "v1", eventId = "eventId" , allocations = List(assessorAllocation2, qacAllocation2))
      result mustBe expected
    }

    "deal with Seq implemented as a List with different versions" in new TestFixture {
      val assessorAllocation = newAssessorAllocation("v1")
      val qacAllocation = newAssessorAllocation("v2")
      val allocations = List[AssessorAllocation] ( assessorAllocation, qacAllocation)

      val exception = intercept[Exception] {
        AssessorAllocations(eventId = "eventId", assessorAllocations = allocations)
      }
      exception.getMessage must include(
        s"Allocations to this event [eventId=eventId] " +
          s"and these assessors [assessorIds=List(id1)] have mismatching op lock versions List(v1, v2)")
    }

    "deal with Seq implemented as a Vector with the same version" in new TestFixture {
      val assessorAllocation = newAssessorAllocation("v1")
      val qacAllocation = newQacAllocation("v1")
      val allocations = Vector[AssessorAllocation] ( assessorAllocation, qacAllocation)

      val result = AssessorAllocations(eventId = "eventId", assessorAllocations = allocations)

      val assessorAllocation2 = model.command.AssessorAllocation(id = "id1", status = AllocationStatuses.CONFIRMED,
        allocatedAs = AssessorSkill.SkillMap(assessorAllocation.allocatedAs))

      val qacAllocation2 = model.command.AssessorAllocation(id = "id2", status = AllocationStatuses.CONFIRMED,
        allocatedAs = AssessorSkill.SkillMap(qacAllocation.allocatedAs))

      val expected = AssessorAllocations(version = "v1", eventId = "eventId" , allocations = Vector(assessorAllocation2, qacAllocation2))
      result mustBe expected
    }

    "deal with Seq implemented as a Vector with different versions" in new TestFixture {
      val assessorAllocation = newAssessorAllocation("v1")
      val qacAllocation = newAssessorAllocation("v2")
      val allocations = Vector[AssessorAllocation] ( assessorAllocation, qacAllocation)

      val exception = intercept[Exception] {
        AssessorAllocations(eventId = "eventId", assessorAllocations = allocations)
      }
      exception.getMessage must include(
        s"Allocations to this event [eventId=eventId] and these assessors [assessorIds=Vector(id1)]" +
          s" have mismatching op lock versions Vector(v1, v2)")
    }
  }

  trait TestFixture {
    def newAssessorAllocation(version: String) = model.persisted.AssessorAllocation(id = "id1", eventId = "eventId1",
      status = AllocationStatuses.CONFIRMED, allocatedAs = SkillType.ASSESSOR, version)

    def newQacAllocation(version: String) = model.persisted.AssessorAllocation(id = "id2", eventId = "eventId1",
      status = AllocationStatuses.CONFIRMED, allocatedAs = SkillType.QUALITY_ASSURANCE_COORDINATOR, version)
  }
}
