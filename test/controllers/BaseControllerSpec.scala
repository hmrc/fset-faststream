package controllers

import java.util.UUID

import models.{CachedUser, UniqueIdentifier}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.mvc.{Request, RequestHeader, Result}
import play.api.test.FakeRequest
import play.filters.csrf.CSRF
import security.{SecurityEnvironment, SignInService}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

/**
  * Each Controller test needs to extend this class to simplify controller testing
  */
abstract class BaseControllerSpec extends PlaySpec with MockitoSugar with ScalaFutures with OneServerPerSuite {
  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val rh: RequestHeader = FakeRequest()

  def randomUUID = UniqueIdentifier(UUID.randomUUID().toString)

  def fakeRequest = FakeRequest().withSession(CSRF.TokenName -> CSRF.SignedTokenProvider.generateToken)

  trait TestableSignInService extends SignInService {
    self: BaseController =>

    val signInService: SignInService

    override def signInUser(user: CachedUser,
                            env: SecurityEnvironment,
                            redirect: Result = Redirect(routes.HomeController.present())
                           )(implicit request: Request[_]): Future[Result] =
      signInService.signInUser(user, env, redirect)(request)
  }
}
