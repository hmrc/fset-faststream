package model

import model.OnlineTestCommands.OnlineTestApplication

object OnlineTestApplicationExamples {
  val OnlineTest = OnlineTestApplication("appId", ApplicationStatus.PHASE3_TESTS, "userId",
    guaranteedInterview = false, needsOnlineAdjustments = false, needsAtVenueAdjustments = false, "name", "lastname",
    None, None)
}
