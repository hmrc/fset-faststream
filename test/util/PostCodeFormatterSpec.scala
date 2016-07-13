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

package util;

import mappings.PostCodeMapping
import mappings.PostCodeMapping._
import org.scalatestplus.play.PlaySpec
import play.api.data.validation.Valid

class PostCodeFormatterSpec extends PlaySpec {

  "Postcode Formatter" should {

    def postcodeFormatValidation(from: String, against: String) = {
      validPostcode(from) mustBe Valid
      val formatted = formatPostcode(from)
      formatted mustBe against
      validPostcode(formatted) mustBe Valid
    }

    "Format A99AA type of postcode" in {
      postcodeFormatValidation("d33fi", "D3 3FI")
    }

    "Format A099AA type of postcode" in {
      postcodeFormatValidation("b032az", "B3 2AZ")
    }

    "Format A999AA type of postcode" in {
      postcodeFormatValidation("j321ik", "J32 1IK")
    }

    "Format A9A9AA type of postcode" in {
      postcodeFormatValidation("z5i2uj", "Z5I 2UJ")
    }

    "Format AA99AA type of postcode" in {
      postcodeFormatValidation("ab32qu", "AB3 2QU")
    }

    "Format AA099AA type of postcode" in {
      postcodeFormatValidation("jh072ui", "JH7 2UI")

    }
    "Format AA999AA type of postcode" in {
      postcodeFormatValidation("rm285qk", "RM28 5QK")
    }
    "Format AA9A9AA type of postcode" in {
      postcodeFormatValidation("eh3w4uc", "EH3W 4UC")
    }
  }
}
