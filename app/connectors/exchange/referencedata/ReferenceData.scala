package connectors.exchange.referencedata

import play.api.libs.functional.syntax._
import play.api.libs.json.{ Format, __ }

case class ReferenceData[T](options: List[T], default: T, aggregate: T) {
  val allValues: Set[T] = (aggregate :: options).toSet
}

object ReferenceData {

  // default formatter does not work for generic types, has to define fields manually...
  implicit def referenceDataFormat[T: Format]: Format[ReferenceData[T]] =
    ((__ \ "options").format[List[T]] ~
      (__ \ "default").format[T] ~
      (__ \ "aggregate").format[T]
      )(ReferenceData.apply, unlift(ReferenceData.unapply))
}
