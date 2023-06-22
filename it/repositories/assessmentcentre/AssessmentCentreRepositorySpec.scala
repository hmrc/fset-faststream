/*
 * Copyright 2017 HM Revenue & Customs
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

package repositories.assessmentcentre

import model.EvaluationResults.{Amber, AssessmentEvaluationResult, CompetencyAverageResult, ExerciseAverageResult, FsacResults, Green, Red}
import model.Exceptions.NotFoundException
import model.ProgressStatuses.{ASSESSMENT_CENTRE_AWAITING_ALLOCATION, ASSESSMENT_CENTRE_SCORES_ACCEPTED}
import model.assessmentscores.FixUserStuckInScoresAccepted
import model.{UniqueIdentifier, _}
import model.command.ApplicationForProgression
import model.persisted.SchemeEvaluationResult
import model.persisted.fsac.{AnalysisExercise, AssessmentCentreTests}
import org.scalatest.concurrent.ScalaFutures
import repositories.{CollectionNames, CommonRepository}
import testkit.MongoRepositorySpec

class AssessmentCentreRepositorySpec extends MongoRepositorySpec with ScalaFutures with CommonRepository {

  val collectionName: String = CollectionNames.APPLICATION

  val Commercial: SchemeId = SchemeId("Commercial")
  val Generalist: SchemeId = SchemeId("Generalist")
  val ProjectDelivery = SchemeId("Project Delivery")

  "next Application for assessment centre" must {
    "return applications from phase 1, 3 and sift stages" in {
      insertApplicationWithPhase3TestNotifiedResults("appId1",
        List(SchemeEvaluationResult(SchemeId("HumanResources"), EvaluationResults.Green.toString))).futureValue

      insertApplicationWithPhase3TestNotifiedResults("appId2",
        List(SchemeEvaluationResult(SchemeId("GovernmentEconomicsService"), EvaluationResults.Green.toString))).futureValue
      updateApplicationStatus("appId2", ApplicationStatus.PHASE3_TESTS_PASSED)

      insertApplicationWithPhase3TestNotifiedResults("appId3",
        List(SchemeEvaluationResult(SchemeId("GovernmentEconomicsService"), EvaluationResults.Green.toString))).futureValue
      updateApplicationStatus("appId3", ApplicationStatus.PHASE3_TESTS_FAILED)

      insertApplicationWithPhase3TestNotifiedResults("appId4",
        List(SchemeEvaluationResult(SchemeId("Project Delivery"), EvaluationResults.Green.toString))).futureValue

      insertApplicationWithPhase3TestNotifiedResults("appId5",
        List(SchemeEvaluationResult(SchemeId("DiplomaticAndDevelopment"), EvaluationResults.Green.toString))).futureValue

      insertApplicationWithPhase3TestNotifiedResults("appId6",
        List(SchemeEvaluationResult(SchemeId("Generalist"), EvaluationResults.Red.toString))).futureValue

      insertApplicationWithPhase3TestNotifiedResults("appId7",
        List(SchemeEvaluationResult(SchemeId("Digital and technology"), EvaluationResults.Green.toString))).futureValue

      whenReady(assessmentCentreRepository.nextApplicationForAssessmentCentre(10)) { appsForAc =>
        appsForAc must contain theSameElementsAs Seq(
          ApplicationForProgression("appId1", ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED,
            List(SchemeEvaluationResult(SchemeId("HumanResources"), EvaluationResults.Green.toString))),
          ApplicationForProgression("appId4", ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED,
            List(SchemeEvaluationResult(SchemeId("Project Delivery"), EvaluationResults.Green.toString))),
          ApplicationForProgression("appId7", ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED,
            List(SchemeEvaluationResult(SchemeId("Digital and technology"), EvaluationResults.Green.toString)))
        )
        appsForAc.length mustBe 3
      }
    }

    "return no results when there are only phase 3 applications that aren't in Passed_Notified which don't apply for sift or don't have " +
      "Green/Passed results" in {
      insertApplicationWithPhase3TestNotifiedResults("appId7",
        List(SchemeEvaluationResult(SchemeId("GovernmentEconomicsService"), EvaluationResults.Green.toString))).futureValue
      insertApplicationWithPhase3TestNotifiedResults("appId8",
        List(SchemeEvaluationResult(SchemeId("Generalist"), EvaluationResults.Green.toString))).futureValue
      updateApplicationStatus("appId8", ApplicationStatus.PHASE3_TESTS_FAILED)
      insertApplicationWithPhase3TestNotifiedResults("appId9",
        List(SchemeEvaluationResult(SchemeId("GovernmentEconomicsService"), EvaluationResults.Green.toString))).futureValue
      insertApplicationWithPhase3TestNotifiedResults("appId10",
        List(SchemeEvaluationResult(SchemeId("Project Delivery"), EvaluationResults.Red.toString))).futureValue

      whenReady(assessmentCentreRepository.nextApplicationForAssessmentCentre(10)) { appsForAc =>
        appsForAc mustBe Nil
      }
    }
  }

  "progressToAssessmentCentre" must {
    "ignore candidates who only have Sdip/Edip green at the end of sifting" in {
      insertApplicationWithSiftCompleted("appId1",
        List(SchemeEvaluationResult(Sdip, EvaluationResults.Green.toString),
          SchemeEvaluationResult(Generalist, EvaluationResults.Red.toString),
          SchemeEvaluationResult(GovernmentEconomicsService, EvaluationResults.Red.toString)))
      insertApplicationWithSiftCompleted("appId2",
        List(SchemeEvaluationResult(Edip, EvaluationResults.Green.toString),
          SchemeEvaluationResult(Generalist, EvaluationResults.Red.toString),
          SchemeEvaluationResult(GovernmentEconomicsService, EvaluationResults.Red.toString)))
      insertApplicationWithSiftCompleted("appId3",
        List(SchemeEvaluationResult(SchemeId("Finance"), EvaluationResults.Green.toString),
          SchemeEvaluationResult(Generalist, EvaluationResults.Red.toString),
          SchemeEvaluationResult(GovernmentEconomicsService, EvaluationResults.Green.toString)))

      whenReady(assessmentCentreRepository.nextApplicationForAssessmentCentre(1)) { result =>
        result mustBe ApplicationForProgression("appId3", ApplicationStatus.SIFT,
          List(SchemeEvaluationResult(Finance, EvaluationResults.Green.toString),
            SchemeEvaluationResult(Generalist, EvaluationResults.Red.toString),
            SchemeEvaluationResult(GovernmentEconomicsService, EvaluationResults.Green.toString))
        ) :: Nil
      }
    }

    "progress candidates who have completed the sift phase" in {
      insertApplicationWithSiftCompleted("appId11",
        List(SchemeEvaluationResult(SchemeId("Finance"), EvaluationResults.Green.toString),
          SchemeEvaluationResult(Generalist, EvaluationResults.Red.toString),
          SchemeEvaluationResult(GovernmentEconomicsService, EvaluationResults.Green.toString)))


      val nextResults = assessmentCentreRepository.nextApplicationForAssessmentCentre(1).futureValue
      nextResults mustBe List(
        ApplicationForProgression("appId11", ApplicationStatus.SIFT,
          List(SchemeEvaluationResult(Finance, EvaluationResults.Green.toString),
            SchemeEvaluationResult(Generalist, EvaluationResults.Red.toString),
            SchemeEvaluationResult(GovernmentEconomicsService, EvaluationResults.Green.toString))
        )
      )

      assessmentCentreRepository.progressToAssessmentCentre(nextResults.head, ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION).futureValue
      val result = applicationRepository.findProgress("appId11").futureValue
      result.assessmentCentre.awaitingAllocation mustBe true
    }
  }

  "getAssessmentScoreEvaluation" must {
    "retrieve assessment results when present" in new TestFixture {
      val appId: UniqueIdentifier = UniqueIdentifier.randomUniqueIdentifier

      insertApplicationWithAssessmentCentreAwaitingAllocation(appId.toString())

      assessmentCentreRepository.saveAssessmentScoreEvaluation(
        AssessmentPassMarkEvaluation(
          appId,
          "passMarkVersion",
          AssessmentEvaluationResult(
            FsacResults(
              CompetencyAverageResult(1.0, 2.0, 3.0, 4.0, 5.0),
              ExerciseAverageResult(1.0, 2.0, 3.0, 4.0)
            ),
            Seq(SchemeEvaluationResult("GovernmentCommunicationService", Green.toString))
          )
        ),
        Seq(SchemeEvaluationResult("GovernmentCommunicationService", Green.toString))
      ).futureValue

      val result: Option[AssessmentPassMarkEvaluation] = assessmentCentreRepository.getAssessmentScoreEvaluation(appId.toString()).futureValue

      result mustBe defined
    }

    "return None when not present" in new TestFixture {
      assessmentCentreRepository.getAssessmentScoreEvaluation("appId1").futureValue must not be defined
    }
  }

  "getTests" must {
    "get tests when they exist" in new TestFixture {
      insertApplicationWithAssessmentCentreAwaitingAllocation("appId1")
      assessmentCentreRepository.getTests("appId1").futureValue mustBe expectedAssessmentCentreTests
    }

    "return empty when there are no tests" in new TestFixture {
      insertApplicationWithAssessmentCentreAwaitingAllocation("appId1", withTests = false)
      assessmentCentreRepository.getTests("appId1").futureValue mustBe AssessmentCentreTests()
    }
  }

  "updateTests" must {
    "update the tests key and be retrievable" in new TestFixture {
      insertApplication("appId1", ApplicationStatus.ASSESSMENT_CENTRE,
        additionalProgressStatuses = List(ASSESSMENT_CENTRE_AWAITING_ALLOCATION -> true)
      )

      assessmentCentreRepository.updateTests("appId1", expectedAssessmentCentreTests).futureValue
      assessmentCentreRepository.getTests("appId1").futureValue mustBe expectedAssessmentCentreTests
    }
  }

  "nextApplicationReadyForAssessmentScoreEvaluation" must {
    "handle no eligible candidates" in new TestFixture {
      val result = assessmentCentreRepository.nextApplicationReadyForAssessmentScoreEvaluation(currentPassmarkVersion = "v1", 10).futureValue
      result mustBe Nil
    }

    "handle eligible candidates who have not been evaluated previously" in new TestFixture {
      insertApplication(guid, ApplicationStatus.ASSESSMENT_CENTRE,
        additionalProgressStatuses = List(ASSESSMENT_CENTRE_SCORES_ACCEPTED -> true)
      )

      val result = assessmentCentreRepository.nextApplicationReadyForAssessmentScoreEvaluation("v1", 10).futureValue
      result mustBe Seq(UniqueIdentifier(guid))
    }

    "handle eligible candidates who have been evaluated previously but now pass marks have changed" in new TestFixture {
      insertApplication(guid, ApplicationStatus.ASSESSMENT_CENTRE,
        additionalProgressStatuses = List(ASSESSMENT_CENTRE_SCORES_ACCEPTED -> true)
      )

      assessmentCentreRepository.saveAssessmentScoreEvaluation(
        AssessmentPassMarkEvaluation(
          UniqueIdentifier(guid),
          "passMarkVersion1",
          AssessmentEvaluationResult(
            FsacResults(
              CompetencyAverageResult(1.0, 2.0, 3.0, 4.0, 5.0),
              ExerciseAverageResult(1.0, 2.0, 3.0, 4.0)
            ),
            Seq(SchemeEvaluationResult("Commercial", Green.toString))
          )
        ),
        Seq(SchemeEvaluationResult("Commercial", Green.toString))
      ).futureValue

      val result = assessmentCentreRepository.nextApplicationReadyForAssessmentScoreEvaluation("passMarkVersion2", 10).futureValue
      result mustBe Seq(UniqueIdentifier(guid))
    }
  }

  "nextSpecificCandidateReadyForEvaluation" must {
    "handle no eligible candidates" in new TestFixture {
      val result = assessmentCentreRepository.nextSpecificApplicationReadyForAssessmentScoreEvaluation("v1", "appId").futureValue
      result mustBe Nil
    }

    "handle eligible candidate who has not been evaluated previously" in new TestFixture {
      insertApplication(guid, ApplicationStatus.ASSESSMENT_CENTRE,
        additionalProgressStatuses = List(ASSESSMENT_CENTRE_SCORES_ACCEPTED -> true)
      )

      val result = assessmentCentreRepository.nextSpecificApplicationReadyForAssessmentScoreEvaluation("v1", guid).futureValue
      result mustBe Seq(UniqueIdentifier(guid))
    }

    "handle eligible candidate who has been evaluated previously but now pass marks have changed" in new TestFixture {
      insertApplication(guid, ApplicationStatus.ASSESSMENT_CENTRE,
        additionalProgressStatuses = List(ASSESSMENT_CENTRE_SCORES_ACCEPTED -> true)
      )

      assessmentCentreRepository.saveAssessmentScoreEvaluation(
        AssessmentPassMarkEvaluation(
          UniqueIdentifier(guid),
          "passMarkVersion1",
          AssessmentEvaluationResult(
            FsacResults(
              CompetencyAverageResult(1.0, 2.0, 3.0, 4.0, 5.0),
              ExerciseAverageResult(1.0, 2.0, 3.0, 4.0)
            ),
            Seq(SchemeEvaluationResult("Commercial", Green.toString))
          )
        ),
        Seq(SchemeEvaluationResult("Commercial", Green.toString))
      ).futureValue

      val result = assessmentCentreRepository.nextSpecificApplicationReadyForAssessmentScoreEvaluation("passMarkVersion2", guid).futureValue
      result mustBe Seq(UniqueIdentifier(guid))
    }
  }

  "getFsacEvaluationResultAverages" must {
    "handle no eligible candidates" in new TestFixture {
      assessmentCentreRepository.getFsacEvaluationResultAverages("appId").futureValue mustBe None
    }

    "handle candidate who has already been evaluated" in new TestFixture {
      insertApplication(guid, ApplicationStatus.ASSESSMENT_CENTRE,
        additionalProgressStatuses = List(ASSESSMENT_CENTRE_SCORES_ACCEPTED -> true)
      )

      val competencyAverageResult = CompetencyAverageResult(1.0, 2.0, 3.0, 4.0, 5.0)

      assessmentCentreRepository.saveAssessmentScoreEvaluation(
        AssessmentPassMarkEvaluation(
          UniqueIdentifier(guid),
          "passMarkVersion1",
          AssessmentEvaluationResult(
            FsacResults(
              competencyAverageResult,
              ExerciseAverageResult(1.0, 2.0, 3.0, 4.0)
            ),
            Seq(SchemeEvaluationResult("Commercial", Green.toString))
          )
        ),
        Seq(SchemeEvaluationResult("Commercial", Green.toString))
      ).futureValue

      assessmentCentreRepository.getFsacEvaluationResultAverages(guid).futureValue mustBe Some(competencyAverageResult)
    }

    "handle candidate who has not been evaluated" in new TestFixture {
      insertApplication(guid, ApplicationStatus.ASSESSMENT_CENTRE,
        additionalProgressStatuses = List(ASSESSMENT_CENTRE_SCORES_ACCEPTED -> true)
      )

      assessmentCentreRepository.getFsacEvaluationResultAverages(guid).futureValue mustBe None
    }
  }

  "getFsacEvaluatedSchemes" must {
    "handle no eligible candidates" in new TestFixture {
      assessmentCentreRepository.getFsacEvaluatedSchemes("appId").futureValue mustBe None
    }

    "handle candidate who has already been evaluated" in new TestFixture {
      insertApplication(guid, ApplicationStatus.ASSESSMENT_CENTRE,
        additionalProgressStatuses = List(ASSESSMENT_CENTRE_SCORES_ACCEPTED -> true)
      )

      val schemeEvaluationResult = Seq(
        SchemeEvaluationResult("Commercial", Green.toString),
        SchemeEvaluationResult("Generalist", Green.toString)
      )

      assessmentCentreRepository.saveAssessmentScoreEvaluation(
        AssessmentPassMarkEvaluation(
          UniqueIdentifier(guid),
          "passMarkVersion1",
          AssessmentEvaluationResult(
            FsacResults(
              CompetencyAverageResult(1.0, 2.0, 3.0, 4.0, 5.0),
              ExerciseAverageResult(1.0, 2.0, 3.0, 4.0)
            ),
            schemeEvaluationResult
          )
        ),
        schemeEvaluationResult
      ).futureValue

      assessmentCentreRepository.getFsacEvaluatedSchemes(guid).futureValue mustBe Some(schemeEvaluationResult)
    }
  }

  "removeFsacEvaluation" must {
    "handle no matching candidate" in new TestFixture {
      assessmentCentreRepository.removeFsacEvaluation("appId").failed.futureValue mustBe a[NotFoundException]
    }

    "handle candidate who has already been evaluated" in new TestFixture {
      insertApplication(guid, ApplicationStatus.ASSESSMENT_CENTRE,
        additionalProgressStatuses = List(ASSESSMENT_CENTRE_SCORES_ACCEPTED -> true)
      )

      assessmentCentreRepository.saveAssessmentScoreEvaluation(
        AssessmentPassMarkEvaluation(
          UniqueIdentifier(guid),
          "passMarkVersion1",
          AssessmentEvaluationResult(
            FsacResults(
              CompetencyAverageResult(1.0, 2.0, 3.0, 4.0, 5.0),
              ExerciseAverageResult(1.0, 2.0, 3.0, 4.0)
            ),
            Seq(SchemeEvaluationResult("Commercial", Green.toString))
          )
        ),
        Seq(SchemeEvaluationResult("Commercial", Green.toString))
      ).futureValue

      assessmentCentreRepository.getAssessmentScoreEvaluation(guid).futureValue mustBe defined
      assessmentCentreRepository.removeFsacEvaluation(guid).futureValue
      // Verify that all methods that fetch data handle the data being absent
      assessmentCentreRepository.getAssessmentScoreEvaluation(guid).futureValue mustBe empty
      assessmentCentreRepository.getFsacEvaluationResultAverages(guid).futureValue mustBe empty
      assessmentCentreRepository.getFsacEvaluatedSchemes(guid).futureValue mustBe empty
      assessmentCentreRepository.getTests(guid).futureValue mustBe AssessmentCentreTests()
    }
  }

  "removeFsacTestGroup" must {
    "handle no matching candidate" in new TestFixture {
      assessmentCentreRepository.removeFsacTestGroup("appId").failed.futureValue mustBe a[NotFoundException]
    }

    "handle candidate who has already been evaluated" in new TestFixture {
      insertApplication(guid, ApplicationStatus.ASSESSMENT_CENTRE,
        additionalProgressStatuses = List(ASSESSMENT_CENTRE_SCORES_ACCEPTED -> true)
      )

      assessmentCentreRepository.saveAssessmentScoreEvaluation(
        AssessmentPassMarkEvaluation(
          UniqueIdentifier(guid),
          "passMarkVersion1",
          AssessmentEvaluationResult(
            FsacResults(
              CompetencyAverageResult(1.0, 2.0, 3.0, 4.0, 5.0),
              ExerciseAverageResult(1.0, 2.0, 3.0, 4.0)
            ),
            Seq(SchemeEvaluationResult("Commercial", Green.toString))
          )
        ),
        Seq(SchemeEvaluationResult("Commercial", Green.toString))
      ).futureValue

      assessmentCentreRepository.getAssessmentScoreEvaluation(guid).futureValue mustBe defined
      assessmentCentreRepository.removeFsacTestGroup(guid).futureValue
      // Verify that all methods that fetch data handle the data being absent
      assessmentCentreRepository.getAssessmentScoreEvaluation(guid).futureValue mustBe empty
      assessmentCentreRepository.getFsacEvaluationResultAverages(guid).futureValue mustBe empty
      assessmentCentreRepository.getFsacEvaluatedSchemes(guid).futureValue mustBe empty
      assessmentCentreRepository.getTests(guid).futureValue mustBe AssessmentCentreTests()
    }
  }

  "findNonPassedNonFailedNonAmberUsersInAssessmentScoresAccepted" must {
    "handle no candidates" in new TestFixture {
      assessmentCentreRepository.findNonPassedNonFailedNonAmberUsersInAssessmentScoresAccepted.futureValue mustBe Nil
    }

    "match candidates who are evaluated to Green and Red" in new TestFixture {
      insertApplication(guid, ApplicationStatus.ASSESSMENT_CENTRE)

      assessmentCentreRepository.saveAssessmentScoreEvaluation(
        AssessmentPassMarkEvaluation(
          UniqueIdentifier(guid),
          "passMarkVersion1",
          AssessmentEvaluationResult(
            FsacResults(
              CompetencyAverageResult(1.0, 2.0, 3.0, 4.0, 5.0),
              ExerciseAverageResult(1.0, 2.0, 3.0, 4.0)
            ),
            Seq(
              SchemeEvaluationResult("Commercial", Green.toString),
              SchemeEvaluationResult("Generalist", Red.toString)
            )
          )
        ),
        Seq(
          SchemeEvaluationResult("Commercial", Green.toString),
          SchemeEvaluationResult("Generalist", Red.toString)
        )
      ).futureValue

      val result = assessmentCentreRepository.findNonPassedNonFailedNonAmberUsersInAssessmentScoresAccepted.futureValue
      result mustBe Seq(FixUserStuckInScoresAccepted(
        guid,
        Seq(
          SchemeEvaluationResult("Commercial", Green.toString),
          SchemeEvaluationResult("Generalist", Red.toString)
        )
      ))
    }

    "not match candidates who have an Amber" in new TestFixture {
      insertApplication(guid, ApplicationStatus.ASSESSMENT_CENTRE)

      assessmentCentreRepository.saveAssessmentScoreEvaluation(
        AssessmentPassMarkEvaluation(
          UniqueIdentifier(guid),
          "passMarkVersion1",
          AssessmentEvaluationResult(
            FsacResults(
              CompetencyAverageResult(1.0, 2.0, 3.0, 4.0, 5.0),
              ExerciseAverageResult(1.0, 2.0, 3.0, 4.0)
            ),
            Seq(
              SchemeEvaluationResult("Commercial", Green.toString),
              SchemeEvaluationResult("Generalist", Amber.toString)
            )
          )
        ),
        Seq(
          SchemeEvaluationResult("Commercial", Green.toString),
          SchemeEvaluationResult("Generalist", Amber.toString)
        )
      ).futureValue

      assessmentCentreRepository.findNonPassedNonFailedNonAmberUsersInAssessmentScoresAccepted.futureValue mustBe Nil
    }
  }

  trait TestFixture {
    val expectedAssessmentCentreTests = AssessmentCentreTests(
      Some(AnalysisExercise(
        fileId = "fileId1"
      ))
    )

    val guid = "0a23688a-7c9d-4d67-9ab2-403d7c234bc6"

    def insertApplicationWithAssessmentCentreAwaitingAllocation(appId: String, withTests: Boolean = true): Unit = {
      insertApplication(appId, ApplicationStatus.ASSESSMENT_CENTRE,
        additionalProgressStatuses = List(ASSESSMENT_CENTRE_AWAITING_ALLOCATION -> true)
      )

      if (withTests) {
        assessmentCentreRepository.updateTests(appId, expectedAssessmentCentreTests).futureValue
      }
    }
  }
}
