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

package scheduler

import play.api.Logger
import scheduler.assessment.{ EvaluateAssessmentScoreJob, EvaluateAssessmentScoreJobConfig }
import scheduler.fixer.{ FixerJob, FixerJobConfig }
import scheduler.fsb.{ EvaluateFsbJob, EvaluateFsbJobConfig }
import scheduler.onlinetesting._
import scheduler.sift.{ FirstSiftReminderJob, FirstSiftReminderJobConfig, SecondSiftReminderJob, SecondSiftReminderJobConfig }
import uk.gov.hmrc.play.scheduling.{ RunningOfScheduledJobs, ScheduledJob }

trait Scheduler extends RunningOfScheduledJobs {

  private def maybeInitScheduler(config: BasicJobConfig[_], scheduler: => ScheduledJob): Option[ScheduledJob] = {
    if (config.enabled) Some(scheduler) else {
      Logger.warn(s"${config.name} job is disabled")
      None
    }
  }

  lazy val scheduledJobs: List[ScheduledJob] =  List(
    maybeInitScheduler(SendPhase1InvitationJobConfig, SendPhase1InvitationJob),
    maybeInitScheduler(SendPhase2InvitationJobConfig, SendPhase2InvitationJob),
    maybeInitScheduler(SendPhase3InvitationJobConfig, SendPhase3InvitationJob),
    maybeInitScheduler(ExpirePhase1TestJobConfig, ExpirePhase1TestJob),
    maybeInitScheduler(ExpirePhase2TestJobConfig, ExpirePhase2TestJob),
    maybeInitScheduler(ExpirePhase3TestJobConfig, ExpirePhase3TestJob),
    maybeInitScheduler(FirstPhase1ReminderExpiringTestJobConfig, FirstPhase1ReminderExpiringTestJob),
    maybeInitScheduler(SecondPhase1ReminderExpiringTestJobConfig, SecondPhase1ReminderExpiringTestJob),
    maybeInitScheduler(FirstPhase2ReminderExpiringTestJobConfig, FirstPhase2ReminderExpiringTestJob),
    maybeInitScheduler(SecondPhase2ReminderExpiringTestJobConfig, SecondPhase2ReminderExpiringTestJob),
    maybeInitScheduler(FirstPhase3ReminderExpiringTestJobConfig, FirstPhase3ReminderExpiringTestJob),
    maybeInitScheduler(SecondPhase1ReminderExpiringTestJobConfig, SecondPhase3ReminderExpiringTestJob),
    maybeInitScheduler(FailedPhase1TestJobConfig, FailedPhase1TestJob),
    maybeInitScheduler(FailedPhase2TestJobConfig, FailedPhase2TestJob),
    maybeInitScheduler(FailedPhase3TestJobConfig, FailedPhase3TestJob),
    maybeInitScheduler(FailedSdipFsTestJobConfig, FailedSdipFsTestJob),
    maybeInitScheduler(SuccessPhase1TestJobConfig, SuccessPhase1TestJob),
    maybeInitScheduler(SuccessPhase3TestJobConfig, SuccessPhase3TestJob),
    maybeInitScheduler(SuccessPhase3SdipFsTestJobConfig, SuccessPhase3SdipFsTestJob),
    maybeInitScheduler(RetrievePhase1ResultsJobConfig, RetrievePhase1ResultsJob),
    maybeInitScheduler(RetrievePhase2ResultsJobConfig, RetrievePhase2ResultsJob),
    maybeInitScheduler(EvaluatePhase1ResultJobConfig, EvaluatePhase1ResultJob),
    maybeInitScheduler(EvaluatePhase2ResultJobConfig, EvaluatePhase2ResultJob),
    maybeInitScheduler(EvaluatePhase3ResultJobConfig, EvaluatePhase3ResultJob),
    maybeInitScheduler(EvaluateAssessmentScoreJobConfig, EvaluateAssessmentScoreJob),
    maybeInitScheduler(FixerJobConfig, FixerJob),
    maybeInitScheduler(ProgressSdipForFaststreamCandidateJobConfig, ProgressSdipForFaststreamCandidateJob),
    maybeInitScheduler(ProgressToSiftJobConfig, ProgressToSiftJob),
    maybeInitScheduler(ProgressToAssessmentCentreJobConfig, ProgressToAssessmentCentreJob),
    maybeInitScheduler(NotifyAssessorsOfNewEventsJobConfig, NotifyAssessorsOfNewEventsJob),
    maybeInitScheduler(FirstSiftReminderJobConfig, FirstSiftReminderJob),
    maybeInitScheduler(SecondSiftReminderJobConfig, SecondSiftReminderJob),
    maybeInitScheduler(SiftFailureJobConfig, SiftFailureJob),
    maybeInitScheduler(SiftExpiryJobConfig, SiftExpiryJob),
    maybeInitScheduler(ProgressToFsbOrOfferJobConfig, ProgressToFsbOrOfferJob),
    maybeInitScheduler(ReminderEventAllocationJobConfig, ReminderEventAllocationJob),
    maybeInitScheduler(NotifyOnFinalFailureJobConfig, NotifyOnFinalFailureJob),
    maybeInitScheduler(NotifyOnFinalSuccessJobConfig, NotifyOnFinalSuccessJob),
    maybeInitScheduler(EvaluateFsbJobConfig, EvaluateFsbJob)
  ).flatten
}
