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

package services.testdata.faker

import com.google.inject.ImplementedBy
import factories.UUIDFactory

import javax.inject.{Inject, Singleton}
import model.EvaluationResults.Result
import model._
import model.exchange.AssessorAvailability
import model.persisted.eventschedules.{EventType, Session, _}
import org.joda.time.{LocalDate, LocalTime}
import repositories.SchemeRepository
import repositories.events.LocationsWithVenuesInMemoryYamlRepository

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._
import scala.language.postfixOps

@Singleton
class DataFakerImpl @Inject () (schemeRepository: SchemeRepository,
                                locationsWithVenuesRepo: LocationsWithVenuesInMemoryYamlRepository) extends
  DataFaker(schemeRepository, locationsWithVenuesRepo)

//scalastyle:off number.of.methods
@ImplementedBy(classOf[DataFakerImpl])
abstract class DataFaker(schemeRepo: SchemeRepository,
                         locationsWithVenuesRepo: LocationsWithVenuesInMemoryYamlRepository) extends DataFakerRandom with Schemes {

  object ExchangeObjects {
    case class AvailableAssessmentSlot(venue: Venue, date: LocalDate, session: String)
  }

  def mediaReferrer = Random.randOne(List(
    None,
    Some("GOV.UK or Civil Service Jobs"),
    Some("Recruitment website"),
    Some("Social Media (Facebook, Twitter or Instagram)"),
    Some("Fast Stream website (including scheme sites)"),
    Some("News article or online search (Google)"),
    Some("Friend in the Fast Stream"),
    Some("Friend or family in the Civil Service"),
    Some("Friend or family outside of the Civil Service"),
    Some("Careers fair (University or graduate)"),
    Some("University careers service (or jobs flyers)"),
    Some("University event (Guest lecture or skills session)"),
    Some("Other")
  ))

  def hasDisabilityDescription: String = Random.randOne(List("I am too tall", "I am too good", "I get bored easily"))

  def onlineAdjustmentsDescription: String = Random.randOne(List(
    "I am too sensitive to the light from screens",
    "I am allergic to electronic-magnetic waves",
    "I am a convicted cracker who was asked by the court to be away from computers for 5 years"))

  def assessmentCentreAdjustmentDescription: String = Random.randOne(List(
    "I am very weak, I need constant support",
    "I need a comfortable chair because of my back problem",
    "I need to take a rest every 10 minutes"))

  def phoneAdjustmentsDescription: String = Random.randOne(List(
    "I need a very loud speaker",
    "I need good headphones"
  ))

  def passmark: Result = Random.randOne(List(EvaluationResults.Green, EvaluationResults.Amber, EvaluationResults.Red))

  /* TODO Fix these again once the event allocation features have been done

  private def venueHasFreeSlots(venue: Venue): Future[Option[AvailableAssessmentSlot]] = {
    applicationAssessmentRepository.applicationAssessmentsForVenue(venue.name).map { assessments =>
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
    LocationsWithVenuesYamlRepository.locationsAndAssessmentCentreMapping.map { locationsToAssessmentCentres =>
      val locationToRegion = locationsToAssessmentCentres.values.filterNot(_.startsWith("TestAssessment"))
      randOne(locationToRegion.toList)
    }
  }

  def location(region: String, cannotBe: List[String] = Nil): Future[String] = {
    LocationsWithVenuesYamlRepository.locationsWithVenuesList.map { locationsToAssessmentCentres =>
      val locationsInRegion = locationsToAssessmentCentres.filter(_._2 == region).keys.toList
      randOne(locationsInRegion, cannotBe)
    }
  }
  */

  // Purposefully always at least 2 and max of 4. SdipFaststream candidates actually can have 5 -
  // 4 selectable schemes plus Sdip, which is assigned automatically, but we just support 4 here
  private def randNumberOfSchemes = Random.randOne(List(2, 3, 4))

  def schemeTypes = Random.randList(schemeRepo.schemes.toList, randNumberOfSchemes)

  def gender = Random.randOne(List(
    "Male",
    "Female",
    "Other",
    "I don't know/prefer not to say"))

  def sexualOrientation = Random.randOne(List(
    "Heterosexual/straight",
    "Gay/lesbian",
    "Bisexual",
    "Other",
    "I don't know/prefer not to say"))

  def ethnicGroup = Random.randOne(List(
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
    "Other ethnic group",
    "I don't know/prefer not to say"
  ))

  def age14to16School = Random.randOne(List("Blue Bees School", "Green Goblins School", "Magenta Monkeys School", "Zany Zebras School"))

  def schoolType14to16 = Random.randOne(List(
    "stateRunOrFunded-selective",
    "stateRunOrFunded-nonSelective",
    "indyOrFeePaying-bursary",
    "indyOrFeePaying-noBursary"
  ))

  def age16to18School = Random.randOne(
    List("Advanced Skills School", "Extremely Advanced School", "A-Level Specialist School", "14 to 18 School")
  )

  def university = Random.randOne(Universities.list)

  def degreeCategory = Random.randOne(DegreeCategories.list)

  def homePostcode = Random.randOne(List("AB1 2CD", "BC11 4DE", "CD6 2EF", "DE2F 1GH", "I don't know/prefer not to say"))

  def yesNo = Random.randOne(List("Yes", "No"))

  def yesNoPreferNotToSay = Random.randOne(List("Yes", "No", "I don't know/prefer not to say"))

  def employeeOrSelf = Random.randOne(List(
    "Employee",
    "Self-employed with employees",
    "Self-employed/freelancer without employees",
    "I don't know/prefer not to say"))

  def sizeOfPlaceOfWork = Random.randOne(List("Small (1 - 24 employees)", "Large (over 24 employees)"))

  def parentsDegree = Random.randOne(List(
    "Degree level qualification",
    "Qualifications below degree level",
    "No formal qualifications",
    "I don't know/prefer not to say"
  ))

  def parentsOccupation = Random.randOne(List(
    "Unemployed but seeking work",
    "Unemployed",
    "Employed",
    "Unknown"
  ))

  def skills: List[String] = Random.randList(List(
    SkillType.SIFTER.toString,
    SkillType.QUALITY_ASSURANCE_COORDINATOR.toString,
    SkillType.ASSESSOR.toString,
    SkillType.CHAIR.toString), 4)

  def sifterSchemes: List[SchemeId] = Random.randList(
    List(GovernmentEconomicsService, ProjectDelivery, Sdip), 3
  )

  def parentsOccupationDetails: String = Random.randOne(List(
    "Modern professional",
    "Clerical (office work) and intermediate",
    "Senior managers and administrators",
    "Technical and craft",
    "Semi-routine manual and service",
    "Routine manual and service",
    "Middle or junior managers",
    "Traditional professional"
  ))

  def sizeParentsEmployeer: String = Random.randOne(List(
    "Small (1 to 24 employees)",
    "Large (over 24 employees)",
    "I don't know/prefer not to say"
  ))

  def getFirstname(userNumber: Int): String = {
    val firstName = Random.randOne(Firstnames.list)
    s"$firstName$userNumber"
  }

  def getLastname(userNumber: Int): String = {
    val lastName = Random.randOne(Lastnames.list)
    s"$lastName$userNumber"
  }

  def upperLetter: Char = Random.randOne(( 'A' to 'Z' ).toList)

  def postCode: String = {
    s"$upperLetter${upperLetter}1 2$upperLetter$upperLetter"
  }

  def accessCode: String = randomAlphaString(7)

  def getVideoInterviewScore: Double = Random.randOne(List(1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0))

  private def randomAlphaString(n: Int) = {
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    Stream.continually(Random.scalaRandom.nextInt(alphabet.length)).map(alphabet).take(n).mkString
  }

  val videoInterviewFeedback: String = "Collaborating and Partnering/In the interview you:\n" +
    "1. Provided limited specific, but some evidence of engagement.\n" +
    "2. Provided some limited evidence of focus on learning but none on personal development."

  object Assessor {
    def boolTrue20percent: Boolean = Random.randOne(List(1, 2, 3, 4, 5)) == 5

    def bool: Boolean = Random.randOne(List(true, false))

    private def location = Random.randOne(List("London", "Newcastle"))

    def availability: Option[Set[AssessorAvailability]] = {
      if (boolTrue20percent) {
        Some(Set.empty)
      } else {
        val dates = ( 15 to 25 ).map(i => LocalDate.parse(s"2017-06-$i")).toSet
        Option(dates.flatMap { date =>
          if (bool) {
            Some(AssessorAvailability(location, date))
          } else {
            None
          }
        })
      }
    }
  }

  object Event {
    def id: String = UUIDFactory.generateUUID()

    import EventType._
    def eventType: EventType.Value = Random.randOne(EventType.options.keys.toList)

    def description(eventType: EventType): String = eventType match {
      case EventType.FSB => Random.randOne(ExternalSources.allFsbTypes.toList).key
      case _ => ""
    }

    def location(description: String): Location = {
      if (description.contains("interview")) {
        Location("Virtual")
      } else {
        Random.randOne(List(Location("London"), Location("Newcastle")))
      }
    }

    def venue(l: Location)(implicit ec: ExecutionContext): Venue = Random.randOne(ExternalSources.venuesByLocation(l.name))

    def date: LocalDate = LocalDate.now().plusDays(Random.number(Option(300)))

    def capacity: Int = Random.randOne(List(8, 10, 12, 14, 16, 18))

    def minViableAttendees: Int = capacity - Random.randOne(List(5, 3, 4, 2))

    def attendeeSafetyMargin: Int = Random.randOne(List(1, 2, 3))

    def startTime: LocalTime = LocalTime.parse(Random.randOne(List("9:30", "11:00", "13:30", "15:00")))

    def endTime: LocalTime = startTime.plusHours(1)

    def skillRequirements: Map[String, Int] = {
      val skills = SkillType.values.toList.map(_.toString)
      val numberOfSkills = Random.randOne(( 1 to SkillType.values.size ).map(i => i).toList)
      val skillsSelected = Random.randList(skills, numberOfSkills)

      def numberOfPeopleWithSkillsRequired = Random.randOne(List(1, 2, 3, 4, 8))

      skillsSelected.map { skillSelected =>
        skillSelected -> numberOfPeopleWithSkillsRequired
      }.toMap
    }

    def sessions = Random.randList(List(
      Session(UniqueIdentifier.randomUniqueIdentifier.toString(), "First session",
        capacity, minViableAttendees, attendeeSafetyMargin, startTime, startTime.plusHours(1)),
      Session(UniqueIdentifier.randomUniqueIdentifier.toString(), "Advanced session",
        capacity, minViableAttendees, attendeeSafetyMargin, startTime, startTime.plusHours(2)),
      Session(UniqueIdentifier.randomUniqueIdentifier.toString(), "Midday session",
        capacity, minViableAttendees, attendeeSafetyMargin, startTime, startTime.plusHours(3)),
      Session(UniqueIdentifier.randomUniqueIdentifier.toString(), "Small session",
        capacity, minViableAttendees, attendeeSafetyMargin, startTime, startTime.plusHours(4))
    ), 2)
  }

  object Allocation {
    def status: AllocationStatuses.Value = Random.randOne(List(AllocationStatuses.CONFIRMED, AllocationStatuses.UNCONFIRMED))
  }

  object ExternalSources {

    private val schemeRepository = schemeRepo

    def allFsbTypes = schemeRepository.getFsbTypes

    private val locationsAndVenuesRepository = locationsWithVenuesRepo

    def allVenues(implicit ec: ExecutionContext) = Await.result(locationsAndVenuesRepository.venues.map(_.allValues.toList), 1 second)

    def venuesByLocation(location: String)(implicit ec: ExecutionContext): List[Venue] = {
      val venues = locationsAndVenuesRepository.locationsWithVenuesList.map { list =>
        list.filter(lv => lv.name == location).flatMap(_.venues)
      }
      Await.result(venues, 1 second)
    }
  }

  //scalastyle:off line.size.limit
  val loremIpsum = """
                     |Lorem ipsum dolor sit amet, consectetur adipiscing elit. Donec bibendum odio ac dictum tincidunt. Pellentesque at suscipit metus. Phasellus ac ante at dolor eleifend tincidunt efficitur ut purus. Donec pharetra leo sed malesuada luctus. Proin ac egestas turpis. Vivamus aliquam nunc ac dapibus pharetra. Maecenas dignissim maximus ligula, eu rutrum odio malesuada ac. Fusce euismod lobortis pretium. Ut blandit commodo erat, porttitor eleifend nisl consectetur sed. Duis varius nisi sit amet elit convallis interdum.
                     |
                     |Nam et gravida leo. Maecenas non elementum justo. Praesent nec congue purus. Cras luctus vitae velit at cursus. Maecenas aliquet mauris pulvinar, scelerisque diam vel, tincidunt diam. Curabitur vulputate elementum nulla non rutrum. Sed consequat urna eget mi vestibulum, nec placerat urna tempor.
                     |
                     |Praesent nec nibh felis. Vestibulum porttitor, risus vitae ultrices facilisis, tortor dui elementum dui, nec bibendum tellus diam non nulla. Fusce sagittis quam non feugiat pellentesque. Sed ac auctor quam. Sed nec sem accumsan sem facilisis tincidunt convallis tincidunt ante. Ut mi lorem, consectetur quis magna id, congue molestie dui. Pellentesque auctor, mi molestie feugiat commodo, dui nisl ornare nisi, nec tincidunt erat lacus quis lorem. Nulla rutrum rutrum velit, nec sodales tellus molestie aca
                   """.stripMargin
  //scalastyle:on
}
