package controllers

import java.io.FileInputStream

import play.api.Play.{ start, stop }
import play.api.mvc.{ AnyContent, Request }
import play.api.test.{ FakeApplication, FakeRequest }
import play.api.test.Helpers._

import org.overviewproject.tree.orm.{Document,Tag}
import org.overviewproject.tree.orm.finders.FinderResult
import org.overviewproject.util.TempFile
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import controllers.auth.AuthorizedRequest
import models.OverviewUser
import models.export.Export

class DocumentSetExportControllerSpec extends Specification with Mockito {
  step(start(FakeApplication()))

  def makeFileInputStream(contents: Array[Byte]) : FileInputStream = {
    val tempFile = new TempFile
    tempFile.outputStream.write(contents)
    tempFile.outputStream.close
    tempFile.inputStream
  }

  trait BaseScope extends Scope {
    val mockStorage = mock[DocumentSetExportController.Storage]
    val mockExporters = mock[DocumentSetExportController.Exporters]

    val controller = new DocumentSetExportController {
      override val storage = mockStorage
      override val exporters = mockExporters
    }

    val user = mock[OverviewUser]
    def fakeRequest : Request[AnyContent] = FakeRequest()
    def request = new AuthorizedRequest(fakeRequest, user)
  }

  trait DocumentsWithStringTagsScope extends BaseScope {
    val documentSetId = 1L
    val finderResult = mock[FinderResult[(Document,Option[String])]]
    mockStorage.loadDocumentsWithStringTags(any[Long]) returns finderResult

    val export = mock[Export]
    val contents = "id,name\n1,foo".getBytes
    export.contentTypeHeader returns "text/csv; charset=\"utf-8\""
    export.exportToInputStream returns makeFileInputStream(contents)
    mockExporters.documentsWithStringTags(any[FinderResult[(Document,Option[String])]]) returns export

    lazy val result = controller.documentsWithStringTags(documentSetId)(request)
  }

  trait DocumentsWithColumnTagsScope extends BaseScope {
    val documentSetId = 1L
    val finderResult = mock[FinderResult[(Document,Option[String])]]
    val tagFinderResult = mock[FinderResult[Tag]]
    mockStorage.loadDocumentsWithTagIds(any[Long]) returns finderResult
    mockStorage.loadTags(any[Long]) returns tagFinderResult

    val export = mock[Export]
    val contents = "id,name\n1,foo".getBytes
    export.contentTypeHeader returns "text/csv; charset=\"utf-8\""
    export.exportToInputStream returns makeFileInputStream(contents)
    mockExporters.documentsWithColumnTags(any[FinderResult[(Document,Option[String])]], any[FinderResult[Tag]]) returns export

    lazy val result = controller.documentsWithColumnTags(documentSetId)(request)
  }

  "DocumentSetExportController" should {
    "set Content-Type header in documentsWithStringTags" in new DocumentsWithStringTagsScope {
      header(CONTENT_TYPE, result) must beSome(export.contentTypeHeader)
    }

    "set Content-Length header in documentsWithStringTags" in new DocumentsWithStringTagsScope {
      header(CONTENT_LENGTH, result) must beSome(contents.size.toString)
    }

    "set contents of documentsWithStringTags" in new DocumentsWithStringTagsScope {
      contentAsBytes(result) must beEqualTo(contents)
    }

    "not send as chunked in documentsWithStringTags" in new DocumentsWithStringTagsScope {
      header(TRANSFER_ENCODING, result) must beNone
    }

    "set Content-Type header in documentsWithColumnTags" in new DocumentsWithColumnTagsScope {
      header(CONTENT_TYPE, result) must beSome(export.contentTypeHeader)
    }

    "set Content-Length header in documentsWithColumnTags" in new DocumentsWithColumnTagsScope {
      header(CONTENT_LENGTH, result) must beSome(contents.size.toString)
    }

    "set contents of documentsWithColumnTags" in new DocumentsWithColumnTagsScope {
      contentAsBytes(result) must beEqualTo(contents)
    }

    "not send as chunked in documentsWithColumnTags" in new DocumentsWithColumnTagsScope {
      header(TRANSFER_ENCODING, result) must beNone
    }
  }

  step(stop)
}
