/*
 * Copyright 2016 HM Revenue & Customs
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

import mappings.Address
import mappings.PhoneNumberMapping._
import mappings.PostCodeMapping._
import models.ApplicationRoute.ApplicationRoute
import models.UniqueIdentifier
import org.joda.time.LocalDate
import play.api.libs.json.{ Format, Json }

package object exchange {

  val FrameworkId = "FastStream-2016"

  type LoginInfo = String

  case class EmailWrapper(email: String, service: String)

  case class FindByUserIdRequest(userId: UniqueIdentifier)

  case class CreateApplicationRequest(userId: UniqueIdentifier, frameworkId: String, applicationRoute: ApplicationRoute)

  case class GeneralDetails(firstName: String,
                            lastName: String,
                            preferredName: String,
                            email: String,
                            dateOfBirth: LocalDate,
                            outsideUk: Boolean,
                            address: Address,
                            postCode: Option[PostCode],
                            country: Option[String],
                            phone: Option[PhoneNumber],
                            civilServiceExperienceDetails: Option[CivilServiceExperienceDetails],
                            updateApplicationStatus: Option[Boolean]
                                   )

  case class AddReferral(userId: UniqueIdentifier, media: String)

  case class ApplicationResponse(applicationId: UniqueIdentifier, applicationStatus: String, applicationRoute: ApplicationRoute,
                                 userId: UniqueIdentifier, progressResponse: ProgressResponse,
                                 civilServiceExperienceDetails: Option[CivilServiceExperienceDetails])

  case class PersonalDetailsAdded(applicationId: UniqueIdentifier, userId: String)

  case class RegistrationEmail(to: List[String], templateId: String, parameters: Map[String, String])

  case class AddUserRequest(email: String, password: String, firstName: String, lastName: String, role: String, service: String)

  case class UpdateDetails(firstName: String, lastName: String, preferredName: Option[String], service: String)

  case class UpdateUserRequest(email: String, password: String, firstName: String, lastName: String,
                               userId: UniqueIdentifier, isActive: Boolean, service: String)

  case class SignInRequest(email: String, password: String, service: String)

  case class FindUserRequest(email: String)

  case class UserResponse(firstName: String, lastName: String, preferredName: Option[String], isActive: Boolean,
                          userId: UniqueIdentifier, email: String, lockStatus: String, role: String, service: String = "faststream")

  case class ActivateEmailRequest(email: String, token: String, service: String)

  case class ResendActivationTokenRequest(email: String, service: String)

  case class SendPasswordCodeRequest(email: String, service: String)
  case class ResetPasswordRequest(email: String, token: String, newPassword: String, service: String)

  case class PreviewRequest(flag: Boolean)

  object Implicits {

    implicit val emailWrapperFormat = Json.format[EmailWrapper]
    implicit val findByUserIdRequestFormat = Json.format[FindByUserIdRequest]
    implicit val addressFormat = Json.format[Address]
    implicit val referralFormat = Json.format[AddReferral]

    implicit val userFormat = Json.format[UserResponse]

    implicit val resendActivationTokenRequestFormat = Json.format[ResendActivationTokenRequest]

    implicit val activateEmailRequestFormat = Json.format[ActivateEmailRequest]
    implicit val signInRequestFormat = Json.format[SignInRequest]
    implicit val findUserRequestFormat = Json.format[FindUserRequest]

    implicit class exchangeUserToCachedUser(exchUser: UserResponse) {
      def toCached: models.CachedUser =
        models.CachedUser(exchUser.userId, exchUser.firstName, exchUser.lastName,
          exchUser.preferredName, exchUser.email, exchUser.isActive, exchUser.lockStatus)
    }

    implicit val registrationEmailFormat = Json.format[RegistrationEmail]
    implicit val addUserRequestFormat = Json.format[AddUserRequest]
    implicit val updateDetailsFormat = Json.format[UpdateDetails]

    /** Successes serialization */
    implicit val applicationAddedFormat = Json.format[ApplicationResponse]
    implicit val personalDetailsAddedFormat = Json.format[PersonalDetailsAdded]

    /** Requests serialization */
    implicit val createApplicationRequestFormat: Format[CreateApplicationRequest] = Json.format[CreateApplicationRequest]
    implicit val updatePersonalDetailsRequestFormat: Format[GeneralDetails] = Json.format[GeneralDetails]
    implicit val updateAssistanceDetailsRequestFormat: Format[AssistanceDetails] = Json.format[AssistanceDetails]

    implicit val sendPasswordCodeRequestFormat = Json.format[SendPasswordCodeRequest]
    implicit val resetPasswordRequestFormat = Json.format[ResetPasswordRequest]

    implicit val previewFormat = Json.format[PreviewRequest]
  }
}
