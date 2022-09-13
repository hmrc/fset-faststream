/*
 * Copyright 2022 HM Revenue & Customs
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

trait BaseBSONReader {

  //TODO: mongo
  // the purpose of this is to accept a function that converts from a BsonDocument to a Case Class of type T
  // and execute that function returning the populated Case Class
/*
  protected def bsonReader[T](f: BsonDocument => T): BsonDocumentReader[T] = {
    new BsonDocumentReader[T] {
      def read(bson: BsonDocument) = f(bson)
    }
  }*/
/*
  protected def bsonReader[T](f: BsonDocument => T): BsonDocumentReader[T] = {
    new DocumentReader[T] {
      def read(bson: BsonDocument) = f(bson)
    }
  }*/

  protected def booleanTranslator(bool: Boolean) = if (bool) { "Yes" } else { "No" }
  protected def booleanYnTranslator(bool: Boolean) = if (bool) { "Y" } else { "N" }
}
