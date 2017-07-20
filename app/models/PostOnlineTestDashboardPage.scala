package models

import connectors.exchange.SchemeEvaluationResult
import models.ApplicationData.ApplicationStatus.ApplicationStatus

case class PostOnlineTestDashboardPage(
  applicationStatus: ApplicationStatus,
  phase3EvaluationResults: Seq[SchemeEvaluationResult]
)
