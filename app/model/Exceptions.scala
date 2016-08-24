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

package model

object Exceptions {
  sealed class ConnectorException(message: String) extends Exception(message)

  case class NotFoundException(m: Option[String] = None) extends Exception(m.getOrElse("")) {
    def this(m: String) = this(Some(m))
  }

  case class UnexpectedException(m: String) extends Exception(m)

  case class CannotUpdateRecord(applicationId: String) extends Exception(applicationId)

  case class CannotUpdateFastPassDetails(applicationId: String) extends Exception(applicationId)

  case class FastPassDetailsNotFound(applicationId: String) extends Exception(applicationId)

  case class CannotUpdateContactDetails(userId: String) extends Exception(userId)

  case class CannotUpdateSchemePreferences(applicationId: String) extends Exception(applicationId)

  case class CannotUpdatePartnerGraduateProgrammes(applicationId: String) extends Exception(applicationId)

  case class CannotUpdateAssistanceDetails(userId: String) extends Exception(userId)

  case class CannotUpdatePreview(applicationId: String) extends Exception(applicationId)

  case class PersonalDetailsNotFound(applicationId: String) extends Exception(applicationId)

  case class ContactDetailsNotFound(userId: String) extends Exception(userId)

  case class SchemePreferencesNotFound(applicationId: String) extends Exception(applicationId)

  case class PartnerGraduateProgrammesNotFound(applicationId: String) extends Exception(applicationId)

  case class AssistanceDetailsNotFound(id: String) extends Exception(id)

  case class ApplicationNotFound(id: String) extends Exception(id)

  case class PassMarkSettingsNotFound() extends Exception

  case class CannotAddMedia(userId: String) extends Exception(userId)

  case class TooManyEntries(msg: String) extends Exception(msg)

  case class NoResultsReturned(reason: String) extends Exception(reason)

  case class NoSuchVenueException(reason: String) extends Exception(reason)

  case class NoSuchVenueDateException(reason: String) extends Exception(reason)

  case class IncorrectStatusInApplicationException(reason: String) extends Exception(reason)

  case class InvalidStatusException(reason: String) extends Exception(reason)

  case class EmailTakenException() extends Exception()

  case class DataFakingException(message: String) extends Exception(message)

  case class DataGenerationException(message: String) extends Exception(message)
}
