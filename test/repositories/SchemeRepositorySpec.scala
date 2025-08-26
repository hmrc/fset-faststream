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

package repositories

import config.{MicroserviceAppConfig, SchemeConfig}
import model.Exceptions.SchemeNotFoundException
import model.{FsbType, SchemeId, Schemes}
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
      repo.getSchemeForFsb("CFS - Skype interview").id mustBe Commercial
      repo.getSchemeForFsb("FCO").id mustBe DiplomaticAndDevelopment
      repo.getSchemeForFsb("HOP FSB").id mustBe HousesOfParliament
    }

    "return the faststream schemes" in new TestFixture {
      repo.faststreamSchemes.size mustBe expectedNumberOfSchemes - 2 // All excluding Edip / Sdip
    }

    "return the schemes for the given scheme ids" in new TestFixture {
      repo.getSchemesForIds(Seq(Commercial, OperationalDelivery)).size mustBe 2
    }

    "return the scheme for the given scheme id" in new TestFixture {
      val schemeOpt = repo.getSchemeForId(Commercial)
      schemeOpt mustBe defined
      schemeOpt.map(_.code) mustBe Some("CFS")
    }

    "return fsb schemes" in new TestFixture {
      val expectedFsbSchemes = Seq(
        Commercial, CyberSecurity, DiplomaticAndDevelopment,
        DiplomaticAndDevelopmentEconomics, Finance, GovernmentCommunicationService,
        GovernmentEconomicsService, GovernmentOperationalResearchService,
        GovernmentSocialResearchService, GovernmentStatisticalService,
        HumanResources, HousesOfParliament, OperationalDelivery, ProjectDelivery,
        Property, RiskManagement, ScienceAndEngineering,
        Edip, Sdip
      )
      val fsbSchemes = repo.fsbSchemeIds
      fsbSchemes must contain theSameElementsAs expectedFsbSchemes
    }

    "return siftable schemes" in new TestFixture {
      val expectedSiftableSchemes = Seq(
        DiplomaticAndDevelopment,
        DiplomaticAndDevelopmentEconomics, GovernmentCommunicationService,
        GovernmentEconomicsService, GovernmentOperationalResearchService,
        GovernmentSocialResearchService, GovernmentStatisticalService,
        HousesOfParliament, ProjectDelivery,
        RiskManagement, ScienceAndEngineering,
        Edip
      )
      val siftableSchemes = repo.siftableSchemeIds
      siftableSchemes must contain theSameElementsAs expectedSiftableSchemes
    }

    "return siftable and evaluation required schemes" in new TestFixture {
      val expectedSiftableAndEvaluationRequiredSchemes = Seq(
        DiplomaticAndDevelopmentEconomics, GovernmentEconomicsService,
        GovernmentOperationalResearchService, GovernmentSocialResearchService,
        GovernmentStatisticalService, ScienceAndEngineering
      )
      val siftableSchemes = repo.siftableAndEvaluationRequiredSchemeIds
      siftableSchemes must contain theSameElementsAs expectedSiftableAndEvaluationRequiredSchemes
    }

    "return no sift evaluation required scheme ids" in new TestFixture {
      val expected = Seq(
        Commercial, CyberSecurity, Digital,
        DiplomaticAndDevelopment, Finance,
        GovernmentCommunicationService, GovernmentPolicy,
        HousesOfParliament, HumanResources, OperationalDelivery,
        ProjectDelivery, Property, RiskManagement,
        Edip, Sdip
      )
      val actual = repo.noSiftEvaluationRequiredSchemeIds
      actual must contain theSameElementsAs expected
    }

    "return non siftable schemes" in new TestFixture {
      repo.nonSiftableSchemeIds must contain theSameElementsAs
        Seq(
          Commercial, CyberSecurity, Digital,
          Finance, GovernmentPolicy, HumanResources,
          OperationalDelivery, Property,
          Sdip
        )
    }

    "return numeric test sift schemes" in new TestFixture {
      val numericTestSchemes = repo.numericTestSiftRequirementSchemeIds
      numericTestSchemes must contain theSameElementsAs Nil
    }

    "return form must be filled in sift schemes" in new TestFixture {
      val expectedFormMustBeFilledInSiftableSchemes = Seq(
        DiplomaticAndDevelopment, DiplomaticAndDevelopmentEconomics,
        GovernmentCommunicationService, GovernmentEconomicsService,
        GovernmentOperationalResearchService, GovernmentSocialResearchService,
        GovernmentStatisticalService,
        HousesOfParliament, ProjectDelivery,
        RiskManagement, ScienceAndEngineering,
        Edip
      )
      val formMustBeFilledInSiftSchemes = repo.formMustBeFilledInSchemeIds
      formMustBeFilledInSiftSchemes must contain theSameElementsAs expectedFormMustBeFilledInSiftableSchemes
    }

    "return fsb types" in new TestFixture {
      val expected = Seq(
        FsbType("CFS - Skype interview"), FsbType("CYB - Skype interview"),
        FsbType("FCO"), FsbType("FIN FSB"), FsbType("GES_DS"),
        FsbType("GCFS FSB"), FsbType("EAC"), FsbType("ORAC"),
        FsbType("RMT - Skype interview"),
        FsbType("SRAC"), FsbType("SAC"), FsbType("HOP FSB"), FsbType("HR FSB"),
        FsbType("OPD - Skype interview"),
        FsbType("PDFS - Skype interview"), FsbType("PRO - Skype interview"),
        FsbType("SEFS FSB"), FsbType("EDIP - Telephone interview"), FsbType("SDIP - Telephone interview")
      )
      val actual = repo.getFsbTypes
      actual must contain theSameElementsAs expected
    }

    "identify valid schemeIds" in new TestFixture {
      repo.isValidSchemeId(Commercial) mustBe true
      repo.isValidSchemeId(SchemeId("IDontExist")) mustBe false
    }
  }

  trait TestFixture extends Schemes {
    val appConfigMock = mock[MicroserviceAppConfig]

    val schemeConfig = SchemeConfig(yamlFilePath = "schemes.yaml", candidateFrontendUrl = "aHR0cDovL2xvY2FsaG9zdDo5Mjg0")
    when(appConfigMock.schemeConfig).thenReturn(schemeConfig)

    val repo = new SchemeYamlRepository()(app, appConfigMock)
    val expectedNumberOfSchemes = 21
  }
}
