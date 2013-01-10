package models

import anorm._
import anorm.SqlParser._
import helpers.DbTestContext
import java.sql.Connection
import org.specs2.mutable.Specification
import play.api.test.FakeApplication
import play.api.Play.{ start, stop }
import org.overviewproject.test.DbSetup._

class DocumentDataLoaderSpec extends Specification {

  step(start(FakeApplication()))
  
  "DocumentDataLoader" should {
    
    "Load document data for specified id" in new DbTestContext {
      val documentSetId = insertDocumentSet("DocumentDataLoaderSpec")
      val insertedDocumentId = insertDocument(documentSetId, "description", "documentCloudId", Some("title"))

      val documentDataLoader = new DocumentDataLoader()
      val document = documentDataLoader.loadDocument(insertedDocumentId)

      document must beSome
      document.get must be equalTo((insertedDocumentId, "description", Some("documentCloudId"), Some("title")))
    }
    
    "Returns None for non-existing id" in new DbTestContext {
      val documentDataLoader = new DocumentDataLoader()

      documentDataLoader.loadDocument(-1) must beNone
    }
  }
  
  step(stop)
}
