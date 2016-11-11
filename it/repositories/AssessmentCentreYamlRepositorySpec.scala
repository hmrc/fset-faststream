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

package repositories

import fixture.UnitWithAppSpec
import model.Exceptions.{ NoSuchVenueDateException, NoSuchVenueException }
import org.joda.time.LocalDate
import play.Logger

class AssessmentCentreYamlRepositorySpec extends UnitWithAppSpec {
  val DateFormat = "d/M/yy"

  "Locations and assessment centre mapping" should {
    "return non empty mapping" in {
      val mapping = AssessmentCentreYamlRepository.locationsAndAssessmentCentreMapping.futureValue
      mapping must not be empty
      mapping("London") must be("London")
      mapping("Cardiff") must be("Bristol")
    }

    "be consistent with regions-locations-frameworks" in {
      val allLocationsFromFrameworkRepo = allLocationsFromFrameworkRepository
      val locationToAssessmentCentre = AssessmentCentreYamlRepository.locationsAndAssessmentCentreMapping.futureValue.keys.toSet

      val missingLocationsInFrameworkRepo = locationToAssessmentCentre.diff(allLocationsFromFrameworkRepo)
      val missingLocationsInAssessmentCentresMapping = allLocationsFromFrameworkRepo.diff(locationToAssessmentCentre)

      if (missingLocationsInFrameworkRepo.nonEmpty) {
        Logger.error("Missing: " + missingLocationsInFrameworkRepo.mkString(","))
      }
      if (missingLocationsInFrameworkRepo.nonEmpty) {
        Logger.error("Missing: " + missingLocationsInAssessmentCentresMapping.mkString(","))
      }

      withClue("missingLocationsInAssessmentCentresMapping") {
        missingLocationsInAssessmentCentresMapping must be(empty)
      }
      withClue("missingLocationsInFrameworkRepo") {
        missingLocationsInFrameworkRepo must be(empty)
      }
    }
  }

  "Assessment centre capacities" should {
    "return non empty mapping" in {
      val capacities = AssessmentCentreYamlRepository.assessmentCentreCapacities.futureValue
      capacities must not be empty
      val assessmentCapacity = capacities.head
      assessmentCapacity.locationName must be("London")
      val venue = assessmentCapacity.venues.head
      venue.venueName must be("London (Berkeley House)")
      venue.venueDescription must be ("Berkeley House")
      val capacityDate = venue.capacityDates.head
      capacityDate.amCapacity must be(6)
      capacityDate.pmCapacity must be(6)
      capacityDate.date.toString(DateFormat) must be("24/5/17")
    }

    "reject invalid configuration" in {
      val capacities = AssessmentCentreYamlRepository.assessmentCentreCapacities.futureValue
      for {
        c <- capacities
        v <- c.venues
        d <- v.capacityDates
      } {
        d.amCapacity must be >= 0
        d.pmCapacity must be >= 0
      }
    }
  }

  "Assessment centre capacity by date" should {
    "Throw NoSuchVenueException when a bad venue name is passed" in {
        val exception = AssessmentCentreYamlRepository.assessmentCentreCapacityDate("Bleurgh", LocalDate.parse("2015-04-01")).failed.futureValue
        exception mustBe a[NoSuchVenueException]
    }

    "Throw NoSuchVenueDateException when there are no sessions on the specified date" in {
        val exception = AssessmentCentreYamlRepository.assessmentCentreCapacityDate("London (Berkeley House)",
          LocalDate.parse("2010-04-01")).failed.futureValue
        exception mustBe a[NoSuchVenueDateException]
    }

    "Return date capacity information for a venue on a date with valid inputs" is (pending)

  }

  val productionYAMLConfig  = Map(
    "Test.scheduling.online-testing.assessment-centres.yamlFilePath" -> "assessment-centres-prod.yaml"
  )

  "Assessment centre production YAML file" should {

    "remain parsable and load" in {
      val repo = new AssessmentCentreRepositoryImpl {
        val assessmentCentresLocationsPath = "assessment-centres-preferred-locations-prod.yaml"
        val assessmentCentresConfigPath = "assessment-centres-prod.yaml"
      }

      val capacities = repo.assessmentCentreCapacities.futureValue
      capacities must not be empty
      val assessmentCapacity = capacities.head
      assessmentCapacity.locationName must be("London")
      val venue = assessmentCapacity.venues(1)
      venue.venueName must be("London (FSAC) 1")
      venue.venueDescription must be ("FSAC")
      val capacityDate = venue.capacityDates.find(_.date == new LocalDate("2016-07-04")).get
      capacityDate.amCapacity must be(6)
      capacityDate.pmCapacity must be(6)
    }
  }

  def allLocationsFromFrameworkRepository = {
    val frameworkRepository = new FrameworkYamlRepository
    (for {
      r <- frameworkRepository.getFrameworksByRegion.futureValue
      l <- r.locations
    } yield l.name).toSet
  }

}
