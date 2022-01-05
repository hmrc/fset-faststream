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

package controllers

import config.ApplicationRouteFrontendConfig
import models.ApplicationRoute._
import org.joda.time.DateTime
import play.api.Logging

import java.time.format.DateTimeFormatter
import java.time.{ LocalDateTime, ZoneId }

trait ApplicationRouteState {
  def newAccountsStarted: Boolean
  def newAccountsEnabled: Boolean
  def applicationsSubmitEnabled: Boolean
  def applicationsStartDate: Option[LocalDateTime]
}

case class ApplicationRouteStateImpl(config: ApplicationRouteFrontendConfig) extends ApplicationRouteState with Logging {
  require(
    config.startNewAccountsDate.forall(startDate => config.blockNewAccountsDate.forall(_.isAfter(startDate))),
    "start new accounts date must be before block new accounts date"
  )

  def newAccountsStarted: Boolean = isBeforeNow(config.startNewAccountsDate)
  def newAccountsEnabled: Boolean = isAfterNow(config.blockNewAccountsDate)
  def applicationsSubmitEnabled: Boolean = isAfterNow(config.blockApplicationsDate)
  def applicationsStartDate: Option[LocalDateTime] = config.startNewAccountsDate

  val zoneId = config.timeZone.map(ZoneId.of).getOrElse(ZoneId.systemDefault())

  def now = LocalDateTime.now(zoneId)

  def isAfterNow(date: Option[LocalDateTime]) = {
    val result = date forall (_.isAfter(now))
    logger.warn(s"isAfterNow check: checking if given closing date($date) in timezone($zoneId) is after now($now) - " +
      s"result=$result (false indicates we are closed)")
    result
  }

  def isBeforeNow(date: Option[LocalDateTime]) = date forall (_.isBefore(now))
}

trait CampaignAwareController {
  this: BaseController =>

  val appRouteConfigMap: Map[ApplicationRoute, ApplicationRouteState]

  def isNewAccountsStarted(implicit applicationRoute: ApplicationRoute = Faststream): Boolean =
    appRouteConfigMap.get(applicationRoute).forall(_.newAccountsStarted)

  def isNewAccountsEnabled(implicit applicationRoute: ApplicationRoute = Faststream): Boolean =
    appRouteConfigMap.get(applicationRoute).forall(_.newAccountsEnabled)

  def canApplicationBeSubmitted(overriddenSubmissionDeadline: Option[DateTime])
                               (implicit applicationRoute: ApplicationRoute = Faststream): Boolean = {
    isSubmitApplicationsEnabled match {
      case true => true
      case false if overriddenSubmissionDeadline.isDefined => overriddenSubmissionDeadline.get.isAfter(DateTime.now)
      case _ => false
    }
  }

  private def isSubmitApplicationsEnabled(implicit applicationRoute: ApplicationRoute): Boolean =
    appRouteConfigMap.get(applicationRoute).forall(_.applicationsSubmitEnabled)

  def getApplicationStartDate(implicit applicationRoute: ApplicationRoute = Faststream): String =
    appRouteConfigMap.get(applicationRoute)
      .flatMap(_.applicationsStartDate.map(_.format(DateTimeFormatter.ofPattern("dd MMM YYYY HH:mm:ss a"))))
      .getOrElse("")
}
