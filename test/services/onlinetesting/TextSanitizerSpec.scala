/*
 * Copyright 2021 HM Revenue & Customs
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

package services.onlinetesting

import testkit.UnitSpec

class TextSanitizerSpec extends UnitSpec {
  "Text sanitizer" should {
    "remove new lines" in new TestFixture {
      val input = "hello\nworld"
      val expected = "hello world"
      val actual = sanitizer.sanitizeFreeText(input)
      actual mustBe expected
    }

    "remove tabs" in new TestFixture {
      val input = "hello\tworld"
      val expected = "hello world"
      val actual = sanitizer.sanitizeFreeText(input)
      actual mustBe expected
    }

    "remove left chevrons" in new TestFixture {
      val input = "hello<world"
      val expected = "hello world"
      val actual = sanitizer.sanitizeFreeText(input)
      actual mustBe expected
    }

    "remove right chevrons" in new TestFixture {
      val input = "hello>world"
      val expected = "hello world"
      val actual = sanitizer.sanitizeFreeText(input)
      actual mustBe expected
    }

    "remove multiple spaces" in new TestFixture {
      val input = "hello   world"
      val expected = "hello world"
      val actual = sanitizer.sanitizeFreeText(input)
      actual mustBe expected
    }

    "remove multiple spaces after making replacements" in new TestFixture {
      val input = "hello\t\t\t<<<>>>world"
      val expected = "hello world"
      val actual = sanitizer.sanitizeFreeText(input)
      actual mustBe expected
    }
  }
  trait TestFixture {
    val sanitizer = TextSanitizer
  }
}
