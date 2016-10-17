package model

import model.ProgressStatuses.{ PHASE1_TESTS_EXPIRED, PHASE2_TESTS_EXPIRED, ProgressStatus }


sealed case class ExpiryTest(hoursBeforeReminder: Int, phase: String, expiredStatus: ProgressStatus) {
  require(hoursBeforeReminder > 0, "Hours before expiration was negative")
  require(expiredStatus == PHASE1_TESTS_EXPIRED || expiredStatus == PHASE2_TESTS_EXPIRED,
    "progressStatuses value not allowed")
  require(phase == "PHASE1" || phase == "PHASE2",
    "phase value not allowed")

}

object Phase1ExpiryTest extends ExpiryTest(24 * 7, "PHASE1", PHASE1_TESTS_EXPIRED)
object Phase2ExpiryTest extends ExpiryTest(24 * 7, "PHASE2", PHASE2_TESTS_EXPIRED)
