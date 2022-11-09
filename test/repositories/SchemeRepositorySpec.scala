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

package repositories

import config.{MicroserviceAppConfig, SchemeConfig}
import model.Exceptions.SchemeNotFoundException
import model.{FsbType, SchemeId}
import org.mockito.Mockito.when
import testkit.UnitWithAppSpec

class SchemeRepositorySpec extends UnitWithAppSpec {
  "SchemeRepository" must {
    "return the schemes" in new TestFixture {
      repo.schemes.size mustBe expectedNumberOfSchemes
    }

    "throw an exception if scheme for fsb does not exist" in new TestFixture {
      intercept[SchemeNotFoundException] {
        repo.getSchemeForFsb("IDontExist")
      }
    }

    "return the scheme for the given fsb" in new TestFixture {
      repo.getSchemeForFsb("CFS - Skype interview").id mustBe SchemeId("Commercial")
      repo.getSchemeForFsb("FCO").id mustBe SchemeId("DiplomaticAndDevelopment")
      repo.getSchemeForFsb("HOP FSB").id mustBe SchemeId("HousesOfParliament")
    }

    "return the faststream schemes" in new TestFixture {
      repo.faststreamSchemes.size mustBe expectedNumberOfSchemes - 2 // All excluding Edip / Sdip
    }

    "return the schemes for the given scheme ids" in new TestFixture {
      repo.getSchemesForIds(Seq(SchemeId("Commercial"), SchemeId("Generalist"))).size mustBe 2
    }

    "return the scheme for the given scheme id" in new TestFixture {
      val schemeOpt = repo.getSchemeForId(SchemeId("Commercial"))
      schemeOpt mustBe defined
      schemeOpt.map(_.code) mustBe Some("CFS")
    }

    "return fsb schemes" in new TestFixture {
      val expectedFsbSchemes = Seq(
        SchemeId("Commercial"), SchemeId("DiplomaticAndDevelopment"),
        SchemeId("DiplomaticAndDevelopmentEconomics"), SchemeId("GovernmentCommunicationService"),
        SchemeId("GovernmentEconomicsService"), SchemeId("GovernmentOperationalResearchService"),
        SchemeId("GovernmentSocialResearchService"), SchemeId("GovernmentStatisticalService"),
        SchemeId("HousesOfParliament"), SchemeId("ProjectDelivery"),
        SchemeId("Property"), SchemeId("ScienceAndEngineering"),
        SchemeId("Edip"), SchemeId("Sdip")
      )
      val fsbSchemes = repo.fsbSchemeIds
      fsbSchemes must contain theSameElementsAs(expectedFsbSchemes)
    }

    "return siftable schemes" in new TestFixture {
      val expectedSiftableSchemes = Seq(
        SchemeId("Commercial"), SchemeId("DiplomaticAndDevelopment"),
        SchemeId("DiplomaticAndDevelopmentEconomics"), SchemeId("Finance"), SchemeId("GovernmentCommunicationService"),
        SchemeId("GovernmentEconomicsService"), SchemeId("GovernmentOperationalResearchService"),
        SchemeId("GovernmentSocialResearchService"), SchemeId("GovernmentStatisticalService"),
        SchemeId("HousesOfParliament"), SchemeId("ProjectDelivery"),
        SchemeId("Property"), SchemeId("ScienceAndEngineering"),
        SchemeId("Edip"), SchemeId("Sdip")
      )
      val siftableSchemes = repo.siftableSchemeIds
      siftableSchemes must contain theSameElementsAs(expectedSiftableSchemes)
    }

    "return siftable and evaluation required schemes" in new TestFixture {
      val expectedSiftableAndEvaluationRequiredSchemes = Seq(
        SchemeId("Commercial"), SchemeId("DiplomaticAndDevelopmentEconomics"),
        SchemeId("Finance"), SchemeId("GovernmentEconomicsService"),
        SchemeId("GovernmentOperationalResearchService"), SchemeId("GovernmentSocialResearchService"),
        SchemeId("GovernmentStatisticalService"), SchemeId("ScienceAndEngineering")
      )
      val siftableSchemes = repo.siftableAndEvaluationRequiredSchemeIds
      siftableSchemes must contain theSameElementsAs(expectedSiftableAndEvaluationRequiredSchemes)
    }

    "return no sift evaluation required scheme ids" in new TestFixture {
      val expected = Seq(
        SchemeId("DigitalDataTechnologyAndCyber"), SchemeId("DiplomaticAndDevelopment"),
        SchemeId("Generalist"), SchemeId("GeneralistSTEM"), SchemeId("GovernmentCommunicationService"),
        SchemeId("HousesOfParliament"), SchemeId("HumanResources"),
        SchemeId("ProjectDelivery"), SchemeId("Property"),
        SchemeId("Edip"), SchemeId("Sdip")
      )
      val actual = repo.noSiftEvaluationRequiredSchemeIds
      actual must contain theSameElementsAs(expected)
    }

    "return non siftable schemes" in new TestFixture {
      repo.nonSiftableSchemeIds must contain theSameElementsAs
        Seq(SchemeId("DigitalDataTechnologyAndCyber"), SchemeId("Generalist"),
          SchemeId("GeneralistSTEM"), SchemeId("HumanResources"))
    }

    "return numeric test sift schemes" in new TestFixture {
      val numericTestSchemes = repo.numericTestSiftRequirementSchemeIds
      numericTestSchemes must contain theSameElementsAs(Seq(SchemeId("Commercial"), SchemeId("Finance")))
    }

    "return form must be filled in sift schemes" in new TestFixture {
      val expectedFormMustBeFilledInSiftableSchemes = Seq(
        SchemeId("DiplomaticAndDevelopment"), SchemeId("DiplomaticAndDevelopmentEconomics"),
        SchemeId("GovernmentCommunicationService"), SchemeId("GovernmentEconomicsService"),
        SchemeId("GovernmentOperationalResearchService"), SchemeId("GovernmentSocialResearchService"),
        SchemeId("GovernmentStatisticalService"),
        SchemeId("HousesOfParliament"), SchemeId("ProjectDelivery"),
        SchemeId("Property"), SchemeId("ScienceAndEngineering"),
        SchemeId("Edip"), SchemeId("Sdip")
      )
      val formMustBeFilledInSiftSchemes = repo.formMustBeFilledInSchemeIds
      formMustBeFilledInSiftSchemes must contain theSameElementsAs(expectedFormMustBeFilledInSiftableSchemes)
    }

    "return fsb types" in new TestFixture {
      val expected = Seq(
        FsbType("CFS - Skype interview"), FsbType("FCO"), FsbType("EAC_DS"),
        FsbType("GCFS FSB"), FsbType("EAC"), FsbType("ORAC"),
        FsbType("SRAC"), FsbType("SAC"), FsbType("HOP FSB"),
        FsbType("PDFS - Skype interview"), FsbType("PRO - Skype interview"),
        FsbType("SEFS FSB"), FsbType("EDIP - Telephone interview"), FsbType("SDIP - Telephone interview")
      )
      val actual = repo.getFsbTypes
      actual must contain theSameElementsAs(expected)
    }

    "identify valid schemeIds" in new TestFixture {
      repo.isValidSchemeId(SchemeId("Commercial")) mustBe true
      repo.isValidSchemeId(SchemeId("IDontExist")) mustBe false
    }
  }

  trait TestFixture {
    val appConfigMock = mock[MicroserviceAppConfig]

    val schemeConfig = SchemeConfig(yamlFilePath = "schemes.yaml")
    when(appConfigMock.schemeConfig).thenReturn(schemeConfig)

    val repo = new SchemeYamlRepository()(app, appConfigMock)
    val expectedNumberOfSchemes = 19
  }
}
