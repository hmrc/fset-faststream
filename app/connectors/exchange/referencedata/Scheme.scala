package connectors.exchange.referencedata

import play.api.libs.json._

case class SchemeId(value: String)

object SchemeId {
  // Custom json formatter to serialise to a string
  val schemeIdWritesFormat: Writes[SchemeId] = Writes[SchemeId](scheme => JsString(scheme.value))
  val schemeIdReadsFormat: Reads[SchemeId] = Reads[SchemeId](scheme => JsSuccess(SchemeId(scheme.as[String])))

  implicit val schemeIdFormat = Format(schemeIdReadsFormat, schemeIdWritesFormat)
}

/**
  * Wrapper for scheme data
  *
  * @param id The scheme ID to be delivered across the wire/stored in DB etc.
  * @param code The abbreviated form
  * @param name The form displayed to end users
  */
case class Scheme(
  id: SchemeId,
  code: String,
  name: String,
  requiresSift: Boolean,
  requiresForm: Boolean,
  requiresNumericTest: Boolean
)

object Scheme {
  implicit val schemeFormat = Json.format[Scheme]

  def apply(id: String, code: String, name: String, requiresSift: Boolean, requiresForm: Boolean,
    requiresNumericTest: Boolean
  ): Scheme = Scheme(SchemeId(id), code, name, requiresSift, requiresForm, requiresNumericTest)
}
