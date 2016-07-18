package support

import java.util.UUID

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.impl.authenticators.SessionAuthenticator
import models.{CachedData, SecurityUser}
import org.joda.time.DateTime
import play.api.mvc.{Action, AnyContent, Result}
import security.Roles.CsrAuthorization
import security.SecureActions

import scala.concurrent.Future

// scalastyle:off method.name
trait TestableSecureActions extends SecureActions {
  import models.SecurityUserExamples._

  val currentCandidate: CachedData = ActiveCandidate

  override def CSRSecureAction(role: CsrAuthorization)(block: SecuredRequest[_] => CachedData => Future[Result]): Action[AnyContent] = {
    Action.async { request =>
      val secReq = SecuredRequest(
        SecurityUser(UUID.randomUUID.toString),
        SessionAuthenticator(
          LoginInfo("fakeProvider", "fakeKey"),
          DateTime.now(),
          DateTime.now().plusDays(1),
          None, None
        ), request
      )
      implicit val carrier = hc(request)
      block(secReq)(currentCandidate)
    }
  }

}
