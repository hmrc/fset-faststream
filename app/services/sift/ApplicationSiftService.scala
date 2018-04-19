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

package services.sift

import common.FutureEx
import connectors.{ CSREmailClient, EmailClient }
import factories.DateTimeFactory
import model.EvaluationResults.{ Green, Red, Result, Withdrawn }
import model.Exceptions.SiftResultsAlreadyExistsException
import model.ProgressStatuses.SIFT_ENTERED
import model._
import model.command.{ ApplicationForNumericTest, ApplicationForSift, ApplicationForSiftExpiry }
import model.command.{ ApplicationForSift, ApplicationForSiftExpiry }
import model.exchange.sift.SiftState
import model.persisted.SchemeEvaluationResult
import model.persisted.sift.NotificationExpiringSift
import model.sift.{ FixStuckUser, FixUserStuckInSiftEntered, SiftReminderNotice }
import play.api.Logger
import model.sift.{ FixStuckUser, FixUserStuckInSiftEntered }
import org.joda.time.DateTime
import play.api.Logger
import reactivemongo.bson.BSONDocument
import repositories.{ CommonBSONDocuments, CurrentSchemeStatusHelper, SchemeRepository, SchemeYamlRepository }
import repositories.application.{ GeneralApplicationMongoRepository, GeneralApplicationRepository }
import repositories.contactdetails.{ ContactDetailsMongoRepository, ContactDetailsRepository }
import repositories.sift.{ ApplicationSiftMongoRepository, ApplicationSiftRepository }
import services.allocation.CandidateAllocationService.CouldNotFindCandidateWithApplication

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps
import uk.gov.hmrc.http.HeaderCarrier

object ApplicationSiftService extends ApplicationSiftService {
  val applicationSiftRepo: ApplicationSiftMongoRepository = repositories.applicationSiftRepository
  val applicationRepo: GeneralApplicationMongoRepository = repositories.applicationRepository
  val contactDetailsRepo: ContactDetailsMongoRepository = repositories.faststreamContactDetailsRepository
  val schemeRepo: SchemeRepository = SchemeYamlRepository
  val dateTimeFactory: DateTimeFactory = DateTimeFactory
  val emailClient: CSREmailClient = CSREmailClient
  val SiftExpiryWindowInDays: Int = 7
}

// scalastyle:off number.of.methods
trait ApplicationSiftService extends CurrentSchemeStatusHelper with CommonBSONDocuments {

  val SiftExpiryWindowInDays: Int

  def applicationSiftRepo: ApplicationSiftRepository
  def applicationRepo: GeneralApplicationRepository
  def contactDetailsRepo: ContactDetailsRepository
  def schemeRepo: SchemeRepository
  def emailClient: EmailClient

  def nextApplicationsReadyForSiftStage(batchSize: Int): Future[Seq[ApplicationForSift]] = {
    applicationSiftRepo.nextApplicationsForSiftStage(batchSize)
  }

  def nextApplicationForFirstReminder(timeInHours: Int): Future[Option[NotificationExpiringSift]] = {
    applicationSiftRepo.nextApplicationForFirstSiftReminder(timeInHours)
  }

  def nextApplicationForSecondReminder(timeInHours: Int): Future[Option[NotificationExpiringSift]] = {
    applicationSiftRepo.nextApplicationForSecondSiftReminder(timeInHours)
  }

  def nextApplicationsReadyForNumericTestsInvitation(batchSize: Int) : Future[Seq[ApplicationForNumericTest]] = {
    val numericalSchemeIds = schemeRepo.numericTestSiftRequirementSchemeIds
    def isEligibleForNumericTest(app: ApplicationForNumericTest): Boolean = {
      app.currentSchemeStatus.exists(schemeRes =>
        Result(schemeRes.result) == Green && numericalSchemeIds.contains(schemeRes.schemeId)
      )
    }
    applicationSiftRepo.nextApplicationsReadyForNumericTestsInvitation(batchSize).map(_.filter(isEligibleForNumericTest))
  }

  def sendReminderNotification(expiringSift: NotificationExpiringSift,
    siftReminderNotice: SiftReminderNotice)(implicit hc: HeaderCarrier): Future[Unit] = {
      for {
        emailAddress <- contactDetailsRepo.find(expiringSift.userId).map(_.email)
        _ <- emailClient.sendSiftReminder(emailAddress, expiringSift.preferredName, siftReminderNotice.hoursBeforeReminder,
          siftReminderNotice.timeUnit, expiringSift.expiryDate)
        _ <- applicationRepo.addProgressStatusAndUpdateAppStatus(expiringSift.applicationId, siftReminderNotice.progressStatus)
      } yield {
        val msg = s"Sift reminder email sent to candidate whose applicationId = ${expiringSift.applicationId} " +
          s"${siftReminderNotice.hoursBeforeReminder} hours before expiry and candidate status updated " +
          s"to ${siftReminderNotice.progressStatus}"
        Logger.info(msg)
      }
  }

  def isSiftExpired(applicationId: String): Future[Boolean] = {
    applicationSiftRepo.isSiftExpired(applicationId)
  }

  def processNextApplicationFailedAtSift: Future[Unit] = applicationSiftRepo.nextApplicationFailedAtSift.flatMap(_.map { application =>
    applicationRepo.addProgressStatusAndUpdateAppStatus(application.applicationId, ProgressStatuses.FAILED_AT_SIFT)
  }.getOrElse(Future.successful(())))

  private def requiresForms(schemeIds: Seq[SchemeId]) = {
    schemeRepo.getSchemesForIds(schemeIds).exists(_.siftRequirement.contains(SiftRequirement.FORM))
  }

  def progressStatusForSiftStage(schemeList: Seq[SchemeId]): ProgressStatuses.ProgressStatus = if (requiresForms(schemeList)) {
    ProgressStatuses.SIFT_ENTERED
  } else {
    ProgressStatuses.SIFT_READY
  }

  def progressApplicationToSiftStage(applications: Seq[ApplicationForSift]): Future[SerialUpdateResult[ApplicationForSift]] = {
    val updates = FutureEx.traverseSerial(applications) { app =>
      val status = progressStatusForSiftStage(app.currentSchemeStatus.collect { case s if s.result == Green.toString => s.schemeId })
      FutureEx.futureToEither(
        app,
        applicationRepo.addProgressStatusAndUpdateAppStatus(app.applicationId, status)
      )
    }
    updates.map(SerialUpdateResult.fromEither)
  }

  def saveSiftExpiryDate(applicationId: String,
                         expiryDate: DateTime = DateTimeFactory.nowLocalTimeZone.plusDays(SiftExpiryWindowInDays)): Future[Unit] = {
    applicationSiftRepo.saveSiftExpiryDate(applicationId, expiryDate).map(_ => ())
  }

  def findApplicationsReadyForSchemeSift(schemeId: SchemeId): Future[Seq[model.Candidate]] = {
    applicationSiftRepo.findApplicationsReadyForSchemeSift(schemeId)
  }

  def siftApplicationForScheme(applicationId: String, result: SchemeEvaluationResult): Future[Unit] = {
    applicationSiftRepo.siftResultsExistsForScheme(applicationId, result.schemeId).map { siftResultsExists =>
      if(siftResultsExists) {
        throw SiftResultsAlreadyExistsException(s"Sift result already exists for appId $applicationId and scheme ${result.schemeId}")
      } else {
        applicationRepo.getApplicationRoute(applicationId).flatMap { route =>
          val updateFunction = route match {
            case ApplicationRoute.SdipFaststream => buildSiftSettableFields(result, sdipFaststreamSchemeFilter) _
            case _ => buildSiftSettableFields(result, schemeFilter) _
          }
          siftApplicationForScheme(applicationId, result, updateFunction)
        }
      }
    }
  }

  def processExpiredCandidates(batchSize: Int)(implicit hc: HeaderCarrier): Future[Unit] = {
    def processApplication(appForExpiry: ApplicationForSiftExpiry): Future[Unit] = {
      expireCandidate(appForExpiry)
        .map(_ => notifyExpiredCandidate(appForExpiry.applicationId))
    }

    nextApplicationsForExpiry(batchSize)
      .flatMap {
        case Nil =>
          Logger.info("No application found for SIFT expiry")
          Future.successful(())
        case applications: Seq[ApplicationForSiftExpiry] =>
          Logger.info(s"${applications.size} applications found for SIFT expiry -- $applications")
          Future.sequence(applications.map(processApplication)).map(_ => ())
      }
  }

  def nextApplicationsForExpiry(batchSize: Int): Future[Seq[ApplicationForSiftExpiry]] = {
    applicationSiftRepo.nextApplicationsForSiftExpiry(batchSize)
  }

  def expireCandidate(appForExpiry: ApplicationForSiftExpiry): Future[Unit] = {
    applicationRepo
      .addProgressStatusAndUpdateAppStatus(appForExpiry.applicationId, ProgressStatuses.SIFT_EXPIRED)
      .map(_ => Logger.info(s"Expiring Application: $appForExpiry"))
  }

  def expireCandidates(appsForExpiry: Seq[ApplicationForSiftExpiry]): Future[Unit] = {
    Future.sequence(appsForExpiry.map(app => expireCandidate(app))).map(_ => ())
  }

  def getSiftState(applicationId: String): Future[Option[SiftState]] = {
    for {
      progressStatusTimestamps <- applicationRepo.getProgressStatusTimestamps(applicationId)
      siftTestGroup <- applicationSiftRepo.getTestGroup(applicationId)

    } yield {
      val mappedStates = progressStatusTimestamps.toMap
      val siftEnteredDateOpt = mappedStates.get(SIFT_ENTERED.toString)

      // Both dates have to be present otherwise we return a None
      // testGroups.SIFT_PHASE.expirationDate is created as soon as the candidate moves into SIFT_ENTERED
      (siftEnteredDateOpt, siftTestGroup) match {
        case (Some(enteredDate), Some(testGroup)) =>
          Some(SiftState(siftEnteredDate = enteredDate, expirationDate = testGroup.expirationDate))
        case _ => None
      }
    }
  }

  private def notifyExpiredCandidate(applicationId: String)(implicit hc: HeaderCarrier): Future[Unit] = {
    applicationRepo.find(applicationId).flatMap {
      case Some(candidate) => contactDetailsRepo.find(candidate.userId).flatMap { contactDetails =>
        emailClient.sendSiftExpired(contactDetails.email, candidate.name).map(_ => ())
      }
      case None => throw CouldNotFindCandidateWithApplication(applicationId)
    }
  }

  private def sdipFaststreamSchemeFilter: PartialFunction[SchemeEvaluationResult, SchemeId] = {
    case s if s.result != Withdrawn.toString && s.result != Red.toString => s.schemeId
  }

  private def schemeFilter: PartialFunction[SchemeEvaluationResult, SchemeId] = {
    case s if s.result != Withdrawn.toString && s.result != Red.toString => s.schemeId
  }

  def sendSiftEnteredNotification(applicationId: String)(implicit hc: HeaderCarrier): Future[Unit] = {
    applicationRepo.find(applicationId).flatMap {
      case Some(candidate) => contactDetailsRepo.find(candidate.userId).flatMap { contactDetails =>
        emailClient.notifyCandidateSiftEnteredAdditionalQuestions(contactDetails.email, candidate.name).map(_ => ())
      }
      case None => throw CouldNotFindCandidateWithApplication(applicationId)
    }
  }

  private def siftApplicationForScheme(applicationId: String, result: SchemeEvaluationResult,
    updateBuilder: (Seq[SchemeEvaluationResult], Seq[SchemeEvaluationResult]) => Seq[BSONDocument]
  ): Future[Unit] = {
    (for {
      currentSchemeStatus <- applicationRepo.getCurrentSchemeStatus(applicationId)
      currentSiftEvaluation <- applicationSiftRepo.getSiftEvaluations(applicationId).recover { case _ => Nil }
    } yield {

      val settableFields = updateBuilder(currentSchemeStatus, currentSiftEvaluation)
      applicationSiftRepo.siftApplicationForScheme(applicationId, result, settableFields)

    }) flatMap identity
  }

  private def buildSiftSettableFields(result: SchemeEvaluationResult, schemeFilter: PartialFunction[SchemeEvaluationResult, SchemeId])
    (currentSchemeStatus: Seq[SchemeEvaluationResult], currentSiftEvaluation: Seq[SchemeEvaluationResult]
  ): Seq[BSONDocument] = {
    val newSchemeStatus = calculateCurrentSchemeStatus(currentSchemeStatus, result :: Nil)
    val candidatesGreenSchemes = currentSchemeStatus.collect { schemeFilter }
    val candidatesSiftableSchemes = schemeRepo.siftableAndEvaluationRequiredSchemeIds.filter(s => candidatesGreenSchemes.contains(s))
    val siftedSchemes = (currentSiftEvaluation.map(_.schemeId) :+ result.schemeId).distinct

    Seq(currentSchemeStatusBSON(newSchemeStatus),
      maybeSetProgressStatus(siftedSchemes.toSet, candidatesSiftableSchemes.toSet),
      maybeFailSdip(result),
      maybeSetSdipFaststreamProgressStatus(newSchemeStatus, siftedSchemes)
    ).foldLeft(Seq.empty[BSONDocument]) { (acc, doc) =>
      doc match {
        case _ @BSONDocument.empty => acc
        case _ => acc :+ doc
      }
    }
  }

  private def maybeSetProgressStatus(siftedSchemes: Set[SchemeId], candidatesSiftableSchemes: Set[SchemeId]) = {
    if (candidatesSiftableSchemes subsetOf siftedSchemes) {
      progressStatusOnlyBSON(ProgressStatuses.SIFT_COMPLETED)
    } else {
      BSONDocument.empty
    }
  }

  private def maybeFailSdip(result: SchemeEvaluationResult) = {
    if (Scheme.isSdip(result.schemeId) && result.result == Red.toString) {
      progressStatusOnlyBSON(ProgressStatuses.SDIP_FAILED_AT_SIFT)
    } else {
      BSONDocument.empty
    }
  }

  def getSiftEvaluations(applicationId: String): Future[Seq[SchemeEvaluationResult]] = {
    applicationSiftRepo.getSiftEvaluations(applicationId)
  }

  // we need to consider that all siftable schemes have been sifted with a fail or the candidate has withdrawn from them
  // and sdip has been sifted with a pass
  private def maybeSetSdipFaststreamProgressStatus(newSchemeStatus: Seq[SchemeEvaluationResult], siftedSchemes: Seq[SchemeId]) = {

    // Sdip has been sifted and it passed
    val SdipPassed = SchemeEvaluationResult(Scheme.SdipId, Green.toString)
    val sdipPassedSift = siftedSchemes.contains(Scheme.SdipId) && newSchemeStatus.contains(SdipPassed)

    val schemesExcludingSdip = newSchemeStatus.filterNot( s => s.schemeId == Scheme.SdipId)
    val faststreamSchemesRedOrWithdrawn = schemesExcludingSdip.forall{ s =>
      s.result == Red.toString || s.result == Withdrawn.toString
    }

    if (sdipPassedSift && faststreamSchemesRedOrWithdrawn) {
      progressStatusOnlyBSON(ProgressStatuses.SIFT_FASTSTREAM_FAILED_SDIP_GREEN)
    } else {
      BSONDocument.empty
    }
  }

  def findStuckUsersCalculateCorrectProgressStatus(currentSchemeStatus: Seq[SchemeEvaluationResult],
    currentSiftEvaluation: Seq[SchemeEvaluationResult]): BSONDocument = {

    val candidatesGreenSchemes = currentSchemeStatus.collect { schemeFilter }
    val candidatesSiftableSchemes = schemeRepo.siftableAndEvaluationRequiredSchemeIds.filter(s => candidatesGreenSchemes.contains(s))
    val siftedSchemes = currentSiftEvaluation.map(_.schemeId).distinct

    maybeSetProgressStatus(siftedSchemes.toSet, candidatesSiftableSchemes.toSet)
  }

  def findUsersInSiftReadyWhoShouldHaveBeenCompleted: Future[Seq[(FixStuckUser, Boolean)]] = {

    applicationSiftRepo.findAllUsersInSiftReady.map(_.map { potentialStuckUser =>
      val result = findStuckUsersCalculateCorrectProgressStatus(
        potentialStuckUser.currentSchemeStatus,
        potentialStuckUser.currentSiftEvaluation
      )

      (potentialStuckUser, !result.isEmpty)
    })
  }

  def fixUserInSiftReadyWhoShouldHaveBeenCompleted(applicationId: String): Future[Unit] = {
    (for {
      usersToFix <- findUsersInSiftReadyWhoShouldHaveBeenCompleted
    } yield {
      if (usersToFix.exists { case (user, shouldBeMoved) => user.applicationId == applicationId && shouldBeMoved }) {
        applicationRepo.addProgressStatusAndUpdateAppStatus(applicationId, ProgressStatuses.SIFT_COMPLETED)
      } else {
        throw new Exception(s"Application ID $applicationId is not available for fixing")
      }
    }).flatMap(identity)
  }

  def findUsersInSiftEnteredWhoShouldBeInSiftReadyWhoHaveFailedFormBasedSchemesInVideoPhase: Future[Seq[FixUserStuckInSiftEntered]] = {

    def includeUser(potentialStuckUser: FixUserStuckInSiftEntered): Boolean = {
      //  we include the candidate if their green schemes are either numeric_test or generalist / human resources
      // and there must be at least one numeric_test
      val greenSchemes = potentialStuckUser.currentSchemeStatus.filter( s => s.result == Green.toString)
      val allSchemesApplicable = greenSchemes.forall { s =>
        schemeRepo.nonSiftableSchemeIds.contains(s.schemeId) || schemeRepo.numericTestSiftRequirementSchemeIds.contains(s.schemeId)
      }
      val atLeastOneNumericTestScheme = greenSchemes.exists( s => schemeRepo.numericTestSiftRequirementSchemeIds.contains(s.schemeId) )
      allSchemesApplicable && atLeastOneNumericTestScheme
    }
    applicationSiftRepo.findAllUsersInSiftEntered.map( _.filter ( potentialStuckUser => includeUser(potentialStuckUser) ))
  }

  def fixUserInSiftEnteredWhoShouldBeInSiftReadyWhoHasFailedFormBasedSchemesInVideoPhase(applicationId: String): Future[Unit] = {
    (for {
      usersToFix <- findUsersInSiftEnteredWhoShouldBeInSiftReadyWhoHaveFailedFormBasedSchemesInVideoPhase
    } yield {
      if (usersToFix.exists { user => user.applicationId == applicationId }) {
        applicationRepo.addProgressStatusAndUpdateAppStatus(applicationId, ProgressStatuses.SIFT_READY)
      } else {
        throw new Exception(s"Application ID $applicationId is not available for fixing")
      }
    }).flatMap(identity)
  }

  // candidates who are in sift_entered who have withdrawn from all form based schemes and are still in the running
  // for at least one numeric scheme
  def findUsersInSiftEnteredWhoShouldBeInSiftReadyAfterWithdrawingFromAllFormBasedSchemes: Future[Seq[FixUserStuckInSiftEntered]] = {

    def includeUser(potentialStuckUser: FixUserStuckInSiftEntered): Boolean = {
      //  we include the candidate if their green schemes are either numeric_test or generalist / human resources
      // and there must be at least one numeric_test and they have form based schemes, which are all withdrawn
      val greenSchemes = potentialStuckUser.currentSchemeStatus.filter( s => s.result == Green.toString)

      // remaining green schemes require a numeric test or generalist / human resources
      val allSchemesApplicable = greenSchemes.forall { s =>
        schemeRepo.nonSiftableSchemeIds.contains(s.schemeId) || schemeRepo.numericTestSiftRequirementSchemeIds.contains(s.schemeId)
      }

      // the candidate must still be in the running for at least one numeric test scheme
      val atLeastOneNumericTestScheme = greenSchemes.exists( s => schemeRepo.numericTestSiftRequirementSchemeIds.contains(s.schemeId) )

      // must have form based schemes and be withdrawn from all of them
      val usersFormBasedSchemes = potentialStuckUser.currentSchemeStatus.filter { s =>
        schemeRepo.formMustBeFilledInSchemeIds.contains(s.schemeId)
      }
      val hasFormBasedSchemesAndAllWithdrawn = usersFormBasedSchemes.nonEmpty && usersFormBasedSchemes.forall( _.result == Withdrawn.toString )

      allSchemesApplicable && atLeastOneNumericTestScheme && hasFormBasedSchemesAndAllWithdrawn
    }
    applicationSiftRepo.findAllUsersInSiftEntered.map( _.filter ( potentialStuckUser => includeUser(potentialStuckUser) ))
  }

  def fixUserInSiftEnteredWhoShouldBeInSiftReadyAfterWithdrawingFromAllFormBasedSchemes(applicationId: String): Future[Unit] = {
    (for {
      usersToFix <- findUsersInSiftEnteredWhoShouldBeInSiftReadyAfterWithdrawingFromAllFormBasedSchemes
    } yield {
      if (usersToFix.exists { user => user.applicationId == applicationId }) {
        applicationRepo.addProgressStatusAndUpdateAppStatus(applicationId, ProgressStatuses.SIFT_READY)
      } else {
        throw new Exception(s"Application ID $applicationId is not available for fixing")
      }
    }).flatMap(identity)
  }

  def fixUserSiftedWithAFailByMistake(applicationId: String): Future[Unit] = {
    for {
      _ <- applicationSiftRepo.fixDataByRemovingSiftPhaseEvaluationAndFailureStatus(applicationId)
      _ <- applicationRepo.removeProgressStatuses(applicationId,
        List(ProgressStatuses.SIFT_COMPLETED, ProgressStatuses.FAILED_AT_SIFT, ProgressStatuses.FAILED_AT_SIFT_NOTIFIED))
    } yield ()
  }
}
// scalastyle:off