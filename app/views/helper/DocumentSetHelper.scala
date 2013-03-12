package views.helper

import play.api.i18n.Lang
import models.OverviewDocumentSetCreationJob

object DocumentSetHelper {
  /**
   * @param jobDescriptionKey A key, like "clustering_level:4"
   * @return A translated string, like "Clustering (4)"
   */
  def jobDescriptionKeyToMessage(jobDescriptionKey: String)(implicit lang: Lang): String = {
    val keyAndArgs : Seq[String] = jobDescriptionKey.split(':')
    val key = keyAndArgs.head
    if (key == "") {
      ""
    } else {
      val m = views.ScopedMessages("views.DocumentSetCreationJob._documentSetCreationJob.job_state_description")
      m(keyAndArgs.head, keyAndArgs.drop(1) : _*)
    }
  }

  /**
   * @param job A DocumentSetCreationJob
   * @return A translated string, like "Clustering (4)"
   */
  def jobDescriptionMessage(job: OverviewDocumentSetCreationJob)(implicit lang: Lang): String = {
    val n = job.jobsAheadInQueue

    if (n > 0) {
      views.Magic.t("views.DocumentSetCreationJob._documentSetCreationJob.jobs_to_process", n)
    } else {
      jobDescriptionKeyToMessage(job.stateDescription)
    }
  }
}
