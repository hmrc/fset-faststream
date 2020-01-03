/*
 * Copyright 2020 HM Revenue & Customs
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

package repositories

import model.EvaluationResults._
import model.{ Scheme, SchemeId }
import model.persisted.SchemeEvaluationResult
import reactivemongo.bson.BSONDocument

import scala.annotation.tailrec

trait CurrentSchemeStatusHelper {

  def calculateCurrentSchemeStatus(existingEvaluations: Seq[SchemeEvaluationResult],
    newEvaluations: Seq[SchemeEvaluationResult]): Seq[SchemeEvaluationResult] = {

    @tailrec
    def accumulateStatus(existingStatuses: Seq[SchemeEvaluationResult], newStatuses: Seq[SchemeEvaluationResult],
      accum : Seq[SchemeEvaluationResult]
    ):Seq[SchemeEvaluationResult] = existingStatuses match {
      case Nil => if (accum.isEmpty) newStatuses else accum ++ newStatuses
      case head :: tail =>
        val (updated, reducedNewStatuses) = newStatuses.find(_.schemeId == head.schemeId).map { newStatus =>
          (
            SchemeEvaluationResult(head.schemeId, (Result(head.result) + Result(newStatus.result)).toString),
            newStatuses.filterNot(_.schemeId == newStatus.schemeId)
          )
        }.getOrElse(head, newStatuses)

        accumulateStatus(tail, reducedNewStatuses, accum :+ updated)
    }

    accumulateStatus(existingEvaluations, newEvaluations, Nil)
  }

  def currentSchemeStatusBSON(latestResults: Seq[SchemeEvaluationResult]): BSONDocument = {
    BSONDocument("currentSchemeStatus" -> latestResults.map { r =>
      SchemeEvaluationResult.bsonHandler.write(r)
    })
  }

  def currentSchemeStatusGreen(schemeIds: SchemeId*): BSONDocument = currentSchemeStatus(Green, schemeIds:_*)

  def currentSchemeStatusRed(schemeIds: SchemeId*): BSONDocument = currentSchemeStatus(Red, schemeIds:_*)

  def currentSchemeStatusAmber(schemeIds: SchemeId*): BSONDocument = currentSchemeStatus(Amber, schemeIds:_*)

  def currentSchemeStatusWithdrawn(schemeIds: SchemeId*): BSONDocument = currentSchemeStatus(Withdrawn, schemeIds:_*)

  private def currentSchemeStatus(status: Result, schemeIds: SchemeId*): BSONDocument = {
    schemeIds.foldLeft(BSONDocument.empty) { case (doc, id) =>
      doc ++ BSONDocument(s"currentSchemeStatus" -> BSONDocument("$elemMatch" -> SchemeEvaluationResult(id, status.toString)))
    }
  }

  def isFirstResidualPreference(schemeId: SchemeId): BSONDocument = {
    BSONDocument("$where" ->
      s"""
        |var greens = this.currentSchemeStatus.filter(
        |   function(e){
        |     return e.result=="$Green"
        |   }
        |);
        |greens.length > 0 && greens[0].schemeId=="$schemeId";
      """.stripMargin)
  }

  def firstResidualPreference(results: Seq[SchemeEvaluationResult], ignoreSdip: Boolean = false): Option[SchemeEvaluationResult] = {
    val resultsWithIndex = results.zipWithIndex

    val amberOrGreenPreferences = resultsWithIndex.filterNot { case (result, _) =>
      if (ignoreSdip) {
        result.result == Red.toString || result.result == Withdrawn.toString || result.schemeId == SchemeId(Scheme.Sdip)
      } else {
        result.result == Red.toString || result.result == Withdrawn.toString
      }
    }

    amberOrGreenPreferences match {
      case Nil => None
      case list => Some(list.minBy { case (_, id) => id }._1)
    }
  }
}
