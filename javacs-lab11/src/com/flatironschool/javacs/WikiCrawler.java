package com.flatironschool.javacs;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import redis.clients.jedis.Jedis;


public class WikiCrawler {
	// keeps track of where we started
	private final String source;
	
	// the index where the results go
	private JedisIndex index;
	
	// queue of URLs to be indexed
	private Queue<String> queue = new LinkedList<String>();
	
	// fetcher used to get pages from Wikipedia
	final static WikiFetcher wf = new WikiFetcher();

	/**
	 * Constructor.
	 * 
	 * @param source
	 * @param index
	 */
	public WikiCrawler(String source, JedisIndex index) {
		this.source = source;
		this.index = index;
		queue.offer(source);
	}

	/**
	 * Returns the number of URLs in the queue.
	 * 
	 * @return
	 */
	public int queueSize() {
		return queue.size();	
	}

	/**
	 * Gets a URL from the queue and indexes it.
	 * @param b 
	 * 
	 * @return Number of pages indexed.
	 * @throws IOException
	 */
	/* Notes for method:
	when testing is true:
		choose and remove URL from queue in FIFO order
		read contents of page using WikiFetcher.readWikipedia, which is included for testing purposes
		index pages regardless of whether they're already indexed
		find all internal links on page and add the to queue
		return URL of page it indexed
	when testing is false:
		choose and remove URl from queue in FIFO order
		if URL is already indexed, it doesn't index it again and returns null
		otherwise, it reads contents of page using WikiFetcher.readWikipedia,
		and then indexes the page and returns the URL

	*/
	public String crawl(boolean testing) throws IOException {
        if (queue.isEmpty()) {
        	return null;
        }

        String url = queue.poll();

        if(testing) {
        	Elements wikiDatas = wf.readWikipedia(url);
        	index.indexPage(url, wikiDatas);
        	queueInternalLinks(wikiDatas);
        	System.out.println(url);
        	return url;
        } else if (index.isIndexed(url) && !testing) {
    		return null;
        } else {
    		Elements wikiDatas = wf.fetchWikipedia(url);
    		index.indexPage(url, wikiDatas);
    		queueInternalLinks(wikiDatas);
    		return url;
        }
	}
	
	/**
	 * Parses paragraphs and adds internal links to the queue.
	 * 
	 * @param paragraphs
	 */
	// NOTE: absence of access level modifier means package-level
	void queueInternalLinks(Elements paragraphs) {
        for (Element paragraph: paragraphs) {
        	Elements linkNodes = paragraph.getElementsByAttributeValueStarting("href", "/wiki/");

        	for (Element linkNode: linkNodes) {
				String wikipedia = "https://en.wikipedia.org";
				String innerHTML = linkNode.attr("href");
				String link = wikipedia + innerHTML;
				queue.offer(link);
			}
        }
	}

	public static void main(String[] args) throws IOException {
		
		// make a WikiCrawler
		Jedis jedis = JedisMaker.make();
		JedisIndex index = new JedisIndex(jedis); 
		String source = "https://en.wikipedia.org/wiki/Java_(programming_language)";
		WikiCrawler wc = new WikiCrawler(source, index);
		
		// for testing purposes, load up the queue
		Elements paragraphs = wf.fetchWikipedia(source);
		wc.queueInternalLinks(paragraphs);

		// loop until we index a new page
		String res;
		do {
			res = wc.crawl(false);

            // REMOVE THIS BREAK STATEMENT WHEN crawl() IS WORKING
            break;
		} while (res == null);
		
		Map<String, Integer> map = index.getCounts("the");
		for (Entry<String, Integer> entry: map.entrySet()) {
			System.out.println(entry);
		}
	}
}
