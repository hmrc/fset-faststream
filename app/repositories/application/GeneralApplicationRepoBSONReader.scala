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

package repositories.application

import model.{ ApplicationRoute, Schemes }
import model.ApplicationRoute._
import model.ApplicationStatus.{ apply => _, _ }
import model.AssessmentScheduleCommands.ApplicationForAssessmentAllocation
import model.CivilServiceExperienceType.{ apply => _ }
import model.Commands.{ CandidateScoresSummary, PersonalInfo, SchemeEvaluation, _ }
import model.EvaluationResults.Result
import model.InternshipType.{ apply => _ }
import model.persisted._
import org.joda.time.{ DateTime, LocalDate }
import reactivemongo.bson.{ BSONDocument, _ }
import repositories._

trait GeneralApplicationRepoBSONReader extends BaseBSONReader {
  this: BSONHelpers =>

  implicit val toApplicationsForAssessmentAllocation = bsonReader {
    (doc: BSONDocument) => {
      val userId = doc.getAs[String]("userId").get
      val applicationId = doc.getAs[String]("applicationId").get
      val personalDetails = doc.getAs[BSONDocument]("personal-details").get
      val firstName = personalDetails.getAs[String]("firstName").get
      val lastName = personalDetails.getAs[String]("lastName").get
      val assistanceDetails = doc.getAs[BSONDocument]("assistance-details").get
      val needsSupportAtVenue = assistanceDetails.getAs[Boolean]("needsSupportAtVenue").flatMap(b => Some(booleanTranslator(b))).get
      val onlineTestDetails = doc.getAs[BSONDocument]("online-tests").get
      val invitationDate = onlineTestDetails.getAs[DateTime]("invitationDate").get
      ApplicationForAssessmentAllocation(firstName, lastName, userId, applicationId, needsSupportAtVenue, invitationDate)
    }
  }

  implicit val toApplicationForNotification = bsonReader {
    (doc: BSONDocument) => {
      val applicationId = doc.getAs[String]("applicationId").get
      val userId = doc.getAs[String]("userId").get
      val applicationStatus = doc.getAs[ApplicationStatus]("applicationStatus").get
      val personalDetailsRoot = doc.getAs[BSONDocument]("personal-details").get
      val preferredName = personalDetailsRoot.getAs[String]("preferredName").get
      ApplicationForNotification(applicationId, userId, preferredName, applicationStatus)
    }
  }

  implicit val toCandidate = bsonReader {
    (doc: BSONDocument) => {
      val userId = doc.getAs[String]("userId").getOrElse("")
      val applicationId = doc.getAs[String]("applicationId")
      // If the application does not have applicationRoute, it is legacy data
      // as it needs to be interpreted as Faststream
      val applicationRoute = doc.getAs[ApplicationRoute]("applicationRoute").getOrElse(ApplicationRoute.Faststream)

      val psRoot = doc.getAs[BSONDocument]("personal-details")
      val firstName = psRoot.flatMap(_.getAs[String]("firstName"))
      val lastName = psRoot.flatMap(_.getAs[String]("lastName"))
      val preferredName = psRoot.flatMap(_.getAs[String]("preferredName"))
      val dateOfBirth = psRoot.flatMap(_.getAs[LocalDate]("dateOfBirth"))

      Candidate(userId, applicationId, None, firstName, lastName, preferredName, dateOfBirth, None, None, None, Some(applicationRoute))
    }
  }


  implicit val toApplicationPreferencesWithTestResults = bsonReader {
    (doc: BSONDocument) => {
      val userId = document.getAs[String]("userId").getOrElse("")

      val fr = document.getAs[BSONDocument]("framework-preferences")

      val fr1 = fr.flatMap(_.getAs[BSONDocument]("firstLocation"))
      val fr1FirstLocation = fr1.flatMap(_.getAs[String]("location"))
      val fr1FirstFramework = fr1.flatMap(_.getAs[String]("firstFramework"))
      val fr1SecondFramework = fr1.flatMap(_.getAs[String]("secondFramework"))

      val fr2 = fr.flatMap(_.getAs[BSONDocument]("secondLocation"))
      val fr2FirstLocation = fr2.flatMap(_.getAs[String]("location"))
      val fr2FirstFramework = fr2.flatMap(_.getAs[String]("firstFramework"))
      val fr2SecondFramework = fr2.flatMap(_.getAs[String]("secondFramework"))

      val frAlternatives = fr.flatMap(_.getAs[BSONDocument]("alternatives"))
      val location = frAlternatives.flatMap(_.getAs[IsNonSubmitted]("location").map(booleanTranslator))
      val framework = frAlternatives.flatMap(_.getAs[IsNonSubmitted]("framework").map(booleanTranslator))

      val applicationId = document.getAs[String]("applicationId").getOrElse("")

      val pe = document.getAs[BSONDocument]("assessment-centre-passmark-evaluation")

      val ca = pe.flatMap(_.getAs[BSONDocument]("competency-average"))
      val leadingAndCommunicatingAverage = ca.flatMap(_.getAs[Double]("leadingAndCommunicatingAverage"))
      val collaboratingAndPartneringAverage = ca.flatMap(_.getAs[Double]("collaboratingAndPartneringAverage"))
      val deliveringAtPaceAverage = ca.flatMap(_.getAs[Double]("deliveringAtPaceAverage"))
      val makingEffectiveDecisionsAverage = ca.flatMap(_.getAs[Double]("makingEffectiveDecisionsAverage"))
      val changingAndImprovingAverage = ca.flatMap(_.getAs[Double]("changingAndImprovingAverage"))
      val buildingCapabilityForAllAverage = ca.flatMap(_.getAs[Double]("buildingCapabilityForAllAverage"))
      val motivationFitAverage = ca.flatMap(_.getAs[Double]("motivationFitAverage"))
      val overallScore = ca.flatMap(_.getAs[Double]("overallScore"))

      val se = pe.flatMap(_.getAs[BSONDocument]("schemes-evaluation"))
      val commercial = se.flatMap(_.getAs[String](Schemes.Commercial).map(Result(_).toPassmark))
      val digitalAndTechnology = se.flatMap(_.getAs[String](Schemes.DigitalAndTechnology).map(Result(_).toPassmark))
      val business = se.flatMap(_.getAs[String](Schemes.Business).map(Result(_).toPassmark))
      val projectDelivery = se.flatMap(_.getAs[String](Schemes.ProjectDelivery).map(Result(_).toPassmark))
      val finance = se.flatMap(_.getAs[String](Schemes.Finance).map(Result(_).toPassmark))

      val pd = document.getAs[BSONDocument]("personal-details")
      val firstName = pd.flatMap(_.getAs[String]("firstName"))
      val lastName = pd.flatMap(_.getAs[String]("lastName"))
      val preferredName = pd.flatMap(_.getAs[String]("preferredName"))
      val aLevel = pd.flatMap(_.getAs[IsNonSubmitted]("aLevel").map(booleanTranslator))
      val stemLevel = pd.flatMap(_.getAs[IsNonSubmitted]("stemLevel").map(booleanTranslator))

      ApplicationPreferencesWithTestResults(userId, applicationId, fr1FirstLocation, fr1FirstFramework,
        fr1SecondFramework, fr2FirstLocation, fr2FirstFramework, fr2SecondFramework, location, framework,
        PersonalInfo(firstName, lastName, preferredName, aLevel, stemLevel),
        CandidateScoresSummary(leadingAndCommunicatingAverage, collaboratingAndPartneringAverage,
          deliveringAtPaceAverage, makingEffectiveDecisionsAverage, changingAndImprovingAverage,
          buildingCapabilityForAllAverage, motivationFitAverage, overallScore),
        SchemeEvaluation(commercial, digitalAndTechnology, business, projectDelivery, finance))
    }
  }
}
