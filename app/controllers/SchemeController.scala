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

package controllers

import _root_.forms.SchemeLocationPreferenceForm.{ form => preferenceForm, resetPreference, validateSchemeLocation }
import _root_.forms.{ AlternateLocationsForm, SchemeLocationPreferenceForm }
import config.CSRHttp
import connectors.SchemeClient.CannotFindSelection
import connectors.{ ApplicationClient, SchemeClient }
import helpers.NotificationType._
import models.CachedDataWithApp
import models.frameworks.LocationAndSchemeSelection.empty
import models.frameworks.{ LocationAndSchemeSelection, Region }
import play.api.data.Form
import play.api.mvc.{ Request, Result }
import play.twirl.api.Html
import security.Roles.SchemesRole
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

object SchemeController extends SchemeController {
  val http = CSRHttp
}

trait SchemeController extends BaseController with SchemeClient with ApplicationClient {

  def entryPoint = CSRSecureAppAction(SchemesRole) { implicit request =>
    implicit user =>
      withRegions { regions =>
        getSelection(user.application.applicationId).map {
          case LocationAndSchemeSelection(_, Some(true), None, None) =>
            Redirect(routes.SchemeController.secondPreference())

          case sel @ LocationAndSchemeSelection(fp, _, _, _) if validateSchemeLocation(fp, regions).nonEmpty =>
            val errs = validateSchemeLocation(fp, regions)
            Ok(views.html.application.scheme.firstPreference(
              preferenceForm.fill(resetPreference(fp, errs)), sel, notification = Some(danger(errs.head))
            )(regions))

          case sel @ LocationAndSchemeSelection(_, _, Some(sp), _) if validateSchemeLocation(sp, regions).nonEmpty =>
            val errs = validateSchemeLocation(sp, regions)
            Ok(views.html.application.scheme.secondPreference(
              preferenceForm.fill(resetPreference(sp, errs)), sel, notification = Some(danger(errs.head))
            )(regions))

          case _ =>
            Redirect(routes.SchemeController.summary())

        }.recover {
          case e: CannotFindSelection =>
            Ok(views.html.application.scheme.firstPreference(preferenceForm.fill(empty.firstLocation), empty)(regions))
        }
      }
  }

  def firstPreference = schemeFlowAction { (sel, regions) =>
    implicit request => implicit user =>
      Future.successful(Ok(views.html.application.scheme.firstPreference(preferenceForm.fill(sel.firstLocation), sel)(regions)))
  }

  def saveFirstPreference = savePreference { (sel, regions, invalidForm) =>
    implicit request => implicit user => implicit hc =>
      views.html.application.scheme.firstPreference(invalidForm, sel)(regions)
  } { data =>
    implicit request => implicit user => implicit hc =>
      updateFirstPref(data)(user.application.applicationId)
  }

  def secondPreference = schemeFlowAction { (sel, regions) =>
    implicit request => implicit user =>
      val form = sel.secondLocation match {
        case Some(pref) => preferenceForm.fill(pref)
        case None => preferenceForm.fill(
          SchemeLocationPreferenceForm.Data("", "", None)
        )
      }
      Future.successful(Ok(views.html.application.scheme.secondPreference(form, sel)(regions)))
  }

  def saveSecondPreference = savePreference { (sel, regions, invalidForm) =>
    implicit request => implicit user => implicit hc =>
      views.html.application.scheme.secondPreference(invalidForm, sel)(regions)
  } { (data) =>
    implicit request => implicit user => implicit hc =>
      updateSecondPref(data)(user.application.applicationId)
  }

  def considerSecondPreference(consider: Boolean) = CSRSecureAppAction(SchemesRole) { implicit request =>
    implicit user =>
      updateNoSecondPref(noSecPref = consider)(user.application.applicationId).map(_ => Redirect(routes.SchemeController.summary()))
  }

  def summary = CSRSecureAppAction(SchemesRole) { implicit request =>
    implicit user =>
      getSelection(user.application.applicationId).map { sel =>
        Future.successful(Ok(views.html.application.scheme.summary(
          sel.alternatives.map(AlternateLocationsForm.form.fill(_)), sel
        )))
      }.flatMap(identity).recover {
        case e: CannotFindSelection => Redirect(routes.SchemeController.entryPoint())
      }

  }

  def saveConsiderAlternatives = schemeFlowAction { (sel, regions) =>
    implicit request => implicit user =>
      AlternateLocationsForm.form.bindFromRequest.fold(
        invalidForm =>
          Future.successful(Ok(views.html.application.scheme.summary(Some(invalidForm), sel))),
        data =>
          updateAlternatives(data)(user.application.applicationId).flatMap { _ =>
            updateProgress()(_ => Redirect(routes.AssistanceController.present()))
          }
      )
  }

  // format: OFF
  type errorFuncSig = (LocationAndSchemeSelection, List[Region], Form[SchemeLocationPreferenceForm.Data])
    => Request[_]
    => CachedDataWithApp
    => HeaderCarrier
    => Html

  type successFuncSig = (SchemeLocationPreferenceForm.Data)
    => Request[_]
    => CachedDataWithApp
    => HeaderCarrier
    => Future[Unit]
  // format: ON

  private def savePreference(error: errorFuncSig)(success: successFuncSig) = CSRSecureAppAction(SchemesRole) { implicit request =>
    implicit user =>
      withRegions { regions =>
        def handleForm(sel: LocationAndSchemeSelection) = preferenceForm.bindFromRequest.fold(
          invalidForm =>
            Future.successful(Ok(error(sel, regions, invalidForm)(request)(user)(hc))),
          data => {
            val errs = validateSchemeLocation(data, regions)
            if (errs.isEmpty) {
              success(data)(request)(user)(hc).map(_ => Redirect(routes.SchemeController.entryPoint()))
            } else {
              val invalidForm = errs.foldLeft(preferenceForm.fill(data))((form, err) => form.withError(err, err))
              Future.successful(Ok(error(sel, regions, invalidForm)(request)(user)(hc)))
            }
          }
        )

        getSelection(user.application.applicationId).map(handleForm).recover {
          case e: CannotFindSelection =>
            handleForm(empty)
        }.flatMap(identity)
      }
  }

  private def withRegions(f: List[Region] => Future[Result])(implicit user: CachedDataWithApp, hc: HeaderCarrier, request: Request[_]) = {
    getAvailableFrameworksWithLocations(user.application.applicationId).flatMap { regions =>
      f(regions)
    }
  }

  type schemeFlowActionType = (LocationAndSchemeSelection, List[Region]) => Request[_] => CachedDataWithApp => Future[Result]

  private def schemeFlowAction(f: schemeFlowActionType) = CSRSecureAppAction(SchemesRole) { implicit request =>
    implicit user =>
      withRegions { regions =>
        getSelection(user.application.applicationId).map { sel =>
          f(sel, regions)(request)(user)
        }.flatMap(identity).recover {
          case e: CannotFindSelection => Redirect(routes.SchemeController.entryPoint())
        }
      }
  }
}
