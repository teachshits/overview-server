/*
 * DocumentVectorGenerator.scala
 * Takes lists of terms for each document (post-lexing), generates weighted and normalized vectors for a document set
 * Currently just simple TF-IDF
 * 
 * Overview Project
 * 
 * Created by Jonathan Stray, July 2012
 * 
 */

package clustering

import scala.collection.mutable.Map
import ClusterTypes._

// Basic use: call addDocument(docID, terms) until done, then getVectors() once
class DocumentVectorGenerator {


  // --- Data ---
  private var numDocs = 0 
  private var doccount = DocumentVector() 
  private var tf = DocumentSetVectors()
   
  // --- Methods ---
  
  // Add one document. Takes a list of terms, which are pre-lexed strings. Order of terms and docs does not matter.
  def addDocument(docId:DocumentID, terms:Seq[String]) = {
     
    if (terms.size > 0) {
        
      // count how many times each token appears in this doc (term frequency)      
      var termcounts = DocumentVector()
      for (t <- terms) {
        val prev_count = termcounts.getOrElse(t,0f)
        termcounts += (t -> (prev_count + 1))
      }
      
      // divide out document length to go from term count to term frequency
      termcounts.transform((key,count) => count/terms.size.toFloat) 
      tf += (docId -> termcounts)
       
      // for each unique term in this doc, update how many docs each term appears in (doc count)
      for (t <- termcounts.keys) {
        val prev_count = doccount.getOrElse(t, 0f)
        doccount += (t -> (prev_count + 1))
      }
      
      numDocs += 1
    }
  }   
  
  // Return inverse document frequency map: term -> idf
  // Drop all terms where count < 3 or count == N, and take the log of document frequency in the usual IDF way
  def Idf() : DocumentVector = {
    var idf2 = doccount.filter(kv => (kv._2 > 2) && (kv._2 < numDocs))
    idf2.transform((term,count) => math.log10(numDocs / count).toFloat)    
  }
  
  // After all documents have been added (or, really, at any point) compute the total set of document vectors
  // Previously added documents will end up with different vectors as new docs are added, due to IDF term
  def getVectors() : DocumentSetVectors = {
   
    var docVectors = DocumentSetVectors()
    val idf = Idf()
    
    // run over the stored TF values one more time, multiplying them by the IDF values for each term, and normalizing
    for ((docid, doctf) <- tf) {                // for each doc

      var tfidf = DocumentVector()
      var length = 0f
      
      for ((term,termfreq) <- doctf) {          // for each term in doc
        if (idf.contains(term)) {               // skip if term eliminated in previous step
          val weight = termfreq * idf(term)     // otherwise, multiply tf*idf
          tfidf += (term -> weight)
          length += weight*weight
        }
      }
      
      length = math.sqrt(length).toFloat
      tfidf.transform((term,weight) => weight/length)
     
      docVectors += (docid -> tfidf)
    }
    
    return docVectors    
  }
  
}
