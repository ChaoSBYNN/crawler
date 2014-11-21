package com.zxsoft.crawler.api.impl;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.hadoop.conf.Configuration;

import com.zxsoft.crawler.api.ConfManager;
import com.zxsoft.crawler.api.ConfResource;
import com.zxsoft.crawler.util.CrawlerConfiguration;

public class RAMConfManager implements ConfManager {
  Map<String,Configuration> configs = new HashMap<String,Configuration>();
  
  public RAMConfManager() {
    configs.put(ConfResource.DEFAULT_CONF, CrawlerConfiguration.create());
  }
  
  public Set<String> list() {
    return configs.keySet();
  }
  
  public Configuration get(String confId) {
    return configs.get(confId);
  }
  
  public Map<String,String> getAsMap(String confId) {
    Configuration cfg = configs.get(confId);
    if (cfg == null) return null;
    Iterator<Entry<String,String>> it = cfg.iterator();
    TreeMap<String,String> res = new TreeMap<String,String>();
    while (it.hasNext()) {
      Entry<String,String> e = it.next();
      res.put(e.getKey(), e.getValue());
    }
    return res;
  }
  
  public void create(String confId, Map<String,String> props, boolean force) throws Exception {
    if (configs.containsKey(confId) && !force) {
      throw new Exception("Config name '" + confId + "' already exists.");
    }
    Configuration conf = CrawlerConfiguration.create();
    // apply overrides
    if (props != null) {
      for (Entry<String,String> e : props.entrySet()) {
        conf.set(e.getKey(), e.getValue());
      }
    }
    configs.put(confId, conf);
  }
  
  public void setProperty(String confId, String propName, String propValue) throws Exception {
    if (!configs.containsKey(confId)) {
      throw new Exception("Unknown configId '" + confId + "'");
    }
    Configuration conf = configs.get(confId);
    conf.set(propName, propValue);
  }
  
  public void delete(String confId) {
    configs.remove(confId);
  }
}
