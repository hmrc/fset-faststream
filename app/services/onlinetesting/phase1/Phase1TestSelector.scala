package services.onlinetesting.phase1

import config.CubiksGatewayConfig
import model.OnlineTestCommands.Phase1Test

trait Phase1TestSelector {
  val gatewayConfig: CubiksGatewayConfig

  def findFirstSjqTest(tests: List[Phase1Test]): Option[Phase1Test] = tests find (_.scheduleId == sjq)

  def findFirstBqTest(tests: List[Phase1Test]): Option[Phase1Test] = tests find (_.scheduleId == bq)

  private def sjq = gatewayConfig.phase1Tests.scheduleIds("sjq")

  private def bq = gatewayConfig.phase1Tests.scheduleIds("bq")

}
