package models

import java.util.UUID

object SecurityUserExamples {
  val ActiveCandidateUser = CachedUser(UniqueIdentifier(UUID.randomUUID.toString), "firstName", "lastName", Some("preferredName"),
    "email@test.com", isActive = true, "lockStatus")
  val ActiveCandidate = CachedData(ActiveCandidateUser, None)

  val InactiveCandidateUser = ActiveCandidateUser.copy(isActive = false)
  val InactiveCandidate = CachedData(InactiveCandidateUser, None)
}
