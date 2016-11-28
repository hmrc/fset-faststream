package controllers

import java.time.format.DateTimeFormatter

import models.ApplicationRoute._

trait CampaignAwareController {
  this: BaseController =>

  val appRouteConfigMap: Map[ApplicationRoute, ApplicationRouteConfig]

  def isNewAccountsStarted(implicit applicationRoute: ApplicationRoute = Faststream) =
    appRouteConfigMap.get(applicationRoute).forall(_.newAccountsStarted)

  def isNewAccountsEnabled(implicit applicationRoute: ApplicationRoute = Faststream) =
    appRouteConfigMap.get(applicationRoute).forall(_.newAccountsEnabled)

  def isSubmitApplicationsEnabled(implicit applicationRoute: ApplicationRoute = Faststream) =
    appRouteConfigMap.get(applicationRoute).forall(_.applicationsSubmitEnabled)

  def getApplicationStartDate(implicit applicationRoute: ApplicationRoute = Faststream) =
    appRouteConfigMap.get(applicationRoute)
      .flatMap(_.applicationsStartDate.map(_.format(DateTimeFormatter.ofPattern("dd MMM YYYY"))))
      .getOrElse("")
}
