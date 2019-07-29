/*
 * Copyright 2019 HM Revenue & Customs
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

package controllers.testdata

import model.ApplicationRoute.ApplicationRoute
import model.command.testdata.CreateCandidateRequest.CreateCandidateRequest
import model.{ApplicationRoute, ApplicationStatus}

case class ValidatorResult(val result: Boolean, val message: Option[String])

object CreateCandidateRequestValidator extends CreateCandidateRequestValidator

trait CreateCandidateRequestValidator {
  def validate(request: CreateCandidateRequest): ValidatorResult = {
    val route = request.statusData.applicationRoute.map(ApplicationRoute.withName(_))
    val status = ApplicationStatus.withName(request.statusData.applicationStatus)
    val progressStatus = request.statusData.progressStatus
    val hasFastPass = request.hasFastPass
    val isCivilServant = request.isCivilServant
    val internshipTypes = request.internshipTypes

    if (!validateGis(request)) {
      ValidatorResult(false, Some("Request contains incompatible values for Gis"))
    } else if (!validateSdip(request)) {
      ValidatorResult(false, Some("Request contains incompatible values for Sdip"))
    } else if (!validateEdip(request)) {
      ValidatorResult(false, Some("Request contains incompatible values for Edip"))
    } else if (!validateFastPass(request)) {
      ValidatorResult(false, Some("Request contains incompatible values for Fastpass"))
    } else {
      ValidatorResult(true, None)
    }
  }

  def validateFastPass(request: CreateCandidateRequest): Boolean = {
    val route = getApplicationRoute(request)
    val status = getApplicationStatus(request)

    def isValidStatusForFastPass = {

      def containsPhase2AndPhase3Statuses = List(ApplicationStatus.PHASE2_TESTS, ApplicationStatus.PHASE2_TESTS_FAILED,
        ApplicationStatus.PHASE2_TESTS_PASSED, ApplicationStatus.PHASE3_TESTS, ApplicationStatus.PHASE3_TESTS_FAILED,
        ApplicationStatus.PHASE3_TESTS_PASSED, ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED,
        ApplicationStatus.PHASE3_TESTS_PASSED_WITH_AMBER).contains(status)

      def containsPhase1Statuses = List(ApplicationStatus.PHASE1_TESTS, ApplicationStatus.PHASE1_TESTS_FAILED,
        ApplicationStatus.PHASE1_TESTS_PASSED, ApplicationStatus.PHASE1_TESTS_PASSED_NOTIFIED
      ).contains(status)

      if (List(ApplicationRoute.Edip, ApplicationRoute.Sdip).contains(route)) {
        !containsPhase2AndPhase3Statuses
      } else {
        !containsPhase2AndPhase3Statuses && !containsPhase1Statuses
      }
    }

    def containsCivilServantAndInternshipTypes = {
      val isCivilServant = request.isCivilServant.getOrElse(false)
      val isExpectedInternshipType = request.internshipTypes.exists(_.contains("SDIPCurrentYear"))
      isCivilServant && isExpectedInternshipType
    }

    if (request.hasFastPass.getOrElse(false)) {
      (containsCivilServantAndInternshipTypes && isValidStatusForFastPass)
    } else {
      true
    }
  }

  def validateSdip(request: CreateCandidateRequest): Boolean = {
    validateSdipOrEdip(request, ApplicationRoute.Sdip)
  }

  def validateEdip(request: CreateCandidateRequest): Boolean = {
    validateSdipOrEdip(request, ApplicationRoute.Edip)
  }

  def validateSdipOrEdip(request: CreateCandidateRequest, routeToValidate: ApplicationRoute) = {
    val requestedRoute = getApplicationRoute(request)
    val status = getApplicationStatus(request)

    def isInvalidApplicationStatusForRoute = {
      List(ApplicationStatus.PHASE2_TESTS, ApplicationStatus.PHASE2_TESTS_FAILED, ApplicationStatus.PHASE2_TESTS_PASSED,
        ApplicationStatus.PHASE3_TESTS, ApplicationStatus.PHASE3_TESTS_FAILED, ApplicationStatus.PHASE3_TESTS_PASSED,
        ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED, ApplicationStatus.PHASE3_TESTS_PASSED_WITH_AMBER).contains(status)
    }

    def containsNoSdipSchemes = {
      request.schemeTypes.map(_.filterNot(schemeType => {
        Some(schemeType.value) == requestedRoute.map(_.toString)
      }).size > 0).getOrElse(false)
    }

    def containsNoSdipSchemePhase1TestData = {
      request.phase1TestData.flatMap(_.passmarkEvaluation.map(
        _.result.map(_.schemeId)
          .filterNot(schemeId => Some(schemeId.value) == requestedRoute.map(_.toString))
      )).toList.flatten.size > 0
    }

    def areApplicationStatusSchemesAndTestResultsApplicableForApplicationRoute: Boolean = {
      if (Some(routeToValidate) == requestedRoute) {
        if (isInvalidApplicationStatusForRoute || containsNoSdipSchemes || containsNoSdipSchemePhase1TestData) {
          false
        } else {
          true
        }
      } else {
        true
      }
    }
    areApplicationStatusSchemesAndTestResultsApplicableForApplicationRoute
  }

  def validateGis(request: CreateCandidateRequest) = {
    request.assistanceDetails.map(assistanceDetails =>
      if ((assistanceDetails.hasDisability == Some("No") || assistanceDetails.hasDisability == None)
        && assistanceDetails.setGis == Some(true)) {
        false
      } else {
        true
      }).getOrElse(true)
  }

  def getApplicationRoute(request: CreateCandidateRequest) = {
    request.statusData.applicationRoute.map(ApplicationRoute.withName(_))
  }

  def getApplicationStatus(request: CreateCandidateRequest) = {
    ApplicationStatus.withName(request.statusData.applicationStatus)
  }

}
