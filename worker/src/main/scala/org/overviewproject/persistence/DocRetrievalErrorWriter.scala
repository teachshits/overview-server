package org.overviewproject.persistence

import org.overviewproject.http.DocRetrievalError
import org.overviewproject.persistence.orm.Schema
import org.overviewproject.tree.orm.DocumentProcessingError


object DocRetrievalErrorWriter {
  def write(documentSetId: Long, errors: Seq[DocRetrievalError]) {
    val documentProcessingErrors = 
      errors.map(e => DocumentProcessingError(documentSetId, e.documentUrl, e.message, e.statusCode, e.headers))
    
    Schema.documentProcessingErrors.insert(documentProcessingErrors)
  }
}