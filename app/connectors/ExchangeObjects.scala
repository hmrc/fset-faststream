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

package connectors

import java.util.UUID

import model.TestAdjustment
import org.joda.time.{ DateTime, LocalDate }
import play.api.libs.json.JodaWrites._ // This is needed for DateTime serialization
import play.api.libs.json.JodaReads._ // This is needed for DateTime serialization
import play.api.libs.json._

// scalastyle:off
object ExchangeObjects {

  val frameworkId = "FastStream-2016"

  case class SendFsetMailRequest(
    to: List[String],
    templateId: String,
    parameters: Map[String, String],
    force: Boolean = false,
    eventUrl: Option[String] = None,
    onSendUrl: Option[String] = None,
    auditData: Map[String, String] = Map.empty
  )

  object SendFsetMailRequest {
    implicit val format: Format[SendFsetMailRequest] = Json.format[SendFsetMailRequest]
  }

  case class Candidate(firstName: String, lastName: String, preferredName: Option[String], email: String,
    phone: Option[String], userId: String, roles: List[String]) {
    def name: String = preferredName.getOrElse(firstName)
  }
  object Candidate { implicit val candidateFormat: OFormat[Candidate] = Json.format[Candidate] }

  case class UserAuthInfo(userId: String, isActive: Boolean, disabled: Boolean,
                          lastAttempt: Option[List[String]], failedAttempts: Option[Int])
  object UserAuthInfo { implicit val format: OFormat[UserAuthInfo] = Json.format[UserAuthInfo] }

  // PSI gateway requests
  case class RegisterCandidateRequest(inventoryId: String, // Read from config to identify the test we are registering for
                                      orderId: String, // Identifier we generate to uniquely identify the test
                                      accountId: String, // Candidate's account across all tests
                                      preferredName: String,
                                      lastName: String,
                                      redirectionUrl: String,
                                      assessmentId: String, // Read from config to identify the test we are registering for
                                      reportId: String, // Read from config to identify the test we are registering for
                                      normId: String, // Read from config to identify the test we are registering for
                                      adjustment: Option[TestAdjustment] = None)

  object RegisterCandidateRequest {
    implicit val registerCandidateRequest: OFormat[RegisterCandidateRequest] = Json.format[RegisterCandidateRequest]
  }

  case class CancelCandidateTestRequest(orderId: String)

  object CancelCandidateTestRequest {
    implicit val cancelCandidateTestRequestFormat: OFormat[CancelCandidateTestRequest] = Json.format[CancelCandidateTestRequest]
  }

  // TODO: Cubiks Gateway Requests delete
//  case class RegisterApplicant(firstName: String, lastName: String, email: String)
//  object RegisterApplicant { implicit val registerApplicantFormat: OFormat[RegisterApplicant] = Json.format[RegisterApplicant] }

  case class TimeAdjustments(assessmentId: Int,
    sectionId: Int,
    absoluteTime: Int
  )
  object TimeAdjustments { implicit val timeAdjustmentsFormat: OFormat[TimeAdjustments] = Json.format[TimeAdjustments] }

  // TODO: Cubiks delete
//  case class InviteApplicant(scheduleID: Int, userId: Int, scheduleCompletionURL: String, resultsURL: Option[String] = None,
//                             timeAdjustments: List[TimeAdjustments] = Nil)
//  object InviteApplicant { implicit val inviteApplicantFormat: OFormat[InviteApplicant] = Json.format[InviteApplicant] }

  // TODO: Cubiks Gateway Response delete
//  case class Registration(userId: Int)
//  object Registration { implicit val registrationFormat: OFormat[Registration] = Json.format[Registration] }

  // TODO: Cubiks delete
//  case class Invitation(userId: Int,
//    email: String, accessCode: String, logonUrl: String, authenticateUrl: String,
//    participantScheduleId: Int
//  )
//  object Invitation { implicit val invitationFormat: OFormat[Invitation] = Json.format[Invitation] }

  case class AllocationDetails(location: String, venueDescription: String, attendanceDateTime: DateTime, expirationDate: Option[LocalDate])
  object AllocationDetails { implicit val allocationDetailsFormat: OFormat[AllocationDetails] = Json.format[AllocationDetails] }

  case class AddUserRequest(email: String, password: String, firstName: String, lastName: String, roles: List[String], service: String)
  object AddUserRequest { implicit val addUserRequestFormat: OFormat[AddUserRequest] = Json.format[AddUserRequest] }

  case class UserResponse(firstName: String, lastName: String, preferredName: Option[String],
                          isActive: Boolean, userId: String, email: String, disabled: Boolean, lockStatus: String,
                          roles: List[String], service: String, phoneNumber: Option[String], detailsConfirmed: Option[Boolean])
  object UserResponse { implicit val userResponseFormat: OFormat[UserResponse] = Json.format[UserResponse] }

  case class ActivateEmailRequest(email: String, token: String, service: String)
  object ActivateEmailRequest { implicit val activateEmailRequestFormat: OFormat[ActivateEmailRequest] = Json.format[ActivateEmailRequest] }

  // Find by first/last
  case class FindByFirstNameRequest(roles: List[String], firstName: String)
  object FindByFirstNameRequest { implicit val findByFirstNameRequestFormat: OFormat[FindByFirstNameRequest] =
    Json.format[FindByFirstNameRequest]
  }

  case class FindByLastNameRequest(roles: List[String], lastName: String)
  object FindByLastNameRequest { implicit val findByLastNameRequestFormat: OFormat[FindByLastNameRequest] = Json.format[FindByLastNameRequest] }

  case class FindByFirstNameLastNameRequest(roles: List[String], firstName: String, lastName: String)
  object FindByFirstNameLastNameRequest { implicit val findByFirstNameLastNameRequestFormat: OFormat[FindByFirstNameLastNameRequest] =
    Json.format[FindByFirstNameLastNameRequest]
  }

  case class AssessmentOrderAcknowledgement(customerId: String,
                                            receiptId: String,
                                            orderId: String,
                                            testLaunchUrl: String,
                                            status: String,
                                            statusDetails: String,
                                            statusDate: LocalDate)

  object AssessmentOrderAcknowledgement {
    val acknowledgedStatus = "Acknowledged"
    val errorStatus = "Error"
    implicit val assessmentOrderAcknowledgementFormat: Format[AssessmentOrderAcknowledgement] =
      Json.format[AssessmentOrderAcknowledgement]

  }

  case class AssessmentCancelAcknowledgementResponse(status: String, details: String, statusDate: LocalDate)
  object AssessmentCancelAcknowledgementResponse {
    val completedStatus = "Completed"
    val errorStatus = "Error"

    implicit val assessmentCancelAcknowledgementResponseFormat: OFormat[AssessmentCancelAcknowledgementResponse] =
      Json.format[AssessmentCancelAcknowledgementResponse]
  }

  object Implicits {

    implicit val localDateFormat: Format[LocalDate] = new Format[LocalDate] {
      override def reads(json: JsValue): JsResult[LocalDate] =
        json.validate[String].map(LocalDate.parse)

      override def writes(o: LocalDate): JsValue = Json.toJson(o.toString)
    }
  }
}
