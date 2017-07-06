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

package repositories.application

import model.ApplicationRoute.{ apply => _ }
import model.BSONExamples
import model.ProgressStatuses.{ PHASE1_TESTS_PASSED => _, SUBMITTED => _ }
import model.report.{ CandidateProgressReportItemExamples, VideoInterviewQuestionTestResult, VideoInterviewTestResult }
import reactivemongo.bson.{ BSONArray, BSONDateTime, BSONDocument }
import testkit.UnitWithAppSpec

class ReportingRepoBSONReaderSpec extends UnitWithAppSpec {

  def bsonReader = new ReportingRepoBSONReader {}

  "toCandidateProgressReport" should {
    "return sdip candidate correctly" in new CandidateProgressReportFixture {
      val candidateProgressReportItem = bsonReader.toCandidateProgressReportItem.read(BSONExamples.SubmittedSdipCandidateWithEdipCompleted)
      candidateProgressReportItem mustBe CandidateProgressReportItemExamples.SdipCandidate
    }
  }

  "toPhase3TestResults" should {
    "return corresponding VideoInterviewTestResult when only one reviewer and one reviewed callback" in new OnlineTestPassMarkReportFixture {
      val videoInterviewTestResult = bsonReader.toPhase3TestResults(Some(oneReviewedOneReviewerReviewedPhase3BSONDoc(1.5)))
      videoInterviewTestResult mustBe
        Some(expectedBaseResult.copy(question1 = VideoInterviewQuestionTestResult(Some(1.5), Some(2.5)), overallTotal = 39.5))
    }

    "return corresponding VideoInterviewTestResult when only two reviewer and one reviewed callback" in new OnlineTestPassMarkReportFixture {
      val videoInterviewTestResult = bsonReader.toPhase3TestResults(Some(oneReviewedTwoReviewerReviewedPhase3BSONDoc(2.5, 2.5)))
      videoInterviewTestResult mustBe
        Some(expectedBaseResult.copy(question1 = VideoInterviewQuestionTestResult(Some(2.5), Some(2.5)), overallTotal = 40.5))
    }

    "return corresponding VideoInterviewTestResult when two reviewer and three reviewed callback" in new OnlineTestPassMarkReportFixture {
      val videoInterviewTestResult =
        bsonReader.toPhase3TestResults(Some(threeReviewedTwoReviewerReviewedPhase3BSONDoc(2.5, 2.5, 3.5, 1.0, 1.0, 4.0)))
      videoInterviewTestResult mustBe
        Some(expectedBaseResult.copy(question1 = VideoInterviewQuestionTestResult(Some(1.0), Some(2.5)), overallTotal = 39.0))
    }
  }

  trait OnlineTestPassMarkReportFixture {
    val baseTotalOverall = 38.0
    val expectedBaseResult = VideoInterviewTestResult(
      VideoInterviewQuestionTestResult(None, Some(2.5)),
      VideoInterviewQuestionTestResult(Some(1.5), Some(2.0)),
      VideoInterviewQuestionTestResult(Some(3.0), Some(2.5)),
      VideoInterviewQuestionTestResult(Some(3.5), Some(1.0)),
      VideoInterviewQuestionTestResult(Some(3.5), Some(2.0)),
      VideoInterviewQuestionTestResult(Some(4.0), Some(2.5)),
      VideoInterviewQuestionTestResult(Some(3.5), Some(4.0)),
      VideoInterviewQuestionTestResult(Some(1.0), Some(1.5)),
      baseTotalOverall
    )

    //scalastyle:off method.length
    // overall should be 38 + score
    def reviewerBSONDoc(score: Double) = BSONDocument(
      "name" -> "Test user 1",
      "email" -> "testuser1@localhost",
      "question1" -> BSONDocument(
        "id" -> 100,
        "reviewCriteria1" -> BSONDocument(
          "type" -> "numeric",
          "score" -> score
        ),
        "reviewCriteria2" -> BSONDocument(
          "type" -> "numeric",
          "score" -> 2.5
        )
      ),
      "question2" -> BSONDocument(
        "id" -> 101,
        "reviewCriteria1" -> BSONDocument(
          "type" -> "numeric",
          "score" -> 1.5
        ),
        "reviewCriteria2" -> BSONDocument(
          "type" -> "numeric",
          "score" -> 2.0
        )
      ),
      "question3" -> BSONDocument(
        "id" -> 102,
        "reviewCriteria1" -> BSONDocument(
          "type" -> "numeric",
          "score" -> 3.0
        ),
        "reviewCriteria2" -> BSONDocument(
          "type" -> "numeric",
          "score" -> 2.5
        )
      ),
      "question4" -> BSONDocument(
        "id" -> 103,
        "reviewCriteria1" -> BSONDocument(
          "type" -> "numeric",
          "score" -> 3.5
        ),
        "reviewCriteria2" -> BSONDocument(
          "type" -> "numeric",
          "score" -> 1.0
        )
      ),
      "question5" -> BSONDocument(
        "id" -> 104,
        "reviewCriteria1" -> BSONDocument(
          "type" -> "numeric",
          "score" -> 3.5
        ),
        "reviewCriteria2" -> BSONDocument(
          "type" -> "numeric",
          "score" -> 2.0
        )
      ),
      "question6" -> BSONDocument(
        "id" -> 105,
        "reviewCriteria1" -> BSONDocument(
          "type" -> "numeric",
          "score" -> 4.0
        ),
        "reviewCriteria2" -> BSONDocument(
          "type" -> "numeric",
          "score" -> 2.5
        )
      ),
      "question7" -> BSONDocument(
        "id" -> 106,
        "reviewCriteria1" -> BSONDocument(
          "type" -> "numeric",
          "score" -> 3.5
        ),
        "reviewCriteria2" -> BSONDocument(
          "type" -> "numeric",
          "score" -> 4.0
        )
      ),
      "question8" -> BSONDocument(
        "id" -> 107,
        "reviewCriteria1" -> BSONDocument(
          "type" -> "numeric",
          "score" -> 1.0
        ),
        "reviewCriteria2" -> BSONDocument(
          "type" -> "numeric",
          "score" -> 1.5
        )
      )
    )

    //scalastyle:on method.length

    def oneReviewedOneReviewerReviewedBSONDoc(score: Double) = BSONArray(
      BSONDocument(
        "received" -> BSONDateTime(2),
        "candidateId" -> "cnd_f9e67cbb858aed29b3884ff4a10d77e2",
        "customCandidateId" -> "FSCND-f182f623-80ba-49f4-bed2-77c3c93296d2",
        "interviewId" -> 46,
        "customInviteId" -> "FSINV-5071afd4-7f61-42e9-9e72-f621dcaec618",
        "deadline" -> "2016-11-21",
        "reviews" -> BSONDocument(
          "totalAverage" -> BSONDocument(
            "type" -> "video_interview",
            "scoreText" -> "48%",
            "scoreValue" -> (baseTotalOverall + score)
          ),
          "reviewers" -> BSONDocument(
            "reviewer1" -> reviewerBSONDoc(score)
          )
        )
      )
    )

    def oneReviewedTwoReviewerReviewedBSONDoc(score1: Double, score2: Double) = BSONArray(
      BSONDocument(
        "received" -> BSONDateTime(2),
        "candidateId" -> "cnd_f9e67cbb858aed29b3884ff4a10d77e2",
        "customCandidateId" -> "FSCND-f182f623-80ba-49f4-bed2-77c3c93296d2",
        "interviewId" -> 46,
        "customInviteId" -> "FSINV-5071afd4-7f61-42e9-9e72-f621dcaec618",
        "deadline" -> "2016-11-21",
        "reviews" -> BSONDocument(
          "totalAverage" -> BSONDocument(
            "type" -> "video_interview",
            "scoreText" -> "48%",
            "scoreValue" -> (baseTotalOverall + score2)
          ),
          "reviewers" -> BSONDocument(
            "reviewer1" -> reviewerBSONDoc(score1),
            "reviewer2" -> reviewerBSONDoc(score2)
          )
        )
      )
    )

    //scalastyle:off method.length
    def threeReviewedTwoReviewerReviewedBSONDoc(
      score1: Double,
      score2: Double,
      score3: Double,
      score4: Double,
      score5: Double,
      score6: Double
    ) = BSONArray(
      BSONDocument(
        "received" -> BSONDateTime(2),
        "candidateId" -> "cnd_f9e67cbb858aed29b3884ff4a10d77e2",
        "customCandidateId" -> "FSCND-f182f623-80ba-49f4-bed2-77c3c93296d2",
        "interviewId" -> 46,
        "customInviteId" -> "FSINV-5071afd4-7f61-42e9-9e72-f621dcaec618",
        "deadline" -> "2016-11-21",
        "reviews" -> BSONDocument(
          "totalAverage" -> BSONDocument(
            "type" -> "video_interview",
            "scoreText" -> "48%",
            "scoreValue" -> (baseTotalOverall + score2)
          ),
          "reviewers" -> BSONDocument(
            "reviewer1" -> reviewerBSONDoc(score1),
            "reviewer2" -> reviewerBSONDoc(score2)
          )
        )
      ),
      BSONDocument(
        "received" -> BSONDateTime(4),
        "candidateId" -> "cnd_f9e67cbb858aed29b3884ff4a10d77e2",
        "customCandidateId" -> "FSCND-f182f623-80ba-49f4-bed2-77c3c93296d2",
        "interviewId" -> 46,
        "customInviteId" -> "FSINV-5071afd4-7f61-42e9-9e72-f621dcaec618",
        "deadline" -> "2016-11-21",
        "reviews" -> BSONDocument(
          "totalAverage" -> BSONDocument(
            "type" -> "video_interview",
            "scoreText" -> "48%",
            "scoreValue" -> (baseTotalOverall + score2)
          ),
          "reviewers" -> BSONDocument(
            "reviewer1" -> reviewerBSONDoc(score3),
            "reviewer2" -> reviewerBSONDoc(score4)
          )
        )
      ),
      BSONDocument(
        "received" -> BSONDateTime(3),
        "candidateId" -> "cnd_f9e67cbb858aed29b3884ff4a10d77e2",
        "customCandidateId" -> "FSCND-f182f623-80ba-49f4-bed2-77c3c93296d2",
        "interviewId" -> 46,
        "customInviteId" -> "FSINV-5071afd4-7f61-42e9-9e72-f621dcaec618",
        "deadline" -> "2016-11-21",
        "reviews" -> BSONDocument(
          "totalAverage" -> BSONDocument(
            "type" -> "video_interview",
            "scoreText" -> "48%",
            "scoreValue" -> (baseTotalOverall + score2)
          ),
          "reviewers" -> BSONDocument(
            "reviewer1" -> reviewerBSONDoc(score5),
            "reviewer2" -> reviewerBSONDoc(score6)
          )
        )
      )
    )

    //scalastyle:off method.length

    def testsBSONDoc(reviewed: BSONArray) = BSONDocument("tests" ->
      BSONArray(BSONDocument(
        "interviewId" -> 46,
        "userForResults" -> true,
        "testProvider" -> "testProvider",
        "testUrl" -> "testUrl",
        "token" -> "token",
        "candidateId" -> "cnd_f9e67cbb858aed29b3884ff4a10d77e2",
        "customCandidateId" -> "FSCND-f182f623-80ba-49f4-bed2-77c3c93296d2",
        "invitationDate" -> BSONDateTime(1),
        "callbacks" ->
          BSONDocument("reviewed" -> reviewed)
      )))

    def oneReviewedOneReviewerReviewedPhase3BSONDoc(score1: Double) = BSONDocument("PHASE3" ->
      testsBSONDoc(oneReviewedOneReviewerReviewedBSONDoc(score1)))

    def oneReviewedTwoReviewerReviewedPhase3BSONDoc(score1: Double, score2: Double) = BSONDocument("PHASE3" ->
      testsBSONDoc(oneReviewedTwoReviewerReviewedBSONDoc(score1, score2)))

    def threeReviewedTwoReviewerReviewedPhase3BSONDoc(score1: Double, score2: Double, score3: Double,
      score4: Double, score5: Double, score6: Double) = BSONDocument("PHASE3" ->
      testsBSONDoc(threeReviewedTwoReviewerReviewedBSONDoc(score1, score2, score3, score4, score5, score6)))
  }

  trait CandidateProgressReportFixture {
  }

}
