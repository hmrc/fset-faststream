/*
 * Copyright 2023 HM Revenue & Customs
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

package scheduler

import play.api.Application
import play.api.inject.ApplicationLifecycle
import scheduler.assessment.{EvaluateAssessmentCentreJobConfig, EvaluateAssessmentCentreJobImpl}
import scheduler.fixer.FixerJobImpl
import scheduler.fsb.{EvaluateFsbJobConfig, EvaluateFsbJobImpl}
import scheduler.onlinetesting.*
import scheduler.scheduling.{RunningOfScheduledJobs, ScheduledJob}
import scheduler.sift.*

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class Scheduler @Inject()(
                           sendPhase1InvitationJob: SendPhase1InvitationJob,
//                           sendPhase2InvitationJob: SendPhase2InvitationJob,
//                           sendPhase3InvitationJob: SendPhase3InvitationJob,
                           expirePhase1TestJob: ExpirePhase1TestJob,
//                           expirePhase2TestJob: ExpirePhase2TestJob,
//                           expirePhase3TestJob: ExpirePhase3TestJob,
                           firstPhase1ReminderExpiringTestJob: FirstPhase1ReminderExpiringTestJob,
                           secondPhase1ReminderExpiringTestJob: SecondPhase1ReminderExpiringTestJob,
//                           firstPhase2ReminderExpiringTestJob: FirstPhase2ReminderExpiringTestJob,
//                           secondPhase2ReminderExpiringTestJob: SecondPhase2ReminderExpiringTestJob,
//                           firstPhase3ReminderExpiringTestJob: FirstPhase3ReminderExpiringTestJob,
//                           secondPhase3ReminderExpiringTestJob: SecondPhase3ReminderExpiringTestJob,
                           failedPhase1TestJob: FailedPhase1TestJob,
//                           failedPhase2TestJob: FailedPhase2TestJob,
//                           failedPhase3TestJob: FailedPhase3TestJob,
//                           failedSdipFsTestJob: FailedSdipFsTestJob,
                           successPhase1TestJob: SuccessPhase1TestJob,
//                           successPhase3TestJob: SuccessPhase3TestJob,
//                           successPhase3SdipFsTestJob: SuccessPhase3SdipFsTestJob,
                           evaluatePhase1ResultJob: EvaluatePhase1ResultJob,
//                           evaluatePhase2ResultJob: EvaluatePhase2ResultJob,
//                           evaluatePhase3ResultJob: EvaluatePhase3ResultJob,
                           evaluateAssessmentCentreJob: EvaluateAssessmentCentreJobImpl,
                           fixerJob: FixerJobImpl,
                           skipPhasesJob: SkipPhasesJobImpl,
//                           skipPhase3Job: SkipPhase3JobImpl,
//                           fixSdipFsP3SkippedCandidatesJob: FixSdipFsP3SkippedCandidatesJobImpl,
//                           progressSdipForFaststreamCandidateJob: ProgressSdipForFaststreamCandidateJobImpl,
                           progressToSiftJob: ProgressToSiftJobImpl,
//                           siftNumericalTestInvitationJob: SiftNumericalTestInvitationJobImpl,
//                           processSiftNumericalResultsReceivedJob: ProcessSiftNumericalResultsReceivedJobImpl,
                           progressToAssessmentCentreJob: ProgressToAssessmentCentreJobImpl,
                           notifyAssessorsOfNewEventsJob: NotifyAssessorsOfNewEventsJobImpl,
                           firstSiftReminderJob: FirstSiftReminderJobImpl,
                           secondSiftReminderJob: SecondSiftReminderJobImpl,
                           siftFailureJob: SiftFailureJob,
                           siftExpiryJob: SiftExpiryJobImpl,
                           progressToFsbOrOfferJob: ProgressToFsbOrOfferJobImpl,
                           reminderEventAllocationJob: ReminderEventAllocationJobImpl,
                           notifyOnFinalFailureJob: NotifyOnFinalFailureJobImpl,
                           notifyOnFinalSuccessJob: NotifyOnFinalSuccessJobImpl,
                           evaluateFsbJob: EvaluateFsbJobImpl,
                           fsbOverallFailureJob: FsbOverallFailureJob,
                           sendPhase1InvitationJobConfig: SendPhase1InvitationJobConfig,
//                           sendPhase2InvitationJobConfig: SendPhase2InvitationJobConfig,
//                           sendPhase3InvitationJobConfig: SendPhase3InvitationJobConfig,
                           expirePhase1TestJobConfig: ExpirePhase1TestJobConfig,
//                           expirePhase2TestJobConfig: ExpirePhase2TestJobConfig,
//                           expirePhase3TestJobConfig: ExpirePhase3TestJobConfig,
                           firstPhase1ReminderExpiringTestJobConfig: FirstPhase1ReminderExpiringTestJobConfig,
                           secondPhase1ReminderExpiringTestJobConfig: SecondPhase1ReminderExpiringTestJobConfig,
//                           firstPhase2ReminderExpiringTestJobConfig: FirstPhase2ReminderExpiringTestJobConfig,
//                           secondPhase2ReminderExpiringTestJobConfig: SecondPhase2ReminderExpiringTestJobConfig,
//                           firstPhase3ReminderExpiringTestJobConfig: FirstPhase3ReminderExpiringTestJobConfig,
//                           secondPhase3ReminderExpiringTestJobConfig: SecondPhase3ReminderExpiringTestJobConfig,
                           failedPhase1TestJobConfig: FailedPhase1TestJobConfig,
//                           failedPhase2TestJobConfig: FailedPhase2TestJobConfig,
//                           failedPhase3TestJobConfig: FailedPhase3TestJobConfig,
//                           failedSdipFsTestJobConfig: FailedSdipFsTestJobConfig,
                           successPhase1TestJobConfig: SuccessPhase1TestJobConfig,
//                           successPhase3TestJobConfig: SuccessPhase3TestJobConfig,
//                           successPhase3SdipFsTestJobConfig: SuccessPhase3SdipFsTestJobConfig,
                           evaluatePhase1ResultJobConfig: EvaluatePhase1ResultJobConfig,
//                           evaluatePhase2ResultJobConfig: EvaluatePhase2ResultJobConfig,
//                           evaluatePhase3ResultJobConfig: EvaluatePhase3ResultJobConfig,
                           evaluateAssessmentCentreJobConfig: EvaluateAssessmentCentreJobConfig,
//                           fixerJobConfig: FixerJobConfig,
                           skipPhasesJobConfig: SkipPhasesJobConfig,
//                           skipPhase3JobConfig: SkipPhase3JobConfig,
//                           fixSdipFsP3SkippedCandidatesConfig: FixSdipFsP3SkippedCandidatesConfig,
                           progressSdipForFaststreamCandidateJobConfig: ProgressSdipForFaststreamCandidateJobConfig,
                           progressToSiftJobConfig: ProgressToSiftJobConfig,
//                           siftNumericalTestInvitationConfig: SiftNumericalTestInvitationConfig,
//                           processSiftNumericalResultsReceivedJobConfig: ProcessSiftNumericalResultsReceivedJobConfig,
                           progressToAssessmentCentreJobConfig: ProgressToAssessmentCentreJobConfig,
                           notifyAssessorsOfNewEventsJobConfig: NotifyAssessorsOfNewEventsJobConfig,
                           firstSiftReminderJobConfig: FirstSiftReminderJobConfig,
                           secondSiftReminderJobConfig: SecondSiftReminderJobConfig,
                           siftFailureJobConfig: SiftFailureJobConfig,
                           siftExpiryJobConfig: SiftExpiryJobConfig,
                           progressToFsbOrOfferJobConfig: ProgressToFsbOrOfferJobConfig,
                           reminderEventAllocationJobConfig: ReminderEventAllocationJobConfig,
                           notifyOnFinalFailureJobConfig: NotifyOnFinalFailureJobConfig,
                           notifyOnFinalSuccessJobConfig: NotifyOnFinalSuccessJobConfig,
                           evaluateFsbJobConfig: EvaluateFsbJobConfig,
                           fsbOverallFailureJobConfig: FsbOverallFailureJobConfig,
                           override val application: Application,
                           override val applicationLifecycle: ApplicationLifecycle
                         )
                         (override implicit val ec: ExecutionContext) extends RunningOfScheduledJobs(application, applicationLifecycle) {
  logger.info("Scheduler created")

  private def maybeInitScheduler(config: BasicJobConfig[_], scheduler: => ScheduledJob): Option[ScheduledJob] = {
    if (config.enabled) {
      logger.warn(s"${config.jobName} job is enabled")
      Some(scheduler)
    } else {
      logger.warn(s"${config.jobName} job is disabled")
      None
    }
  }

  override def scheduledJobs: Seq[ScheduledJob] = {
    Seq(
      maybeInitScheduler(sendPhase1InvitationJobConfig, sendPhase1InvitationJob),
//      maybeInitScheduler(sendPhase2InvitationJobConfig, sendPhase2InvitationJob),
//      maybeInitScheduler(sendPhase3InvitationJobConfig, sendPhase3InvitationJob),
      maybeInitScheduler(expirePhase1TestJobConfig, expirePhase1TestJob),
//      maybeInitScheduler(expirePhase2TestJobConfig, expirePhase2TestJob),
//      maybeInitScheduler(expirePhase3TestJobConfig, expirePhase3TestJob),
      maybeInitScheduler(firstPhase1ReminderExpiringTestJobConfig, firstPhase1ReminderExpiringTestJob),
      maybeInitScheduler(secondPhase1ReminderExpiringTestJobConfig, secondPhase1ReminderExpiringTestJob),
//      maybeInitScheduler(firstPhase2ReminderExpiringTestJobConfig, firstPhase2ReminderExpiringTestJob),
//      maybeInitScheduler(secondPhase2ReminderExpiringTestJobConfig, secondPhase2ReminderExpiringTestJob),
//      maybeInitScheduler(firstPhase3ReminderExpiringTestJobConfig, firstPhase3ReminderExpiringTestJob),
//      maybeInitScheduler(secondPhase3ReminderExpiringTestJobConfig, secondPhase3ReminderExpiringTestJob),
      maybeInitScheduler(failedPhase1TestJobConfig, failedPhase1TestJob),
//      maybeInitScheduler(failedPhase2TestJobConfig, failedPhase2TestJob),
//      maybeInitScheduler(failedPhase3TestJobConfig, failedPhase3TestJob),
//      maybeInitScheduler(failedSdipFsTestJobConfig, failedSdipFsTestJob),
      maybeInitScheduler(successPhase1TestJobConfig, successPhase1TestJob),
//      maybeInitScheduler(successPhase3TestJobConfig, successPhase3TestJob),
//      maybeInitScheduler(successPhase3SdipFsTestJobConfig, successPhase3SdipFsTestJob),
      maybeInitScheduler(evaluatePhase1ResultJobConfig, evaluatePhase1ResultJob),
//      maybeInitScheduler(evaluatePhase2ResultJobConfig, evaluatePhase2ResultJob),
//      maybeInitScheduler(evaluatePhase3ResultJobConfig, evaluatePhase3ResultJob),
      maybeInitScheduler(evaluateAssessmentCentreJobConfig, evaluateAssessmentCentreJob),
//      maybeInitScheduler(fixerJobConfig, fixerJob),
      maybeInitScheduler(skipPhasesJobConfig, skipPhasesJob),
//      maybeInitScheduler(skipPhase3JobConfig, skipPhase3Job),
//      maybeInitScheduler(fixSdipFsP3SkippedCandidatesConfig, fixSdipFsP3SkippedCandidatesJob),
//      maybeInitScheduler(progressSdipForFaststreamCandidateJobConfig, progressSdipForFaststreamCandidateJob),
      maybeInitScheduler(progressToSiftJobConfig, progressToSiftJob),
//      maybeInitScheduler(siftNumericalTestInvitationConfig, siftNumericalTestInvitationJob),
//      maybeInitScheduler(processSiftNumericalResultsReceivedJobConfig, processSiftNumericalResultsReceivedJob),
      maybeInitScheduler(progressToAssessmentCentreJobConfig, progressToAssessmentCentreJob),
      maybeInitScheduler(notifyAssessorsOfNewEventsJobConfig, notifyAssessorsOfNewEventsJob),
      maybeInitScheduler(firstSiftReminderJobConfig, firstSiftReminderJob),
      maybeInitScheduler(secondSiftReminderJobConfig, secondSiftReminderJob),
      maybeInitScheduler(siftFailureJobConfig, siftFailureJob),
      maybeInitScheduler(siftExpiryJobConfig, siftExpiryJob),
      maybeInitScheduler(progressToFsbOrOfferJobConfig, progressToFsbOrOfferJob),
      maybeInitScheduler(reminderEventAllocationJobConfig, reminderEventAllocationJob),
      maybeInitScheduler(notifyOnFinalFailureJobConfig, notifyOnFinalFailureJob),
      maybeInitScheduler(notifyOnFinalSuccessJobConfig, notifyOnFinalSuccessJob),
      maybeInitScheduler(evaluateFsbJobConfig, evaluateFsbJob),
      maybeInitScheduler(fsbOverallFailureJobConfig, fsbOverallFailureJob)
    ).flatten
  }
}
