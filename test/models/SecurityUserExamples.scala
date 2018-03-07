/*
 * Copyright 2018 HM Revenue & Customs
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

package models

object SecurityUserExamples {
  val ValidToken = "ABCDEFG"
  val CreatedApplication = CachedDataExample.CreatedApplication
  val ActiveCandidateUser = CachedUser(CreatedApplication.userId, "firstName", "lastName", Some("preferredName"),
    "email@test.com", isActive = true, "lockStatus")
  val ActiveCandidate = CachedData(ActiveCandidateUser, None)

  val InactiveCandidateUser = ActiveCandidateUser.copy(isActive = false)
  val InactiveCandidate = CachedData(InactiveCandidateUser, None)

  val CandidateWithApp = CachedDataWithApp(ActiveCandidateUser, CreatedApplication)
}
