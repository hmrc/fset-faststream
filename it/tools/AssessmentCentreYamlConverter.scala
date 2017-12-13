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

package tools

import java.io.{File, PrintWriter}

import scala.collection.immutable.ListMap
import scala.io.Source
import scala.util.{Failure, Success, Try}

object AssessmentCentreYamlConverter extends App {
  val AssessmentsCSV = "/resources/assessment-centres.csv"
  val YamlSuffix = ".yaml"

  val pathToCsv = (getClass getResource AssessmentsCSV).getPath
  val pathToYamlDev = pathToCsv.dropRight(4) + YamlSuffix
  val pathToYamlProd = pathToCsv.dropRight(4) + "-prod" + YamlSuffix

  lazy val lines = Source.fromURL(getClass getResource AssessmentsCSV).getLines().toList

  lazy val linesWithoutHeadersByRegion = lines.tail.map(_.split(",").toList).groupBy(_.head)

  def sortVenuesInRegionByVenueName(venuesInRegion: Map[String, List[AssessmentVenue]]) = {
    ListMap(venuesInRegion.toSeq.sortBy(_._1):_*)
  }

  def locationDetailsListToTuple(locationDetails: List[List[String]]) = {
    locationDetails.map {
      case _ :: venueDescription :: venueName :: capacityInfo => AssessmentVenue(venueName, venueDescription, capacityInfo)
      case _ => throw new IllegalArgumentException("There is a malformed line in the input.")
    }.groupBy(_.venueName)
  }

  lazy val parsedYaml = linesWithoutHeadersByRegion
    .map {
      case (region, locationDetails) =>
        val venuesInRegion = locationDetailsListToTuple(locationDetails)
        val sortedVenuesInRegion = sortVenuesInRegionByVenueName(venuesInRegion)

        (region, sortedVenuesInRegion)
    }

  lazy val stringToWrite = parsedYaml.map { case (loc, venues) =>
    val venuesStrList = venues.map { case (_, venueDetails) =>

      val capacities = "" +
          "      capacities:\n" + venueDetails.map { venueInfo =>

          s"""         - date: ${venueInfo.capacityInfo.head}
            |           amCapacity: ${venueInfo.capacityInfo(1)}
            |           pmCapacity: ${venueInfo.capacityInfo(2)}""".stripMargin
        }.mkString("\n")

        s"""  - ${venueDetails.head.venueName}:
           |      description: ${venueDetails.head.venueDescription}
           |$capacities""".stripMargin
    }

    val venuesStr = venuesStrList.mkString("\n")

    s"""$loc:
       |$venuesStr
     """.stripMargin
  }

  //scalastyle:off printlnRegex
  println("#################################################")
  println("#### Assessment Centre CSV to YAML Converter ####")
  println("#################################################")
  println("- Converting CSV to YAML: " + pathToCsv)
  writeFile(pathToYamlDev)
  writeFile(pathToYamlProd)
  println("- Done.")
  println()
  println("IMPORTANT: Remember to add one year to all dates in the dev environment configuration in order to pass Acceptance Tests")

  def writeFile(pathToOutputFile: String): Unit = {
    val assessmentYamlFile = new File(pathToOutputFile)
    val result = Try {
      val writer = new PrintWriter(new File(pathToOutputFile))
      writer.write(stringToWrite.mkString("\n"))
      writer.close()
    }

    result match {
      case Success(_) =>
        println("  - YAML file written: " + assessmentYamlFile.getAbsolutePath)
      case Failure(ex) =>
        println("  - Error writing YAML file: ")
        ex.printStackTrace()
    }
  }
  //scalastyle:on printlnRegex
}

