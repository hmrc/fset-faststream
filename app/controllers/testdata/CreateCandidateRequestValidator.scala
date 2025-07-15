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

package controllers.testdata

import javax.inject.{Inject, Singleton}
import model.ApplicationRoute.ApplicationRoute
import model.command.testdata.CreateCandidateRequest.CreateCandidateRequest
import model.{ApplicationRoute, ApplicationStatus, LocationId, SchemeId, Schemes}
import repositories.{LocationRepository, SchemeRepository}

case class ValidatorResult(result: Boolean, message: Option[String])

@Singleton
class CreateCandidateRequestValidator @Inject() (schemeRepository: SchemeRepository, locationRepository: LocationRepository) extends Schemes {

  def validate(request: CreateCandidateRequest): ValidatorResult = {
    if (!validateGis(request)) {
      ValidatorResult(result = false, Some("Request contains incompatible values for Gis"))
    } else if (!validateSdip(request)) {
      ValidatorResult(result = false, Some("Request contains incompatible values for Sdip"))
    } else if (!validateEdip(request)) {
      ValidatorResult(result = false, Some("Request contains incompatible values for Edip"))
    } else if (isSdipFaststream(request)) {
      ValidatorResult(result = false, Some("The SdipFaststream application route is invalid for the 2023/24 campaign"))
    } else if (!validateFastPass(request)) {
      ValidatorResult(result = false, Some("Request contains incompatible values for Fastpass"))
    } else if (!validateSchemes(request).isValid) {
      ValidatorResult(result = false, Some(s"Request contains invalid scheme name(s): ${validateSchemes(request).message}"))
    } else if (!validateLocations(request).isValid) {
      ValidatorResult(result = false, Some(s"Request contains invalid location(s): ${validateLocations(request).message}"))
    } else {
      ValidatorResult(result = true, None)
    }
  }

  case class SchemeValidation(isValid: Boolean, message: String)

  def validateSchemes(request: CreateCandidateRequest): SchemeValidation = {
    // Schemes that are still in schemes.yaml but which the business doesn't want to use
    val invalidSchemes = Seq(GovernmentCommunicationService)
    val validSchemes = schemeRepository.schemes.map( _.id ).filterNot(id => invalidSchemes.contains(id)).toSet
    val requestSchemes = request.schemeTypes.map( _.toSet ).getOrElse(Set.empty[SchemeId])
    val invalidRequestSchemes = requestSchemes diff validSchemes

    val maxNumberOfSchemes = 3
    if (invalidRequestSchemes.isEmpty && requestSchemes.size > maxNumberOfSchemes) {
      // The schemes are valid but there are too many
      SchemeValidation(isValid = false, s"The maximum number of schemes you can select is $maxNumberOfSchemes")
    } else {
      SchemeValidation(invalidRequestSchemes.isEmpty, invalidRequestSchemes.mkString(","))
    }
  }

  case class LocationValidation(isValid: Boolean, message: String)
  def validateLocations(request: CreateCandidateRequest): LocationValidation = {
    val validLocations = locationRepository.locations.map( _.id ).toSet
    val requestLocations = request.locationPreferences.map( _.toSet ).getOrElse(Set.empty[LocationId])
    val result = requestLocations diff validLocations
    LocationValidation(result.isEmpty, result.mkString(","))
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
      // Not applicable in FastStream 2022-2023
      //val isExpectedInternshipType = request.civilServantAndInternshipTypes.exists(_.contains(CivilServantAndInternshipType.SDIP.toString))
      // Not applicable in FastStream 2022-2023
      isCivilServant //&& isExpectedInternshipType
    }

    if (request.hasFastPass.getOrElse(false)) {
      containsCivilServantAndInternshipTypes && isValidStatusForFastPass
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

  // The application route is invalid for 23/24 campaign
  def isSdipFaststream(request: CreateCandidateRequest): Boolean =
    getApplicationRoute(request).contains(ApplicationRoute.SdipFaststream)

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
