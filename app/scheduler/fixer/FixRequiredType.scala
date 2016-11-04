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

package scheduler.fixer


sealed case class FixRequiredType(fixName: String, batchSize: Int)
sealed case class FixesRequired(fixes: Seq[FixRequiredType])

/**
  * This fix will take care of all those candidates who's progress status is PHASE2_TESTS_INVITED but the application
  * status is still PHASE1_TESTS (instead of PHASE2_TESTS). Basically the passed the phase 1 test, they've been invited
  * to phase 2 but they can't proceed as there is no button for phase 2 test on the dashboard.
  */
object PassToPhase2 extends FixRequiredType("Candidates who passed successfuly to phase 2 but with PHASE1_TEST Application Status", 1)



object ResetPhase1TestInvitedSubmitted extends FixRequiredType("Candidates who are SUBMITTED but PHASE1_TEST INVITED", 1)

/*
If a further fix is needed, add it to the list. If not needed remove it from the list and possibly
remove it's implementation.
 */
object RequiredFixes extends FixesRequired(PassToPhase2 :: ResetPhase1TestInvitedSubmitted :: Nil)
