package com.nidkil.scraper

import org.slf4j.LoggerFactory

import com.typesafe.scalalogging.Logger

import com.nidkil.kvk.Filter
import com.nidkil.kvk.PageProcessor
import com.nidkil.kvk.Pager
import com.nidkil.kvk.Search
import com.nidkil.kvk.SearchUrl
import com.nidkil.kvk.Version
import com.nidkil.kvk.model.Organisatie
import com.nidkil.kvk.model.ProcessingStats
import com.nidkil.kvk.model.SearchResults
import com.nidkil.kvk.model.SearchStats
import com.nidkil.kvk.model.Stats
import com.nidkil.util.Timer

class Controller(filter: Filter) {

  private val logger = Logger(LoggerFactory.getLogger(classOf[Controller]))

  private lazy val searchUrl = new SearchUrl(filter, 1)
  private lazy val search = new Search(searchUrl)

  def getNumResults() = search.getNumResults()
  def getNumPages() = search.getNumPages()

  def run(): Option[SearchResults] = {
    val timer = new Timer()
    
    timer.start()
    
    search.execute()

    logger.debug(s"KvK web scrapper initialized [results=$getNumResults(), pages=$getNumPages()]")

    if (search.hasResults()) {
      logger.debug("KvK web scrapper loading results [startpage=%s, maxpages=%s]".format(filter.startpage, filter.maxpages))

      val pager = new Pager(filter, search.getNumPages())
      var resultList: List[Organisatie] = List()
      
      // TODO Make multi threaded
      while (pager.hasMorePages()) {
        val pageProcessor = new PageProcessor(pager.nextPage().get)

        //TODO Gracefully handle None
        //TODO When processing multiple pages and one fails does everything fail?
        pageProcessor.process() match {
          case Some(x) => {
            resultList = resultList ::: x
          }
          case None => println("Loading page failed or did not return results [%s]".format(searchUrl.getUrl()))
        }
      }

      timer.stop()
      
      //TODO What happens if processing fails? Handle error correctly
      val searchStats = new SearchStats(search.getNumResults(), search.getNumPages())
      val processingStats = new ProcessingStats(filter.startpage, filter.maxpages, resultList.size)
      val stats = new Stats(timer.execTime(false), searchStats, processingStats)
      
      Some(new SearchResults(Version.api, Version.release, stats, Some(resultList)))
    } else {
      None
    }
  }
}