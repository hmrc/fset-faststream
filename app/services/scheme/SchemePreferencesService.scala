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

package services.scheme

import com.google.inject.ImplementedBy

import javax.inject.{Inject, Singleton}
import model.SelectedSchemes
import repositories.schemepreferences.SchemePreferencesRepository

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[SchemePreferencesServiceImpl])
trait SchemePreferencesService {
  def find(applicationId: String): Future[SelectedSchemes]
  def update(applicationId: String, selectedSchemes: SelectedSchemes): Future[Unit]
}

@Singleton
class SchemePreferencesServiceImpl @Inject() (spRepository: SchemePreferencesRepository)(
  implicit ec: ExecutionContext) extends SchemePreferencesService {
  override def find(applicationId: String): Future[SelectedSchemes] = spRepository.find(applicationId)

  override def update(applicationId: String, selectedSchemes: SelectedSchemes): Future[Unit] =
    spRepository.save(applicationId, selectedSchemes) map (_ => ())
}
