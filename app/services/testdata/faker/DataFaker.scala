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

package services.testdata.faker

import model.EvaluationResults
import model.EvaluationResults.Result
import model.Exceptions.DataFakingException
import org.joda.time.LocalDate
import repositories._
import services.testdata.faker.DataFaker.ExchangeObjects.AvailableAssessmentSlot

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object DataFaker {
  object ExchangeObjects {
    case class AvailableAssessmentSlot(venue: AssessmentCentreVenue, date: LocalDate, session: String)
  }

  object Random {
    def randOne[T](options: List[T], cannotBe: List[T] = Nil) = {
      val filtered = options.filterNot(cannotBe.contains)
      if (filtered.isEmpty) {
        throw new DataFakingException(s"There were no items left after filtering.")
      } else {
        util.Random.shuffle(filtered).head
      }
    }

    def bool: Boolean = randOne(List(true, false))

    def passmark: Result = randOne(List(EvaluationResults.Green, EvaluationResults.Amber, EvaluationResults.Red))

    def availableAssessmentVenueAndDate: Future[AvailableAssessmentSlot] = {
      AssessmentCentreYamlRepository.assessmentCentreCapacities.flatMap { assessmentCentreLocations =>

        val randomisedVenues = util.Random.shuffle(assessmentCentreLocations.flatMap(_.venues))

        val firstVenueWithSpace = randomisedVenues.foldLeft(Future.successful(Option.empty[AvailableAssessmentSlot])) {
          case (acc, venue) =>
            acc.flatMap {
              case Some(accVenueAndDate) => Future.successful(Some(accVenueAndDate))
              case _ => venueHasFreeSlots(venue)
            }
        }
        firstVenueWithSpace.map(_.get)
      }
    }

    private def venueHasFreeSlots(venue: AssessmentCentreVenue): Future[Option[AvailableAssessmentSlot]] = {
      applicationAssessmentRepository.applicationAssessmentsForVenue(venue.venueName).map { assessments =>
        val takenSlotsByDateAndSession = assessments.groupBy(slot => slot.date -> slot.session).map {
          case (date, numOfAssessments) => (date, numOfAssessments.length)
        }
        val assessmentsByDate = venue.capacityDates.map(_.date).toSet
        val availableDate = assessmentsByDate.toList.sortWith(_ isBefore _).flatMap { capacityDate =>
          List("AM", "PM").flatMap { possibleSession =>
            takenSlotsByDateAndSession.get(capacityDate -> possibleSession) match {
              // Date with no free slots
              case Some(slots) if slots >= 6 => None
              // Date with no assessments booked or Date with open slots (all dates have 6 slots per session)
              case _ => Some(AvailableAssessmentSlot(venue, capacityDate, possibleSession))
            }
          }
        }
        availableDate.headOption
      }
    }

    def region: Future[String] = {
      AssessmentCentreYamlRepository.locationsAndAssessmentCentreMapping.map { locationsToAssessmentCentres =>
        val locationToRegion = locationsToAssessmentCentres.values.filterNot(_.startsWith("TestAssessment"))
        randOne(locationToRegion.toList)
      }
    }

    def location(region: String, cannotBe: List[String] = Nil): Future[String] = {
      AssessmentCentreYamlRepository.locationsAndAssessmentCentreMapping.map { locationsToAssessmentCentres =>
        val locationsInRegion = locationsToAssessmentCentres.filter(_._2 == region).keys.toList
        randOne(locationsInRegion, cannotBe)
      }
    }

    def gender = randOne(List("Male", "Female"))
    def sexualOrientation = randOne(List("Heterosexual/straight", "Gay woman/lesbian", "Gay man", "Bisexual", "Other"))
    def ethnicGroup = randOne(List(
      "English/Welsh/Scottish/Northern Irish/British",
      "Irish",
      "Gypsy or Irish Traveller",
      "Other White background",
      "White and Black Caribbean",
      "White and Black African",
      "White and Asian",
      "Other mixed/multiple ethnic background",
      "Indian",
      "Pakistani",
      "Bangladeshi",
      "Chinese",
      "Other Asian background",
      "African",
      "Caribbean",
      "Other Black/African/Caribbean background",
      "Arab",
      "Other ethnic group"
    ))
    def age11to16School = randOne(List("Blue Bees School", "Green Goblins School", "Magenta Monkeys School", "Zany Zebras School"))
    def age16to18School = randOne(List("Advanced Skills School", "Extremely Advanced School", "A-Level Specialist School", "16 to 18 School"))
    def homePostcode = randOne(List("AB1 2CD", "BC11 4DE", "CD6 2EF", "DE2F 1GH"))
    def yesNo = randOne(List("Yes", "No"))
    def employeeOrSelf = randOne(List(Some("Employee"), None))
    def sizeOfPlaceOfWork = randOne(List("Small (1 - 24 employees)", "Large (over 24 employees)"))
    def parentsOccupation = randOne(List(
      "Unemployed but seeking work",
      "Unemployed",
      "Modern professional",
      "Clerical and intermediate",
      "Senior managers and administrators",
      "Technical and craft",
      "Semi-routine manual and service",
      "Routine manual and service",
      "Middle or junior managers",
      "Traditional professional"
    ))

    def getFirstname(userNumber: Int) = {
      val firstName = randOne(Firstnames.list)
      s"$firstName$userNumber"
    }

    def getLastname(userNumber: Int) = {
      val lastName = randOne(Lastnames.list)
      s"$lastName$userNumber"
    }
  }
}
