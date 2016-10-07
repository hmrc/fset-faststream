package model.persisted.phase3tests

import org.joda.time.DateTime
import play.api.libs.json.Json

case class Phase3Test(interviewId: Int,
                      usedForResults: Boolean,
                      testProvider: String = "launchpad",
                      testUrl: String,
                      token: String,
                      candidateId: String,
                      customCandidateId: String,
                      invitationDate: DateTime)

object Phase3Test { implicit val phase3TestFormat = Json.format[Phase3Test] }