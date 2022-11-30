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

package connectors

import connectors.exchange.referencedata.{ Degree, Scheme, SchemeId, SiftRequirement }

object ReferenceDataExamples {

  object Schemes {
    val Commercial = Scheme("Commercial", "CFS", "Commercial", civilServantEligible = false, degree = None, Some(SiftRequirement.NUMERIC_TEST),
      siftEvaluationRequired = true, fsbType = None, schemeGuide = None, schemeQuestion = None)
    val DDTaC = Scheme("CyberSecurity", "DDTAC", "Cyber Security", civilServantEligible = false,
      degree = None, Some(SiftRequirement.FORM), siftEvaluationRequired = true, fsbType = None, schemeGuide = None, schemeQuestion = None)
    val Dip = Scheme("DiplomaticAndDevelopment", "DS", "Diplomatic and Development", civilServantEligible = false, degree = None,
      Some(SiftRequirement.FORM), siftEvaluationRequired = true, fsbType = None, schemeGuide = None, schemeQuestion = None)
    val  DipEcon = Scheme("DiplomaticAndDevelopmentEconomics", "GES-DS", "Diplomatic and Development Economics", civilServantEligible = false,
      degree = None, Some(SiftRequirement.FORM), siftEvaluationRequired = true, fsbType = None, schemeGuide = None, schemeQuestion = None)
    val Finance = Scheme("Finance", "FIFS", "Finance", civilServantEligible = false, degree = None, Some(SiftRequirement.NUMERIC_TEST),
      siftEvaluationRequired = true, fsbType = None, schemeGuide = None, schemeQuestion = None)
    val Generalist = Scheme("Generalist", "GFS", "Generalist",civilServantEligible = false, degree = None, siftRequirement = None,
      siftEvaluationRequired = false, fsbType = None, schemeGuide = None, schemeQuestion = None)
    val GovComms = Scheme("GovernmentCommunicationService", "GCFS", "Government Communication Service", civilServantEligible = false,
      degree = None, Some(SiftRequirement.FORM),  siftEvaluationRequired = true, fsbType = None, schemeGuide = None, schemeQuestion = None)
    val GovEconomics = Scheme("GovernmentEconomicsService", "GES", "Government Economics Service", civilServantEligible = false,
      degree = None, Some(SiftRequirement.FORM), siftEvaluationRequired = true, fsbType = None, schemeGuide = None, schemeQuestion = None)
    val GovOps = Scheme("GovernmentOperationalResearchService", "GORS", "Government Operational Research Service", civilServantEligible = false,
      degree = None, Some(SiftRequirement.FORM), siftEvaluationRequired = true, fsbType = None, schemeGuide = None, schemeQuestion = None)
    val GovSocialResearch = Scheme("GovernmentSocialResearchService", "GSR", "Government Social Research Service", civilServantEligible = false,
      degree = None, Some(SiftRequirement.FORM), siftEvaluationRequired = true, fsbType = None, schemeGuide = None, schemeQuestion = None)
    val GovStats = Scheme("GovernmentStatisticalService", "GSS", "Government Statistical Service", civilServantEligible = false,
      degree = None, Some(SiftRequirement.FORM), siftEvaluationRequired = true, fsbType = None, schemeGuide = None, schemeQuestion = None)
    val HoP = Scheme("HousesOfParliament", "HoP", "Houses Of Parliament", civilServantEligible = false, degree = None,
      Some(SiftRequirement.FORM), siftEvaluationRequired = true, fsbType = None, schemeGuide = None, schemeQuestion = None)
    val HR = Scheme("HumanResources", "HRFS", "Human Resources", civilServantEligible = false, degree = None, siftRequirement = None,
      siftEvaluationRequired = false, fsbType = None, schemeGuide = None, schemeQuestion = None)
    val ProjectDelivery = Scheme("ProjectDelivery", "PDFS", "Project Delivery", civilServantEligible = false,
      degree = Some(Degree(required = "Degree_CharteredEngineer", specificRequirement = true)),
      Some(SiftRequirement.FORM), siftEvaluationRequired = true, fsbType = None, schemeGuide = None, schemeQuestion = None)
    val SciEng = Scheme("ScienceAndEngineering", "SEFS", "Science And Engineering", civilServantEligible = false, degree = None,
      Some(SiftRequirement.FORM), siftEvaluationRequired = true, fsbType = None, schemeGuide = None, schemeQuestion = None)
    val Edip = Scheme("Edip", "EDIP", "Early Diversity Internship Programme", civilServantEligible = false, degree = None,
      Some(SiftRequirement.FORM), siftEvaluationRequired = true, fsbType = None, schemeGuide = None, schemeQuestion = None)
    val Sdip = Scheme("Sdip", "SDIP", "Summer Diversity Internship Programme", civilServantEligible = false, degree = None,
      Some(SiftRequirement.FORM), siftEvaluationRequired = false, fsbType = None, schemeGuide = None, schemeQuestion = None)

    val AllSchemes = (Commercial :: DDTaC :: Dip :: DipEcon :: Finance :: Generalist :: GovComms :: GovEconomics :: GovOps ::
      GovSocialResearch :: GovStats :: HoP :: HR :: ProjectDelivery :: SciEng :: Edip :: Sdip :: Nil)
      .filterNot( s => s.id == SchemeId("GovernmentCommunicationService")) // Filter out GFCS for 2021 campaign

    val SomeSchemes = Commercial :: DDTaC :: Dip :: Nil

    def schemesWithNoDegree = AllSchemes.filter( _.degree.isEmpty )
  }
}
