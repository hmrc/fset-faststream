/*
 * Copyright 2019 HM Revenue & Customs
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

package services.partnergraduateprogrammes

import model.exchange.{ AssistanceDetailsExchange, PartnerGraduateProgrammesExchange }
import model.persisted.{ AssistanceDetails, PartnerGraduateProgrammes }
import repositories._
import repositories.assistancedetails.AssistanceDetailsRepository
import repositories.partnergraduateprogrammes.PartnerGraduateProgrammesRepository
import services.AuditService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object PartnerGraduateProgrammesService extends PartnerGraduateProgrammesService {
  val pgpRepository = faststreamPartnerGraduateProgrammesRepository
}

trait PartnerGraduateProgrammesService {
  val pgpRepository: PartnerGraduateProgrammesRepository

  def update(applicationId: String, partnerGraduateProgrammesExchange: PartnerGraduateProgrammesExchange): Future[Unit] = {
    val partnerGraduateProgrammes = PartnerGraduateProgrammes(
      partnerGraduateProgrammesExchange.interested,
      partnerGraduateProgrammesExchange.partnerGraduateProgrammes
    )
    pgpRepository.update(applicationId, partnerGraduateProgrammes)
  }

  def find(applicationId: String): Future[PartnerGraduateProgrammesExchange] = {
    val partnerGraduateProgrammesFut = pgpRepository.find(applicationId)
    for {
      pgp <- partnerGraduateProgrammesFut
    } yield PartnerGraduateProgrammesExchange(pgp.interested, pgp.partnerGraduateProgrammes)
  }
}
