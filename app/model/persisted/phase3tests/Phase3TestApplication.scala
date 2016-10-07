package model.persisted.phase3tests

import play.api.libs.json.Json


case class Phase3TestApplication(applicationId: String, applicationStatus: String, userId: String,
                                 preferredName: String, lastName: String)

object Phase3TestApplication { implicit val phase3TestApplicationFormat = Json.format[Phase3TestApplication] }
