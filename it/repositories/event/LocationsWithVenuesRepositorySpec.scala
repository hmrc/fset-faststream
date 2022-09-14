package repositories.event

import config.{LocationsAndVenuesConfig, MicroserviceAppConfig}
import model.persisted.ReferenceData
import model.persisted.eventschedules.{Location, Venue}
import org.mockito.Mockito.when
import repositories.events.{LocationsWithVenuesInMemoryYamlRepository, UnknownLocationException, UnknownVenueException}
import testkit.UnitWithAppSpec

class LocationsWithVenuesRepositorySpec extends UnitWithAppSpec {

  "LocationsWithVenuesRepository" should {
    "fetch the expected locations with venues" in new TestFixture {
      val result = repository.locationsWithVenuesList.futureValue
      result.size mustBe 4
      val locationNames = result.map ( _.name )
      locationNames must contain theSameElementsAs List("London", "Newcastle", "Virtual", "Home")
    }

    "fetch the expected locations" in new TestFixture {
      repository.location("Virtual").futureValue mustBe Location("Virtual")
      repository.location("Home").futureValue mustBe Location("Home")
      repository.location("London").futureValue mustBe Location("London")
      repository.location("Newcastle").futureValue mustBe Location("Newcastle")
    }

    "throw an exception if the location doesn't exist" in new TestFixture {
      val result = repository.location("I don't exist").failed.futureValue
      result mustBe a[UnknownLocationException]
    }

    "fetch the locations as reference data" in new TestFixture {
      repository.locations.futureValue mustBe
        ReferenceData(
          options = List(Location("London"), Location("Newcastle"), Location("Virtual"), Location("Home")),
          default = Location("London"), aggregate = Location("All")
        )
    }

    "fetch the expected venues" in new TestFixture {
      repository.venue("LONDON_FSAC").futureValue mustBe Venue("LONDON_FSAC", "London (100 Parliament Street)")
      repository.venue("LONDON_FSB").futureValue mustBe Venue("LONDON_FSB", "London (FCO King Charles Street)")
      repository.venue("VIRTUAL").futureValue mustBe Venue("VIRTUAL", "Virtual")
      repository.venue("NEWCASTLE_FSAC").futureValue mustBe Venue("NEWCASTLE_FSAC", "Newcastle (Tyne View Park)")
    }

    "throw an exception if the venue doesn't exist" in new TestFixture {
      val result = repository.venue("I don't exist").failed.futureValue
      result mustBe a[UnknownVenueException]
    }

    "fetch the venues as reference data" in new TestFixture {
      repository.venues.futureValue mustBe
        ReferenceData(
          options = List(
            Venue("LONDON_FSAC", "London (100 Parliament Street)"),
            Venue("LONDON_FSB", "London (FCO King Charles Street)"),
            Venue("NEWCASTLE_FSAC" ,"Newcastle (Tyne View Park)"),
            Venue("VIRTUAL", "Virtual")),
          default = Venue("LONDON_FSAC", "London (100 Parliament Street)"),
          aggregate = Venue("ALL_VENUES", "All venues")
        )
    }
  }

  trait TestFixture {
    val mockAppConfig = mock[MicroserviceAppConfig]
    val mockLocationsAndVenuesConfig =
      LocationsAndVenuesConfig(
        yamlFilePath = "locations-and-venues.yaml"
      )
    when(mockAppConfig.locationsAndVenuesConfig).thenReturn(mockLocationsAndVenuesConfig)

    when(mockAppConfig.AllLocations).thenReturn(Location("All"))
    when(mockAppConfig.AllVenues).thenReturn(Venue("ALL_VENUES", "All venues"))

    val repository = new LocationsWithVenuesInMemoryYamlRepository(app, mockAppConfig)
  }
}
