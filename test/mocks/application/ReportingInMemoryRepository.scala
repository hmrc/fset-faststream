/*
 * Copyright 2017 HM Revenue & Customs
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

package mocks.application


import model.Commands._
import model._
import model.persisted._
import model.report._
import org.joda.time.LocalDate
import repositories.application.ReportingRepository

import scala.concurrent.Future

object ReportingInMemoryRepository extends ReportingInMemoryRepository

/**
  * @deprecated Please use Mockito
  */
class ReportingInMemoryRepository extends ReportingRepository {

  override def applicationsReport(frameworkId: String): Future[List[(String, IsNonSubmitted, PreferencesWithContactDetails)]] = {
    val app1 = ("user1", true, PreferencesWithContactDetails(Some("John"), Some("Smith"), Some("Jo"),
      Some("user1@email.com"), None, Some("location1"), Some("location1Scheme1"), Some("location1Scheme2"), Some("location2"),
      Some("location2Scheme1"), Some("location2Scheme2"), None, Some("2016-12-25 13:00:14")))
    val app2 = ("user2", true, PreferencesWithContactDetails(Some("John"), Some("Smith"), Some("Jo"),
      None, None, None, None, None, None, None, None, None, None))
    Future.successful(app1 :: app2 :: Nil)
  }

  override def candidateProgressReportNotWithdrawn(frameworkId: String): Future[List[CandidateProgressReportItem]] =
    candidateProgressReport(frameworkId)

  override def candidateProgressReport(frameworkId: String): Future[List[CandidateProgressReportItem]] = Future.successful(List(
    CandidateProgressReportItem("","", Some("registered"),
      List(SchemeType.DigitalAndTechnology, SchemeType.Commercial), None, None, None, None, None, None, None, None, None, None,
      None, None, ApplicationRoute.Faststream))
  )

  override def diversityReport(frameworkId: String): Future[List[ApplicationForDiversityReport]] = ???

  override def onlineTestPassMarkReport(frameworkId: String): Future[List[ApplicationForOnlineTestPassMarkReport]] = ???

  override def adjustmentReport(frameworkId: String): Future[List[AdjustmentReportItem]] =
    Future.successful(
      List(
        AdjustmentReportItem("1", Some("11"), Some("John"), Some("Smith"), Some("Spiderman"), None, None, Some("Yes"),
          Some(ApplicationStatus.SUBMITTED), Some("Need help for online tests"), Some("Need help at the venue"),
          Some("Yes"), Some("A wooden leg"),
          Some(Adjustments(Some(List("etrayTimeExtension")),Some(true),Some(AdjustmentDetail(Some(55),None,None)),None)),None),
        AdjustmentReportItem("2", Some("22"), Some("Jones"), Some("Batman"), None, None, None, None,
          Some(ApplicationStatus.PHASE1_TESTS), None, Some("Need help at the venue"), None, None,
          Some(Adjustments(Some(List("etrayTimeExtension")),Some(true),Some(AdjustmentDetail(Some(55),None,None)),None)),None),
        AdjustmentReportItem("3", Some("33"), Some("Kathrine"), Some("Jones"), Some("Supergirl"), None, None, None,
          Some(ApplicationStatus.PHASE1_TESTS_PASSED), Some("Need help for online tests"), None,
          Some("Yes"), Some("A glass eye"),
          Some(Adjustments(Some(List("etrayTimeExtension")),Some(true),Some(AdjustmentDetail(Some(55),None,None)),None)),None)
      )
    )

  override def allApplicationAndUserIds(frameworkId: String): Future[List[PersonalDetailsAdded]] = ???

  override def candidatesAwaitingAllocation(frameworkId: String): Future[List[CandidateAwaitingAllocation]] =
    Future.successful(
      List(
        CandidateAwaitingAllocation("1", "John", "Smith", "Spiderman", "London", Some("Some adjustments"), new LocalDate(1988, 1, 21)),
        CandidateAwaitingAllocation("2", "James", "Jones", "Batman", "Bournemouth", Some("Some adjustments"), new LocalDate(1992, 11, 30)),
        CandidateAwaitingAllocation("3", "Katherine", "Jones", "Supergirl", "Queer Camel", None, new LocalDate(1990, 2, 12))
      )
    )

  override def overallReportNotWithdrawnWithPersonalDetails(frameworkId: String): Future[List[ReportWithPersonalDetails]] = ???

  override def candidateDeferralReport(frameworkId: String): Future[List[ApplicationDeferralPartialItem]] = ???

  def candidatesForDuplicateDetectionReport: Future[List[UserApplicationProfile]] = ???
}
