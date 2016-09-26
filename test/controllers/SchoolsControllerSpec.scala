package controllers

import connectors.SchoolsClient
import connectors.SchoolsClient.SchoolsNotFound
import connectors.exchange.School
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import play.api.test.Helpers._
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future


class SchoolsControllerSpec extends BaseControllerSpec {

  val schoolClient = mock[SchoolsClient]

  def schoolsController = new SchoolsController(schoolClient) with TestableSecureActions

  "get schools" should {
    val schoolList = List(
      School("IRN", "1", "School1"),
      School("IRN", "2", "School2"),
      School("IRN", "3", "School3"),
      School("IRN", "4", "School4"),
      School("IRN", "5", "School5"),
      School("IRN", "6", "School6"),
      School("IRN", "7", "School7"),
      School("IRN", "8", "School8"),
      School("IRN", "9", "School9"),
      School("IRN", "10", "School10"),
      School("IRN", "11", "School11"),
      School("IRN", "12", "School12"),
      School("IRN", "13", "School13"),
      School("IRN", "14", "School14"),
      School("IRN", "15", "School15")
    )

    "load list of schools based on the search criteria" in {
      val searchCriteria = "Abb"
      when(schoolClient.getSchools(eqTo(searchCriteria))(any[HeaderCarrier]))
        .thenReturn(Future.successful(schoolList))
      val result = schoolsController.getSchools(searchCriteria)(fakeRequest)
      val content = contentAsString(result)
      status(result) mustBe OK
      content must include(
        s"""[{"id":"IRN_1","name":"School1","label":"School1"},""" +
          """{"id":"IRN_2","name":"School2","label":"School2"},""" +
          """{"id":"IRN_3","name":"School3","label":"School3"},""" +
          """{"id":"IRN_4","name":"School4","label":"School4"},""" +
          """{"id":"IRN_5","name":"School5","label":"School5"},""" +
          """{"id":"IRN_6","name":"School6","label":"School6"},""" +
          """{"id":"IRN_7","name":"School7","label":"School7"},""" +
          """{"id":"IRN_8","name":"School8","label":"School8"},""" +
          """{"id":"IRN_9","name":"School9","label":"School9"},""" +
          """{"id":"IRN_10","name":"School10","label":"School10"},""" +
          """{"id":"IRN_11","name":"School11","label":"School11"},""" +
          """{"id":"IRN_12","name":"School12","label":"School12"},""" +
          """{"id":"IRN_13","name":"School13","label":"School13"},""" +
          """{"id":"IRN_14","name":"School14","label":"School14"},""" +
          """{"id":"IRN_15","name":"School15","label":"School15"}]""")
    }

    "load max 15 schools based on the search criteria" in {
      val searchCriteria = "Abb"
      when(schoolClient.getSchools(eqTo(searchCriteria))(any[HeaderCarrier]))
        .thenReturn(Future.successful(schoolList :+ School("IRN", "16", "School16")))
      val result = schoolsController.getSchools(searchCriteria)(fakeRequest)
      val content = contentAsString(result)
      status(result) mustBe OK
      content must include(
        s"""[{"id":"","name":"","label":"Please narrow down your search"},""" +
          """{"id":"IRN_1","name":"School1","label":"School1"},""" +
          """{"id":"IRN_2","name":"School2","label":"School2"},""" +
          """{"id":"IRN_3","name":"School3","label":"School3"},""" +
          """{"id":"IRN_4","name":"School4","label":"School4"},""" +
          """{"id":"IRN_5","name":"School5","label":"School5"},""" +
          """{"id":"IRN_6","name":"School6","label":"School6"},""" +
          """{"id":"IRN_7","name":"School7","label":"School7"},""" +
          """{"id":"IRN_8","name":"School8","label":"School8"},""" +
          """{"id":"IRN_9","name":"School9","label":"School9"},""" +
          """{"id":"IRN_10","name":"School10","label":"School10"},""" +
          """{"id":"IRN_11","name":"School11","label":"School11"},""" +
          """{"id":"IRN_12","name":"School12","label":"School12"},""" +
          """{"id":"IRN_13","name":"School13","label":"School13"},""" +
          """{"id":"IRN_14","name":"School14","label":"School14"},""" +
          """{"id":"IRN_15","name":"School15","label":"School15"}]""")
    }

    "not return any schools" in {
      val searchCriteria = "Abb"
      when(schoolClient.getSchools(eqTo(searchCriteria))(any[HeaderCarrier]))
        .thenReturn(Future.failed(new SchoolsNotFound))
      val result = schoolsController.getSchools(searchCriteria)(fakeRequest)
      status(result) mustBe BAD_REQUEST
    }
  }
}
