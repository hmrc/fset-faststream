/*
 * Copyright 2023 HM Revenue & Customs
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

import com.google.inject.ImplementedBy
import config.MicroserviceAppConfig
import model.Exceptions.SchemeNotFoundException
import model._
import model.exchange.SdipLocation
import net.jcazevedo.moultingyaml._
import play.api.Application
import resource._

import javax.inject.{Inject, Singleton}
import scala.io.Source

@ImplementedBy(classOf[LocationInMemoryRepository])
trait LocationRepository {

  def locations: Seq[SdipLocation]
  def isValidLocationId(locationId: LocationId): Boolean
}

@Singleton
class LocationInMemoryRepository @Inject() extends LocationRepository {

  override lazy val locations: Seq[SdipLocation] = SdipLocation.Locations

  override def isValidLocationId(locationId: LocationId): Boolean = locations.map(_.id).contains(locationId)
}
