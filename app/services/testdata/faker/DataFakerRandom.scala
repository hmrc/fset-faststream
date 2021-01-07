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

package services.testdata.faker

import model.Exceptions.DataFakingException

import scala.language.postfixOps

trait DataFakerRandom {

  object Random {
    val scalaRandom = scala.util.Random

    def randOne[T](options: List[T], cannotBe: List[T] = Nil) = {
      val filtered = options.filterNot(cannotBe.contains)
      if (filtered.isEmpty) {
        throw DataFakingException(s"There were no items left after filtering.")
      } else {
        scalaRandom.shuffle(filtered).head
      }
    }

    def randList[T](options: List[T], size: Int, cannotBe: List[T] = Nil): List[T] = {
      if (size > 0) {

        val filtered = options.filterNot(cannotBe.contains)
        if (filtered.isEmpty) {
          throw DataFakingException(s"There were no items left after filtering.")
        } else {
          val newItem = scalaRandom.shuffle(filtered).head
          newItem :: randList(options, size - 1, newItem :: cannotBe)
        }
      } else {
        Nil
      }
    }

    // All these methods are used when defining default values in case classes in CreateCandidateData object
    // Note these are used in both the case classes and companion objects
    def bool: Boolean = randOne(List(true, false))

    def number(limit: Option[Int] = None): Int = scalaRandom.nextInt(limit.getOrElse(2000000000))

    def monthNumber: Int = scalaRandom.nextInt(12) + 1

    def gender = randOne(List(
      "Male",
      "Female",
      "Other",
      "I don't know/prefer not to say"))

    def sexualOrientation = randOne(List(
      "Heterosexual/straight",
      "Gay/lesbian",
      "Bisexual",
      "Other",
      "I don't know/prefer not to say"))

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
      "Other ethnic group",
      "I don't know/prefer not to say"
    ))

    def university = randOne(Universities.list)

    def parentsOccupation = randOne(List(
      "Unemployed but seeking work",
      "Unemployed",
      "Employed",
      "Unknown"
    ))

    def parentsOccupationDetails: String = randOne(List(
      "Modern professional",
      "Clerical (office work) and intermediate",
      "Senior managers and administrators",
      "Technical and craft",
      "Semi-routine manual and service",
      "Routine manual and service",
      "Middle or junior managers",
      "Traditional professional"
    ))

    def sizeParentsEmployer: String = randOne(List(
      "Small (1 to 24 employees)",
      "Large (over 24 employees)",
      "I don't know/prefer not to say"
    ))

    def firstname(userNumber: Int): String = {
      val firstName = randOne(Firstnames.list)
      s"$firstName$userNumber"
    }

    def lastname(userNumber: Int): String = {
      val lastName = randOne(Lastnames.list)
      s"$lastName$userNumber"
    }
  }
}
