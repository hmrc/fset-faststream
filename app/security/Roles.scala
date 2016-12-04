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

package security

import controllers.routes
import models.ApplicationData.ApplicationStatus._
import models.{ ApplicationRoute, CachedData, CachedDataWithApp, Progress }
import play.api.i18n.Lang
import play.api.mvc.{ Call, RequestHeader }
import security.QuestionnaireRoles.QuestionnaireInProgressRole
import uk.gov.hmrc.play.http.HeaderCarrier

// scalastyle:off
object Roles {

  import RoleUtils._

  trait CsrAuthorization {
    def isAuthorized(user: CachedData)(implicit request: RequestHeader, lang: Lang): Boolean

    def isAuthorized(user: CachedDataWithApp)(implicit request: RequestHeader, lang: Lang): Boolean =
      isAuthorized(CachedData(user.user, Some(user.application)))
  }

  trait AuthorisedUser extends CsrAuthorization {
    def isEnabled(user: CachedData)(implicit request: RequestHeader, lang: Lang): Boolean

    override def isAuthorized(user: CachedData)(implicit request: RequestHeader, lang: Lang) =
      activeUserWithApp(user) && isEnabled(user)
  }

  // All the roles
  object NoRole extends CsrAuthorization {
    override def isAuthorized(user: CachedData)(implicit request: RequestHeader, lang: Lang) = true
  }

  object ActivationRole extends CsrAuthorization {
    override def isAuthorized(user: CachedData)(implicit request: RequestHeader, lang: Lang) =
      !user.user.isActive
  }

  object ActiveUserRole extends CsrAuthorization {
    override def isAuthorized(user: CachedData)(implicit request: RequestHeader, lang: Lang) =
      user.user.isActive
  }

  object ApplicationStartRole extends CsrAuthorization {
    override def isAuthorized(user: CachedData)(implicit request: RequestHeader, lang: Lang) =
      user.user.isActive && (user.application.isEmpty || statusIn(user)(CREATED))
  }

  object EditPersonalDetailsAndContinueRole extends CsrAuthorization {
    override def isAuthorized(user: CachedData)(implicit request: RequestHeader, lang: Lang) =
      activeUserWithApp(user) && statusIn(user)(CREATED, IN_PROGRESS)
  }

  object CreatedOrInProgressRole extends CsrAuthorization {
    override def isAuthorized(user: CachedData)(implicit request: RequestHeader, lang: Lang) =
      activeUserWithApp(user) && statusIn(user)(CREATED, IN_PROGRESS)
  }

  object EditPersonalDetailsRole extends CsrAuthorization {
    override def isAuthorized(user: CachedData)(implicit request: RequestHeader, lang: Lang) =
      activeUserWithApp(user) && !statusIn(user)(WITHDRAWN)
  }

  object SchemesRole extends CsrAuthorization {
    override def isAuthorized(user: CachedData)(implicit request: RequestHeader, lang: Lang) =
      activeUserWithApp(user) && statusIn(user)(IN_PROGRESS) && hasPersonalDetails(user)
  }

  object PartnerGraduateProgrammesRole extends CsrAuthorization {
    override def isAuthorized(user: CachedData)(implicit request: RequestHeader, lang: Lang) =
      activeUserWithApp(user) && statusIn(user)(IN_PROGRESS) && (hasSchemes(user) && !isCivilServant(user))
  }

  object ContinueAsSdipRole extends CsrAuthorization {
    override def isAuthorized(user: CachedData)(implicit request: RequestHeader, lang: Lang) =
      isFaststreamOnly(user) && (user.application.isEmpty || statusIn(user)(WITHDRAWN) || !isSubmitted(user) || isTestExpired(user))
  }

  object AssistanceDetailsRole extends CsrAuthorization {
    override def isAuthorized(user: CachedData)(implicit request: RequestHeader, lang: Lang) =
      activeUserWithApp(user) && statusIn(user)(IN_PROGRESS) &&
        (
          hasPartnerGraduateProgrammes(user) ||
            (hasSchemes(user) && isCivilServant(user)) ||
            (hasPersonalDetails(user) && (isEdip(user) || isSdip(user)))
          )
  }

  object PreviewApplicationRole extends CsrAuthorization {
    override def isAuthorized(user: CachedData)(implicit request: RequestHeader, lang: Lang) =
    activeUserWithApp(user) && !statusIn(user)(CREATED) &&
        hasDiversity(user) && hasEducation(user) && hasOccupation(user)
  }

  object SubmitApplicationRole extends CsrAuthorization {
    override def isAuthorized(user: CachedData)(implicit request: RequestHeader, lang: Lang) =
      activeUserWithApp(user) && statusIn(user)(IN_PROGRESS) && hasPreview(user)
  }

  object InProgressRole extends CsrAuthorization {
    override def isAuthorized(user: CachedData)(implicit request: RequestHeader, lang: Lang) =
      activeUserWithApp(user) && statusIn(user)(IN_PROGRESS)
  }

  object WithdrawApplicationRole extends CsrAuthorization {
    override def isAuthorized(user: CachedData)(implicit request: RequestHeader, lang: Lang) =
      activeUserWithApp(user) && !statusIn(user)(IN_PROGRESS, WITHDRAWN, CREATED)
  }

  object WithdrawnApplicationRole extends CsrAuthorization {
    override def isAuthorized(user: CachedData)(implicit request: RequestHeader, lang: Lang) =
      activeUserWithApp(user) && statusIn(user)(WITHDRAWN)
  }

  object OnlineTestInvitedRole extends CsrAuthorization {
    override def isAuthorized(user: CachedData)(implicit request: RequestHeader, lang: Lang) =
      activeUserWithApp(user) && statusIn(user)(PHASE1_TESTS)
  }

  object OnlineTestExpiredRole extends CsrAuthorization {
    override def isAuthorized(user: CachedData)(implicit request: RequestHeader, lang: Lang) =
      activeUserWithApp(user) && statusIn(user)(PHASE1_TESTS) && isTestExpired(user)
  }

  object Phase1TestFailedRole extends CsrAuthorization {
    override def isAuthorized(user: CachedData)(implicit request: RequestHeader, lang: Lang) =
      activeUserWithApp(user) && statusIn(user)(PHASE1_TESTS_FAILED)
  }

  object Phase2TestFailedRole extends CsrAuthorization {
    override def isAuthorized(user: CachedData)(implicit request: RequestHeader, lang: Lang) =
      activeUserWithApp(user) && statusIn(user)(PHASE2_TESTS_FAILED)
  }

  object Phase2TestInvitedRole extends CsrAuthorization {
    override def isAuthorized(user: CachedData)(implicit request: RequestHeader, lang: Lang) =
      activeUserWithApp(user) && statusIn(user)(PHASE2_TESTS)
  }

  object Phase2TestExpiredRole extends CsrAuthorization {
    override def isAuthorized(user: CachedData)(implicit request: RequestHeader, lang: Lang) =
      activeUserWithApp(user) && statusIn(user)(PHASE2_TESTS) && isPhase2TestExpired(user)
  }

  object Phase3TestInvitedRole extends CsrAuthorization {
    override def isAuthorized(user: CachedData)(implicit request: RequestHeader, lang: Lang) =
      activeUserWithApp(user) && statusIn(user)(PHASE3_TESTS)
  }

  object Phase3TestExpiredRole extends CsrAuthorization {
    override def isAuthorized(user: CachedData)(implicit request: RequestHeader, lang: Lang) =
      activeUserWithApp(user) && statusIn(user)(PHASE3_TESTS) && isPhase3TestExpired(user)
  }

  object Phase3TestFailedRole extends CsrAuthorization {
    override def isAuthorized(user: CachedData)(implicit request: RequestHeader, lang: Lang) =
      activeUserWithApp(user) && statusIn(user)(PHASE3_TESTS_FAILED)
  }

  object DisplayOnlineTestSectionRole extends CsrAuthorization {
    // format: OFF
    override def isAuthorized(user: CachedData)(implicit request: RequestHeader, lang: Lang) =
      activeUserWithApp(user) && statusIn(user)(PHASE1_TESTS,
        ALLOCATION_CONFIRMED, ALLOCATION_UNCONFIRMED, AWAITING_ALLOCATION, FAILED_TO_ATTEND,
        ASSESSMENT_SCORES_ENTERED, ASSESSMENT_SCORES_ACCEPTED, AWAITING_ASSESSMENT_CENTRE_RE_EVALUATION, ASSESSMENT_CENTRE_PASSED,
        ASSESSMENT_CENTRE_FAILED, ASSESSMENT_CENTRE_PASSED_NOTIFIED, ASSESSMENT_CENTRE_FAILED_NOTIFIED)

    // format: ON
  }

  object ConfirmedAllocatedCandidateRole extends CsrAuthorization {
    override def isAuthorized(user: CachedData)(implicit request: RequestHeader, lang: Lang) =
      activeUserWithApp(user) && statusIn(user)(ALLOCATION_CONFIRMED, ASSESSMENT_SCORES_ACCEPTED,
        ASSESSMENT_SCORES_ENTERED, AWAITING_ASSESSMENT_CENTRE_RE_EVALUATION)
  }

  object UnconfirmedAllocatedCandidateRole extends CsrAuthorization {
    override def isAuthorized(user: CachedData)(implicit request: RequestHeader, lang: Lang) =
      activeUserWithApp(user) && statusIn(user)(ALLOCATION_UNCONFIRMED)
  }

  object AssessmentCentreFailedNotifiedRole extends CsrAuthorization {
    override def isAuthorized(user: CachedData)(implicit request: RequestHeader, lang: Lang) =
      activeUserWithApp(user) && statusIn(user)(ASSESSMENT_CENTRE_FAILED_NOTIFIED)
  }

  object AssessmentCentrePassedNotifiedRole extends CsrAuthorization {
    override def isAuthorized(user: CachedData)(implicit request: RequestHeader, lang: Lang) =
      activeUserWithApp(user) && statusIn(user)(ASSESSMENT_CENTRE_PASSED_NOTIFIED)
  }

  object AssessmentCentreFailedToAttendRole extends AuthorisedUser {
    override def isEnabled(user: CachedData)(implicit request: RequestHeader, lang: Lang) =
      statusIn(user)(FAILED_TO_ATTEND)
  }

  object WithdrawComponent extends AuthorisedUser {
    override def isEnabled(user: CachedData)(implicit request: RequestHeader, lang: Lang) =
      !statusIn(user)(IN_PROGRESS, WITHDRAWN, CREATED, ASSESSMENT_CENTRE_FAILED, ASSESSMENT_CENTRE_FAILED_NOTIFIED) &&
        !isSdipFaststream(user)
  }

  val userJourneySequence: List[(CsrAuthorization, Call)] = List(
    ApplicationStartRole -> routes.HomeController.present(),
    EditPersonalDetailsAndContinueRole -> routes.PersonalDetailsController.presentAndContinue(),
    SchemesRole -> routes.SchemePreferencesController.present(),
    PartnerGraduateProgrammesRole -> routes.PartnerGraduateProgrammesController.present(),
    AssistanceDetailsRole -> routes.AssistanceDetailsController.present(),
    QuestionnaireInProgressRole -> routes.QuestionnaireController.presentStartOrContinue(),
    PreviewApplicationRole -> routes.PreviewApplicationController.present(),
    SubmitApplicationRole -> routes.PreviewApplicationController.present(),
    DisplayOnlineTestSectionRole -> routes.HomeController.present(),
    ConfirmedAllocatedCandidateRole -> routes.HomeController.present(),
    UnconfirmedAllocatedCandidateRole -> routes.HomeController.present(),
    WithdrawApplicationRole -> routes.HomeController.present()
  ).reverse
}

object RoleUtils {

  implicit def hc(implicit request: RequestHeader): HeaderCarrier = HeaderCarrier.fromHeadersAndSession(request.headers, Some(request.session))

  def activeUserWithApp(user: CachedData)(implicit request: RequestHeader, lang: Lang) =
    user.user.isActive && user.application.isDefined

  def statusIn(user: CachedData)(status: ApplicationStatus*)(implicit request: RequestHeader, lang: Lang) =
    user.application.isDefined && status.contains(user.application.get.applicationStatus)

  def progress(implicit user: CachedData): Progress = user.application.get.progress

  def hasPersonalDetails(implicit user: CachedData) = progress.personalDetails

  def hasSchemes(implicit user: CachedData) = user.application.isDefined && progress.schemePreferences

  def hasPartnerGraduateProgrammes(implicit user: CachedData) = progress.partnerGraduateProgrammes

  def hasAssistanceDetails(implicit user: CachedData) = user.application.isDefined && progress.assistanceDetails

  def hasStartedQuest(implicit user: CachedData) = progress.startedQuestionnaire

  def hasDiversity(implicit user: CachedData) = progress.diversityQuestionnaire

  def hasEducation(implicit user: CachedData) = progress.educationQuestionnaire

  def hasOccupation(implicit user: CachedData) = progress.occupationQuestionnaire

  def hasPreview(implicit user: CachedData) = progress.preview

  def isSubmitted(implicit user: CachedData) = progress.submitted

  def isCivilServant(user: CachedData)(implicit request: RequestHeader, lang: Lang) =
    user.application
      .flatMap(_.civilServiceExperienceDetails)
      .exists(_.isCivilServant)

  def hasReceivedFastPass(user: CachedData)(implicit request: RequestHeader, lang: Lang) =
    activeUserWithApp(user) && statusIn(user)(SUBMITTED) &&
      user.application
        .flatMap(_.civilServiceExperienceDetails)
        .flatMap(_.fastPassReceived)
        .getOrElse(false)

  def isTestExpired(implicit user: CachedData) = progress.phase1TestProgress.phase1TestsExpired

  def isPhase1TestsPassed(implicit user: CachedData) = {
    user.application.isDefined && progress.phase1TestProgress.phase1TestsPassed
  }

  def isPhase2TestsPassed(implicit user: CachedData) = {
    user.application.isDefined && progress.phase2TestProgress.phase2TestsPassed
  }

  def isPhase3TestsPassed(implicit user: CachedData) = {
    user.application.isDefined && progress.phase3TestProgress.phase3TestsPassed
  }

  def isPhase2TestExpired(implicit user: CachedData) = progress.phase2TestProgress.phase2TestsExpired

  def isPhase3TestExpired(implicit user: CachedData) = progress.phase3TestProgress.phase3TestsExpired

  def isFaststream(implicit user: CachedDataWithApp) = user.application.applicationRoute == ApplicationRoute.Faststream

  def isEdip(implicit user: CachedDataWithApp) = user.application.applicationRoute == ApplicationRoute.Edip

  def isSdip(implicit user: CachedDataWithApp) = user.application.applicationRoute == ApplicationRoute.Sdip

  def isFaststream(implicit user: CachedData): Boolean = user.application.forall { app =>
    app.applicationRoute == ApplicationRoute.Faststream || app.applicationRoute == ApplicationRoute.SdipFaststream
  }

  def isFaststreamOnly(implicit user: CachedData): Boolean = user.application.forall { app =>
    app.applicationRoute == ApplicationRoute.Faststream
  }

  def isEdip(implicit user: CachedData): Boolean = user.application exists (_.applicationRoute == ApplicationRoute.Edip)

  def isSdip(implicit user: CachedData): Boolean = user.application exists (_.applicationRoute == ApplicationRoute.Sdip)

  def isSdipFaststream(implicit user: CachedData): Boolean = user.application exists (_.applicationRoute == ApplicationRoute.SdipFaststream)

  def isFaststream(implicit user: Option[CachedData]): Boolean = user.forall(u => isFaststream(u))

  def isEdip(implicit user: Option[CachedData]): Boolean = user.exists(isEdip(_))

  def isSdip(implicit user: Option[CachedData]): Boolean = user.exists(isSdip(_))
}

// scalastyle:on