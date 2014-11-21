package com.zxsoft.crawler.parse;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hadoop.conf.Configuration;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.thinkingcloud.framework.util.CollectionUtils;
import org.thinkingcloud.framework.util.StringUtils;

import com.zxsoft.crawler.dns.DNSCache;
import com.zxsoft.crawler.parse.FetchStatus.Status;
import com.zxsoft.crawler.protocol.ProtocolOutput;
import com.zxsoft.crawler.protocols.http.HttpFetcher;
import com.zxsoft.crawler.storage.ListConf;
import com.zxsoft.crawler.storage.RecordInfo;
import com.zxsoft.crawler.storage.WebPage;
import com.zxsoft.crawler.store.OutputException;
import com.zxsoft.crawler.util.Md5Signatrue;

/**
 * 解析全网搜索，与网络巡检不同的是不用进入详细页
 */
public final class NetworkSearchParserController extends ParseTool {

	private static Logger LOG = LoggerFactory.getLogger(NetworkSearchParserController.class);
	private AtomicInteger pageNum = new AtomicInteger(1);
	private AtomicInteger sum = new AtomicInteger(0);
	
	public FetchStatus parse(WebPage page) throws ParserNotFoundException, UnsupportedEncodingException, MalformedURLException {
		
		String keyword = page.getKeyword();
		String listUrl = page.getListUrl();
		
		ListConf listConf = confDao.getListConf(listUrl);
		if (listConf == null) {
			throw new NullPointerException("没有找到版块地址是" + page.getBaseUrl() + "的ConfList配置");
		}
		
		String indexUrl = String.format(listUrl, URLEncoder.encode(keyword, "UTF-8"));
		FetchStatus status = new FetchStatus(indexUrl, listConf.getComment() + keyword);
		
		String listDom = listConf.getListdom();
		if (StringUtils.isEmpty(listDom)) {
			LOG.error("列表DOM没有配置,无法获取列表信息:" + indexUrl);
			status.setStatus(Status.CONF_ERROR);
			status.setMessage("列表DOM没有配置,无法获取列表信息");
			return status;
		}
		
		if (listConf.isAuth()) { // need login
			
		}
		
		boolean ajax = listConf.isAjax();
		HttpFetcher httpFetcher = new HttpFetcher(/*conf*/);
		WebPage tempPage = page;
		tempPage.setBaseUrl(indexUrl);
		tempPage.setAjax(ajax);
		ProtocolOutput output = httpFetcher.fetch(tempPage);
		if (!output.getStatus().isSuccess()) {
			status.setStatus(Status.PROTOCOL_FAILURE);
			status.setMessage(output.getStatus().getMessage());
			return status;
		}
		Document document = output.getDocument();
		page.setDocument(document);
		
		LOG.debug("开始利用【" + listConf.getComment() + "】搜索:" + keyword);
		
		String urlDom = listConf.getUrldom();
		String synopsisDom = listConf.getSynopsisdom();
		String dateDom = listConf.getDatedom();
		
		String ip = "";
        try {
	        ip = DNSCache.getIp(new URL(indexUrl));
        } catch (UnknownHostException e1) {
	        e1.printStackTrace();
        }
		
		while (true) {
			Elements list = document.select(listDom);
			if (CollectionUtils.isEmpty(list)) {
				LOG.error("列表DOM设置错误,无法获取列表信息" + indexUrl);
				status.setStatus(Status.CONF_ERROR);
				status.setMessage("列表DOM设置错误,无法获取列表信息");
				return status;
			}
			Elements lines = list.first().select(listConf.getLinedom());

			LOG.debug("【" + listConf.getComment() + "】第" + pageNum.get() + " 页, 数量: " + lines.size());
			
			List<RecordInfo> infos = new LinkedList<RecordInfo>();
			
			for (Element line : lines) {

				if (CollectionUtils.isEmpty(line.select(listConf.getUrldom()))
				        || StringUtils.isEmpty(line.select(listConf.getUrldom()).first().absUrl("href")))
					continue;

				sum.incrementAndGet();
				
				/** 链接地址 */
				String curl = line.select(urlDom).first().absUrl("href");
				/** 标题 */
				String title = line.select(urlDom).first().text();
				/** 简介 */
				String synopsis = "";
				Elements synEles = line.select(synopsisDom);
				if (!CollectionUtils.isEmpty(synEles)) {
					synopsis = synEles.first().text();
				}
				/** 日期 */
				long date = 0L;
				/*if (!StringUtils.isEmpty(dateDom) && !CollectionUtils.isEmpty(line.select(listConf.getDatedom()))) {
					String str = line.select(listConf.getDatedom()).first().text();
					try {
						
	                    date = Utils.formatDate(str).getTime();
                    } catch (ParseException e) {
	                    e.printStackTrace();
                    }
				}*/
				
				RecordInfo info = new RecordInfo(title, curl, System.currentTimeMillis());
				info.setId(Md5Signatrue.generateMd5(curl));
				info.setIp(ip);
				info.setTimestamp(date);
				LOG.info(title);
				infos.add(info);
			}
			
			try {
	            indexWriter.write(infos);
	            status.setStatus(FetchStatus.Status.SUCCESS);
            } catch (OutputException e) {
            	status.setStatus(FetchStatus.Status.OUTPUT_FAILURE);
	            LOG.error("无法写数据出去" + e.getMessage(), e);
            }
			
			tempPage.setDocument(document);
			tempPage.setBaseUrl(document.location());
			
			 // 翻页
			ProtocolOutput ptemp = fetchNextPage(pageNum.get(), tempPage);
			if (ptemp ==null || !ptemp.getStatus().isSuccess()) {
				LOG.debug("No next page, exit.");
				break;
			}
			document = ptemp.getDocument();
			if (document == null) {
				LOG.debug("document == null, break");
				break;
			}

			pageNum.incrementAndGet();
			
		}
		LOG.debug("【" + listConf.getComment() + "】抓取结束, 共抓取数据数量:" + sum.get());
		
		status.setCount(sum.get());
		return status;
	}
}
