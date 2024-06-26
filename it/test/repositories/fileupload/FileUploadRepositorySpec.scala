/*
 * Copyright 2024 HM Revenue & Customs
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

package repositories.fileupload

import org.scalatest.Tag
import repositories.CollectionNames
import repositories.fileupload.FileUploadRepository.FileUploadNotFoundException
import testkit.MongoRepositorySpec

class FileUploadRepositorySpec extends MongoRepositorySpec {

  override val collectionName: String = CollectionNames.FILE_UPLOAD
  lazy val repository: FileUploadMongoRepository = new FileUploadMongoRepository(mongo)

  "add and retrieve" must {
    "store a file with contentType and fetch it" taggedAs TravisIgnore in {
      val testContent = "Test contents".toCharArray.map(_.toByte)
      val testContentType = "application/pdf"
      val fileId = repository.add(testContentType, testContent).futureValue
      val retrievedFile = repository.retrieve(fileId).futureValue
      retrievedFile.contentType mustBe testContentType
      retrievedFile.fileContents mustBe testContent
    }
  }

  "retrieve" must {
    "handle no matching document" in {
      repository.retrieve("missing-fileId").failed.futureValue mustBe a[FileUploadNotFoundException]
    }
  }

  "retrieveAllIdsAndSizes" must {
    "return an empty list when there is no data" in {
      repository.retrieveAllIdsAndSizes.futureValue mustBe Nil
    }

    "fetch the expected data" in {
      val testContent = "Test contents".toCharArray.map(_.toByte)
      val testContentType = "application/pdf"
      val fileId = repository.add(testContentType, testContent).futureValue

      val result = repository.retrieveAllIdsAndSizes.futureValue
      result.head.id mustBe fileId
      result.head.contentType mustBe testContentType
      result.head.length mustBe testContent.length
    }
  }

  "retrieveMetaData" must {
    "return no data when there is no match" in {
      repository.retrieveMetaData("missing-fileId").futureValue mustBe None
    }

    "fetch the expected data" in {
      val testContent = "Test contents".toCharArray.map(_.toByte)
      val testContentType = "application/pdf"
      val fileId = repository.add(testContentType, testContent).futureValue

      val result = repository.retrieveMetaData(fileId).futureValue
      result.head.id mustBe fileId
      result.head.contentType mustBe testContentType
      result.head.length mustBe testContent.length
    }
  }
}

// These tests fail on Travis but work locally and on jenkins. By tagging the tests we can
// explicitly skip them when travis runs
object TravisIgnore extends Tag("TravisIgnore")