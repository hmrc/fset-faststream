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

package model

import play.api.libs.json.{ Json, OFormat }

// scalastyle:off number.of.methods number.of.types
object Exceptions {
  sealed class ConnectorException(message: String) extends Exception(message)

  class NotFoundException(m: Option[String] = None) extends Exception(m.getOrElse("")) {
    def this(m: String) = this(Some(m))
  }

  case class UnexpectedException(m: String) extends Exception(m)

  case class CannotUpdateRecord(applicationId: String) extends Exception(applicationId)

  case class CannotUpdateCivilServiceExperienceDetails(applicationId: String) extends Exception(applicationId)

  case class CivilServiceExperienceDetailsNotFound(applicationId: String) extends Exception(applicationId)

  case class CannotUpdateContactDetails(userId: String) extends Exception(userId)

  case class CannotUpdateFSACIndicator(userId: String) extends Exception(userId)

  case class CannotUpdateSchemePreferences(applicationId: String) extends Exception(applicationId)

  case class CannotUpdateAssistanceDetails(userId: String) extends Exception(userId)

  case class CannotUpdatePreview(applicationId: String) extends Exception(applicationId)

  case class CannotUpdateAssessorWhenSkillsAreRemovedAndFutureAllocationExistsException(userId: String,
    msg: String) extends Exception(msg)

  case class CannotRemoveAssessorWhenFutureAllocationExistsException(userId: String, msg: String) extends Exception(msg)

  case class PersonalDetailsNotFound(applicationId: String) extends Exception(applicationId)

  case class FSACIndicatorNotFound(applicationId: String) extends Exception(applicationId)

  case class FSACCSVIndicatorNotFound(applicationId: String) extends Exception(applicationId)

  case class ContactDetailsNotFound(userId: String) extends Exception(userId)

  case class ContactDetailsNotFoundForEmail() extends Exception

  case class SchemePreferencesNotFound(applicationId: String) extends Exception(applicationId)

  case class PassMarkEvaluationNotFound(applicationId: String) extends Exception(applicationId)

  case class AssistanceDetailsNotFound(id: String) extends Exception(id)

  case class ApplicationNotFound(id: String) extends Exception(id)

  case class TokenNotFound(id: String) extends Exception(id)

  case class PassMarkSettingsNotFound() extends Exception

  case class CannotAddMedia(userId: String) extends Exception(userId)

  case class TooManyEntries(msg: String) extends Exception(msg)

  object TooManyEntries {
    implicit val tooManyEntriesFormat: OFormat[TooManyEntries] = Json.format[TooManyEntries]
  }

  case class NoResultsReturned(reason: String) extends Exception(reason)

  object NoResultsReturned {
    implicit val noResultsReturnedFormat: OFormat[NoResultsReturned] = Json.format[NoResultsReturned]
  }

  case class NoSuchVenueException(reason: String) extends Exception(reason)

  case class NoSuchVenueDateException(reason: String) extends Exception(reason)

  case class IncorrectStatusInApplicationException(reason: String) extends Exception(reason)

  case class InvalidApplicationStatusAndProgressStatusException(reason: String) extends Exception(reason)

  case class EmailTakenException() extends Exception()

  case class DataFakingException(message: String) extends Exception(message)

  case class DataGenerationException(message: String) extends Exception(message)

  case class CannotFindTestByCubiksId(message: String) extends NotFoundException(message)

  case class CannotFindTestGroupByApplicationIdException(message: String) extends NotFoundException(message)

  case class CannotFindTestByOrderIdException(message: String) extends NotFoundException(message)

  case class CannotFindTestByInventoryIdException(message: String) extends NotFoundException(message)

  case class CannotFindApplicationByCubiksId(message: String) extends NotFoundException(message)

  case class CannotFindApplicationByOrderIdException(message: String) extends NotFoundException(message)

  case class AdjustmentsCommentNotFound(applicationId: String) extends Exception(applicationId)

  case class CannotUpdateAdjustmentsComment(applicationId: String) extends Exception(applicationId)

  case class CannotRemoveAdjustmentsComment(applicationId: String) extends Exception(applicationId)

  case class InvalidTokenException(m: String) extends Exception(m)

  case class ExpiredTestForTokenException(m: String) extends Exception(m)

  case class AssessorNotFoundException(userId: String) extends Exception(userId)

  case class CandidateAllocationNotFoundException(m: String) extends Exception(m)

  case class EventNotFoundException(m: String) extends Exception(m)

  case class OptimisticLockException(m: String) extends Exception(m)

  case class TooManyEventIdsException(m: String) extends Exception(m)

  case class TooManySessionIdsException(m: String) extends Exception(m)

  case class SchemeSpecificAnswerNotFound(m: String) extends Exception(m)

  case class SiftAnswersNotFound(m: String) extends Exception(m)

  case class SiftAnswersIncomplete(m: String) extends Exception(m)

  case class SiftAnswersSubmitted(m: String) extends Exception(m)

  case class AlreadyEvaluatedForSchemeException(message: String) extends Exception(message)

  case class SchemeNotFoundException(message: String) extends Exception(message)

  case class LastSchemeWithdrawException(m: String) extends Exception(m)

  case class LastRunInfoNotFound(m: String) extends Exception(m)

  case class SiftResultsAlreadyExistsException(m: String) extends Exception(m)

  case class SiftExpiredException(m: String) extends Exception(m)
}

// scalastyle:on number.of.methods number.of.types
