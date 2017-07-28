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

package connectors

import connectors.exchange.referencedata.{ Scheme, SiftRequirement }

object ReferenceDataExamples {

  object Schemes {
    val Commercial = Scheme("Commercial", "CFS", "Commercial", Some(SiftRequirement.NUMERIC_TEST), siftEvaluationRequired = true)
    val DaT = Scheme("DigitalAndTechnology", "DaT", "Digital And Technology", Some(SiftRequirement.FORM), siftEvaluationRequired = true)
    val Dip = Scheme("DiplomaticService", "DS", "Diplomatic Service",  Some(SiftRequirement.FORM), siftEvaluationRequired = true)
    val Finance = Scheme("Finance", "FIFS", "Finance", Some(SiftRequirement.NUMERIC_TEST), siftEvaluationRequired = true)
    val Generalist = Scheme("Generalist", "GFS", "Generalist", None, siftEvaluationRequired = false)
    val GovComms = Scheme("GovernmentCommunicationService", "GCFS", "Government Communication Service", Some(SiftRequirement.FORM),
      siftEvaluationRequired = true)
    val GovEconomics = Scheme("GovernmentEconomicsService", "GES", "Government Economics Service", Some(SiftRequirement.FORM),
      siftEvaluationRequired = true)
    val GovOps = Scheme("GovernmentOperationalResearchService", "GORS", "Government Operational Research Service",  Some(SiftRequirement.FORM),
      siftEvaluationRequired = true)
    val GovSocialResearch = Scheme("GovernmentSocialResearchService", "GSR", "Government Social Research Service", Some(SiftRequirement.FORM),
      siftEvaluationRequired = true)
    val GovStats = Scheme("GovernmentStatisticalService", "GSS", "Government Statistical Service", Some(SiftRequirement.FORM),
      siftEvaluationRequired = true)
    val HoP = Scheme("HousesOfParliament", "HoP", "Houses Of Parliament", Some(SiftRequirement.FORM), siftEvaluationRequired = true)
    val HR = Scheme("HumanResources", "HRFS", "Human Resources", None, siftEvaluationRequired = false)
    val ProjectDelivery = Scheme("ProjectDelivery", "PDFS", "Project Delivery", Some(SiftRequirement.FORM), siftEvaluationRequired = true)
    val SciEng = Scheme("ScienceAndEngineering", "SEFS", "Science And Engineering", Some(SiftRequirement.FORM), siftEvaluationRequired = true)
    val Edip = Scheme("Edip", "EDIP", "Early Diversity Internship Programme", Some(SiftRequirement.FORM), siftEvaluationRequired = true)
    val Sdip = Scheme("Sdip", "SDIP", "Summer Diversity Internship Programme", Some(SiftRequirement.FORM), siftEvaluationRequired = false)

    val AllSchemes = Commercial :: DaT :: Dip :: Finance :: Generalist :: GovComms :: GovEconomics :: GovOps ::
      GovSocialResearch :: GovStats :: HoP :: HR :: ProjectDelivery :: SciEng :: Edip :: Sdip :: Nil
  }

}
