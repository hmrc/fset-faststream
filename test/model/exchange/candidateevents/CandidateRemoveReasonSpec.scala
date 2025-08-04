/*
 * Copyright 2025 HM Revenue & Customs
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

package model.exchange.candidateevents

import model.exchange.candidateevents.CandidateRemoveReason.NoShow
import org.scalatestplus.play.PlaySpec

class CandidateRemoveReasonSpec extends PlaySpec {

  "CandidateRemoveReason" should {
    "return no show" in {
      val res = CandidateRemoveReason.find(CandidateRemoveReason.NoShow)
      res mustBe Some(CandidateRemoveReason(NoShow, NoShow, failApp = true))
    }
    "return no candidate contacted" in {
      val res = CandidateRemoveReason.find("Candidate_contacted")
      res mustBe Some(CandidateRemoveReason("Candidate_contacted", "Candidate contacted", failApp = false))
    }
    "return expired" in {
      val res = CandidateRemoveReason.find("Expired")
      res mustBe Some(CandidateRemoveReason("Expired", "Expired", failApp = true))
    }
    "return other" in {
      val res = CandidateRemoveReason.find("Other")
      res mustBe Some(CandidateRemoveReason("Other", "Other", failApp = false))
    }
    "return system generated remove reason" in {
      val res = CandidateRemoveReason.find("Candidate withdrew scheme (Commercial)")
      res mustBe Some(CandidateRemoveReason("Candidate withdrew scheme (Commercial)", "Candidate withdrew scheme (Commercial)", failApp = false))
    }
    "return None if no remove reason is supplied" in {
      val res = CandidateRemoveReason.find("")
      res mustBe None
    }
  }
}
