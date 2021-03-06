package org.overviewproject.util

import scala.collection.JavaConverters._
import scala.concurrent.{Future, Promise}

import org.elasticsearch.action.bulk.{ BulkProcessor, BulkRequest, BulkResponse }
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.client.Client
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.cluster.metadata.AliasAction
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.common.transport.InetSocketTransportAddress
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder
import org.elasticsearch.index.query.FilterBuilders
import org.elasticsearch.node.Node
import org.elasticsearch.node.NodeBuilder.nodeBuilder

trait DocumentSetIndexingSession {
  def indexDocument(documentSetId: Long, id: Long, text: String, title: Option[String], suppliedId: Option[String]): Unit
  def complete: Unit

  def requestsComplete: Future[Unit]
}

object SearchIndex {

  private val IndexName = Configuration.searchIndex.getString("index_name")
  private val IndexAlias = "documents"
  private def DocumentSetAlias(id: Long) = s"documents_$id"
  private val DocumentTypeName = "document"
  private val LongType = "integer"
  private val StringType = "string"

  private val DocumentSetIdField = "document_set_id"
  private val IdField = "id"
  private val TextField = "text"
  private val SuppliedIdField = "supplied_id"
  private val TitleField = "title"
  private val ConfigFile = Configuration.searchIndex.getString("config_file")
  private val SearchIndexHost = Configuration.searchIndex.getString("host")
  private val SearchIndexPort = Configuration.searchIndex.getInt("port")

  private val client: TransportClient = createTransportClient
  
  private def admin = client.admin.indices

  def createIndexIfNotExisting = {
    
    if (!indexExists) {
      createIndex
      createMapping
    }
    if (!aliasExists) createAlias
  }

  def startDocumentSetIndexingSession(documentSetId: Long): DocumentSetIndexingSession = {
    createDocumentSetAlias(documentSetId)

    new DocumentSetIndexingSessionImpl(client, documentSetId)
  }

  def createDocumentSetAlias(documentSetId: Long): Unit = {
    val filter = FilterBuilders.termFilter(DocumentSetIdField, documentSetId)
    val alias = new AliasAction(AliasAction.Type.ADD, IndexName, DocumentSetAlias(documentSetId), filter.toString).routing(s"$documentSetId")
    admin.prepareAliases().addAliasAction(alias).execute.actionGet
  }

  def indexDocument(documentSetId: Long, id: Long, text: String, title: Option[String], suppliedId: Option[String]): Unit = {
    val indexRequest = client.prepareIndex(DocumentSetAlias(documentSetId), "document")

    val source = addFields(jsonBuilder.startObject, Seq(
      (DocumentSetIdField, documentSetId),
      (IdField, id),
      (TextField, text),
      (TitleField, title),
      (SuppliedIdField, suppliedId))).endObject

    val r = indexRequest.setSource(source).execute.actionGet()
  }

  def shutdown: Unit = {
    client.close
  }

  private def createTransportClient: TransportClient = {
    val settings =  ImmutableSettings.settingsBuilder.loadFromClasspath(ConfigFile)
    new TransportClient(settings)
    
    Logger.info(s"Connecting to Search Index [${settings.get("cluster.name")}] at $SearchIndexHost:$SearchIndexPort")
    
    val transportClient =  new TransportClient(settings)
    transportClient.addTransportAddress(new InetSocketTransportAddress(SearchIndexHost, SearchIndexPort))
  }  

  
  private def indexExists: Boolean = {
    val r = admin.prepareExists(IndexName).execute.actionGet()

    r.isExists
  }

  private def createIndex: Unit = admin.prepareCreate(IndexName).execute.actionGet

  private def aliasExists: Boolean = {
    val r = admin.prepareExistsAliases(IndexAlias).execute.actionGet
    r.isExists
  }

  private def createAlias: Unit = admin.prepareAliases.addAlias(IndexName, IndexAlias).execute.actionGet

  private def addFields(builder: XContentBuilder, fields: Seq[(String, Any)]): XContentBuilder = {
    fields match {
      case Nil => builder
      case (f, Some(v)) :: tail => addFields(builder.field(f, v), tail)
      case (f, None) :: tail => addFields(builder, tail)
      case (f, v) :: tail => addFields(builder.field(f, v), tail)
    }
  }

  private def createMapping: Unit = {
    def addField(builder: XContentBuilder, fieldInfo: (String, String)): XContentBuilder =
      builder.startObject(fieldInfo._1)
        .field("type", fieldInfo._2)
        .endObject
        
    def fields = Seq(
      (DocumentSetIdField, LongType),
      (IdField, StringType),
      (TextField, StringType),
      (SuppliedIdField, StringType),
      (TitleField, StringType)
    ) 
    
    
    val mapping = fields.foldLeft(jsonBuilder.startObject
        .startObject(DocumentTypeName)
          .startObject("properties"))(addField)
          .endObject
        .endObject

    admin.preparePutMapping(IndexName)
      .setType(DocumentTypeName)
      .setSource(mapping)
      .execute.actionGet
  }

  private class DocumentSetIndexingSessionImpl(client: Client, documentSetId: Long) extends DocumentSetIndexingSession with BulkProcessor.Listener {
    private val bulkProcessor = new BulkProcessor.Builder(client, this).build
    private val allRequestsComplete = Promise[Unit]
    
    // Use mutable variables and locking to determine when all requests have been handled.
    private var requestInProgress: Boolean = false
    private var sessionComplete: Boolean = false
    
    override def indexDocument(documentSetId: Long, id: Long, text: String, title: Option[String], suppliedId: Option[String]): Unit = {
      val indexRequest = client.prepareIndex(DocumentSetAlias(documentSetId), "document")

      val source = addFields(jsonBuilder.startObject, Seq(
        (DocumentSetIdField, documentSetId),
        (IdField, id),
        (TextField, text),
        (TitleField, title),
        (SuppliedIdField, suppliedId))).endObject

      val request: IndexRequest = indexRequest.setSource(source).request

      bulkProcessor.add(request)
    }

    override def complete: Unit = {
      bulkProcessor.close
      synchronized {
        sessionComplete = true
        if (!requestInProgress) allRequestsComplete.success()
      }
    }

    override def requestsComplete: Future[Unit] = allRequestsComplete.future

    override def beforeBulk(executionId: Long, request: BulkRequest): Unit = {
      Logger.debug("Starting bulk indexing request")
      synchronized { requestInProgress = true }
    }

    override def afterBulk(executionId: Long, request: BulkRequest, response: BulkResponse) = {
      synchronized {
        requestInProgress = false
        if (sessionComplete) allRequestsComplete.success()
      }
      Logger.debug("Bulk indexing request complete")
    }

    override def afterBulk(executionId: Long, request: BulkRequest, failure: Throwable) = {
      allRequestsComplete.failure(failure)
    }

  }
}
