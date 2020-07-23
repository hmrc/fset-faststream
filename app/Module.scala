/*
 * Copyright 2020 HM Revenue & Customs
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

import com.google.inject.name.Names
import com.google.inject.{AbstractModule, Provider, TypeLiteral}
import connectors.{CSREmailClientImpl, OnlineTestEmailClient, Phase2OnlineTestEmailClient, Phase3OnlineTestEmailClient}
import model.exchange.passmarksettings.{Phase1PassMarkSettings, Phase2PassMarkSettings, Phase3PassMarkSettings}
import play.api.{Configuration, Environment, Logger}
import repositories._
import repositories.application._
import repositories.assessmentcentre.{AssessmentCentreMongoRepository, AssessmentCentreRepository}
import repositories.assistancedetails.{AssistanceDetailsMongoRepository, AssistanceDetailsRepository}
import repositories.campaignmanagement.CampaignManagementAfterDeadlineSignupCodeMongoRepository
import repositories.campaignmanagement.CampaignManagementAfterDeadlineSignupCodeRepository
import repositories.civilserviceexperiencedetails.{CivilServiceExperienceDetailsMongoRepository, CivilServiceExperienceDetailsRepository}
import repositories.contactdetails.{ContactDetailsMongoRepository, ContactDetailsRepository}
import repositories.events._
import repositories.fsacindicator.{FSACIndicatorMongoRepository, FSACIndicatorRepository}
import repositories.fsb.{FsbMongoRepository, FsbRepository}
import repositories.onlinetesting._
import repositories.personaldetails.{PersonalDetailsMongoRepository, PersonalDetailsRepository}
import repositories.schemepreferences.{SchemePreferencesMongoRepository, SchemePreferencesRepository}
import repositories.sift._
import repositories.stc.{StcEventMongoRepository, StcEventRepository}
import repositories.testdata.{ApplicationRemovalMongoRepository, ApplicationRemovalRepository}
import scheduler.Scheduler
import scheduler.onlinetesting.EvaluateOnlineTestResultService2
import services.assessmentscores._
import services.events.{EventsService, EventsServiceImpl}
import services.onlinetesting.OnlineTestService
import services.onlinetesting.phase1.{EvaluatePhase1ResultService2, Phase1TestService2}
import services.onlinetesting.phase2.{EvaluatePhase2ResultService2, Phase2TestService2}
import services.onlinetesting.phase3.{EvaluatePhase3ResultService, Phase3TestService}
import services.testdata.admin.{AdminCreatedStatusGenerator, AdminUserBaseGenerator}
import uk.gov.hmrc.play.config.ServicesConfig

class Module(val environment: Environment, val configuration: Configuration) extends AbstractModule with ServicesConfig {

  override val runModeConfiguration: Configuration = configuration
  override protected def mode = environment.mode

  //scalastyle:off method.length
  override def configure(): Unit = {
    startUpMessage()

    // Scheduled jobs
    bind(classOf[Scheduler]).asEagerSingleton()

    bind(classOf[AdminUserBaseGenerator]).annotatedWith(Names.named("AdminCreatedStatusGenerator"))
      .to(classOf[AdminCreatedStatusGenerator])

    bind(classOf[EventsService]).to(classOf[EventsServiceImpl]).asEagerSingleton()

    bind(classOf[EventsConfigRepository]).to(classOf[EventsConfigRepositoryImpl]).asEagerSingleton()
    bind(classOf[EventsRepository]).to(classOf[EventsMongoRepository]).asEagerSingleton()

    bind(classOf[LocationsWithVenuesRepository]).to(classOf[LocationsWithVenuesInMemoryYamlRepository]).asEagerSingleton()


    bind(classOf[MediaRepository]).to(classOf[MediaMongoRepository]).asEagerSingleton()
    bind(classOf[QuestionnaireRepository]).to(classOf[QuestionnaireMongoRepository]).asEagerSingleton()

    bind(classOf[GeneralApplicationRepository]).to(classOf[GeneralApplicationMongoRepository]).asEagerSingleton()

    bind(classOf[CampaignManagementAfterDeadlineSignupCodeRepository])
      .to(classOf[CampaignManagementAfterDeadlineSignupCodeMongoRepository]).asEagerSingleton()
    bind(classOf[ContactDetailsRepository]).to(classOf[ContactDetailsMongoRepository]).asEagerSingleton()

    bind(classOf[Phase1TestRepository]).to(classOf[Phase1TestMongoRepository]).asEagerSingleton()
    bind(classOf[Phase1TestRepository2]).to(classOf[Phase1TestMongoRepository2]).asEagerSingleton()
    bind(classOf[Phase2TestRepository]).to(classOf[Phase2TestMongoRepository]).asEagerSingleton()
    bind(classOf[Phase2TestRepository2]).to(classOf[Phase2TestMongoRepository2]).asEagerSingleton()

    bind(classOf[PersonalDetailsRepository]).to(classOf[PersonalDetailsMongoRepository]).asEagerSingleton()

    bind(classOf[CivilServiceExperienceDetailsRepository]).to(classOf[CivilServiceExperienceDetailsMongoRepository]).asEagerSingleton()
    bind(classOf[FSACIndicatorRepository]).to(classOf[FSACIndicatorMongoRepository]).asEagerSingleton()

    bind(classOf[SchemePreferencesRepository]).to(classOf[SchemePreferencesMongoRepository]).asEagerSingleton()

    bind(classOf[AssistanceDetailsRepository]).to(classOf[AssistanceDetailsMongoRepository]).asEagerSingleton()
    bind(classOf[FrameworkPreferenceRepository]).to(classOf[FrameworkPreferenceMongoRepository]).asEagerSingleton()

    bind(classOf[StcEventRepository]).to(classOf[StcEventMongoRepository]).asEagerSingleton()

    //Withdraw
    bind(classOf[AssessmentCentreRepository]).to(classOf[AssessmentCentreMongoRepository]).asEagerSingleton()
    bind(classOf[FsbRepository]).to(classOf[FsbMongoRepository]).asEagerSingleton()
    bind(classOf[Phase3TestRepository]).to(classOf[Phase3TestMongoRepository]).asEagerSingleton()
    bind(classOf[ApplicationSiftRepository]).to(classOf[ApplicationSiftMongoRepository]).asEagerSingleton()
    bind(classOf[SiftAnswersRepository]).to(classOf[SiftAnswersMongoRepository]).asEagerSingleton()

    bind(classOf[FinalOutcomeRepository]).to(classOf[FinaOutcomeMongoRepository]).asEagerSingleton()
    bind(classOf[AssessorsEventsSummaryJobsRepository]).to(classOf[AssessorsEventsSummaryJobsMongoRepository]).asEagerSingleton()

    //reporting
    bind(classOf[ReportingRepository]).to(classOf[ReportingMongoRepository]).asEagerSingleton()
    bind(classOf[PreviousYearCandidatesDetailsRepository]).to(classOf[PreviousYearCandidatesDetailsMongoRepository]).asEagerSingleton()
    bind(classOf[CandidateAllocationRepository]).to(classOf[CandidateAllocationMongoRepository]).asEagerSingleton()

    bind(classOf[AssessorRepository]).to(classOf[AssessorMongoRepository]).asEagerSingleton()
    bind(classOf[AssessorAllocationRepository]).to(classOf[AssessorAllocationMongoRepository]).asEagerSingleton()
    bind(classOf[DiagnosticReportingRepository]).to(classOf[DiagnosticReportingMongoRepository]).asEagerSingleton()

    bind(classOf[FlagCandidateRepository]).to(classOf[FlagCandidateMongoRepository]).asEagerSingleton()

    bind(classOf[String])
      .annotatedWith(Names.named("appName"))
      .toProvider(new ConfigProvider("appName"))

    // Bind the named implementations for the online test service
    bind(classOf[OnlineTestService]).annotatedWith(Names.named("Phase1OnlineTestService"))
      .to(classOf[Phase1TestService2])
    bind(classOf[OnlineTestService]).annotatedWith(Names.named("Phase2OnlineTestService"))
      .to(classOf[Phase2TestService2])
    bind(classOf[OnlineTestService]).annotatedWith(Names.named("Phase3OnlineTestService"))
      .to(classOf[Phase3TestService])

    // Bind the named implementations for the online test repository
    bind(classOf[OnlineTestRepository]).annotatedWith(Names.named("Phase1OnlineTestRepo"))
      .to(classOf[Phase1TestMongoRepository2])
    bind(classOf[OnlineTestRepository]).annotatedWith(Names.named("Phase2OnlineTestRepo"))
      .to(classOf[Phase2TestMongoRepository2])
    bind(classOf[OnlineTestRepository]).annotatedWith(Names.named("Phase3OnlineTestRepo"))
      .to(classOf[Phase3TestMongoRepository])

    // You need TypeLiterals to keep the parameterised type information for guice to bind at runtime
    bind(new TypeLiteral[EvaluateOnlineTestResultService2[Phase1PassMarkSettings]] {})
      .annotatedWith(Names.named("Phase1EvaluationService"))
      .to(classOf[EvaluatePhase1ResultService2])
    bind(new TypeLiteral[EvaluateOnlineTestResultService2[Phase2PassMarkSettings]] {})
      .annotatedWith(Names.named("Phase2EvaluationService"))
      .to(classOf[EvaluatePhase2ResultService2])
    bind(new TypeLiteral[EvaluateOnlineTestResultService2[Phase3PassMarkSettings]] {})
      .annotatedWith(Names.named("Phase3EvaluationService"))
      .to(classOf[EvaluatePhase3ResultService])

      // Bind the named implementations for the online test evaluation repositories
    bind(classOf[OnlineTestEvaluationRepository]).annotatedWith(Names.named("Phase1EvaluationRepository"))
      .to(classOf[Phase1EvaluationMongoRepository])
    bind(classOf[OnlineTestEvaluationRepository]).annotatedWith(Names.named("Phase2EvaluationRepository"))
      .to(classOf[Phase2EvaluationMongoRepository])
    bind(classOf[OnlineTestEvaluationRepository]).annotatedWith(Names.named("Phase3EvaluationRepository"))
      .to(classOf[Phase3EvaluationMongoRepository])

    // Bind the named implementations for the email clients
    bind(classOf[OnlineTestEmailClient]).annotatedWith(Names.named("CSREmailClient"))
      .to(classOf[CSREmailClientImpl])
    bind(classOf[OnlineTestEmailClient]).annotatedWith(Names.named("Phase2OnlineTestEmailClient"))
      .to(classOf[Phase2OnlineTestEmailClient])
    bind(classOf[OnlineTestEmailClient]).annotatedWith(Names.named("Phase3OnlineTestEmailClient"))
      .to(classOf[Phase3OnlineTestEmailClient])

    // Bind assessor implementations of common traits using @Named annotation
    bind(classOf[AssessmentScoresService]).annotatedWith(Names.named("AssessorAssessmentScoresService"))
      .to(classOf[AssessorAssessmentScoresServiceImpl])
    bind(classOf[AssessmentScoresRepository]).annotatedWith(Names.named("AssessorAssessmentScoresRepo"))
      .to(classOf[AssessorAssessmentScoresMongoRepository])
    // Bind reviewer implementations of common traits using @Named annotation
    bind(classOf[AssessmentScoresService]).annotatedWith(Names.named("ReviewerAssessmentScoresService"))
      .to(classOf[ReviewerAssessmentScoresServiceImpl])
    bind(classOf[AssessmentScoresRepository]).annotatedWith(Names.named("ReviewerAssessmentScoresRepo"))
      .to(classOf[ReviewerAssessmentScoresMongoRepository])

    bind(classOf[ApplicationRemovalRepository]).to(classOf[ApplicationRemovalMongoRepository]).asEagerSingleton()
  } //scalastyle:on

  private def startUpMessage() = {
    val appName = configuration.getString("appName").orElse(throw new RuntimeException(s"No configuration value found for 'appName'"))
    Logger.info(s"Starting micro service ${appName.getOrElse("NOT-SET")} in mode ${environment.mode}")
  }

  private class ConfigProvider(key: String) extends Provider[String] {
    override lazy val get = configuration
      .getString(key)
      .getOrElse(throw new IllegalStateException(s"No value found for configuration property $key"))
  }
}
