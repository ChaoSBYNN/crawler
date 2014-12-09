package com.zxsoft.crawler.master.impl;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thinkingcloud.framework.util.Assert;
import org.thinkingcloud.framework.util.CollectionUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.zxsoft.crawler.api.JobCode;
import com.zxsoft.crawler.api.Machine;
import com.zxsoft.crawler.master.SlaveCache;
import com.zxsoft.crawler.master.SlaveManager;
import com.zxsoft.crawler.master.SlaveStatus;
import com.zxsoft.crawler.master.SlaveStatus.State;
import com.zxsoft.crawler.slave.SlavePath;

public class RAMSlaveManager implements SlaveManager {

	private static Logger LOG = LoggerFactory.getLogger(RAMSlaveManager.class);
	
	private class MyPoolExecutor extends ThreadPoolExecutor {
		public MyPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime,
		        TimeUnit unit, BlockingQueue<Runnable> workQueue) {
			super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
		}
	}

	public static Set<Machine> runningMachines = new HashSet<Machine>(100);
	
	static {
		List<Machine> list = SlaveCache.machines;
		if (CollectionUtils.isEmpty(list)) {
			throw new NullPointerException("No Slave machines found, please configure it.");
		}
		for (Machine machine : list) {
	        runningMachines.add(machine);
        }
	}
	
	private static SlaveScheduler scheduler = SlaveScheduler.getInstance();
	
	@Override
	@SuppressWarnings("fallthrough")
	public List<SlaveStatus> list() throws Exception {
		List<SlaveStatus> res = new ArrayList<SlaveStatus>();
		List<Machine> machines = SlaveCache.machines;
		ThreadPoolExecutor exec = new MyPoolExecutor(10, 100, 10, TimeUnit.SECONDS,
		        new ArrayBlockingQueue<Runnable>(100));
		List<Callable<SlaveStatus>> tasks = new ArrayList<Callable<SlaveStatus>>();
		for (Machine machine : machines) {
			Vistor vistor = new Vistor(machine);
			tasks.add(vistor);
		}
		List<Future<SlaveStatus>> futures = exec.invokeAll(tasks);
		for (Future<SlaveStatus> future : futures) {
			SlaveStatus status = future.get();
			if (status.state == State.STOP)  {
				status.score = 0.0f;
			} else {
				status.score = 1.0f / (1.0f + status.runningNum);
				ScoredMachine sm = new ScoredMachine(status.machine, status.runningNum, status.score);
				scheduler.addSlave(sm);
				LOG.info(status.machine.getId() + ":" + status.score);
			}
			res.add(status);
		}
		exec.shutdown();
		return res;
	}
	
	public void listRunning(String slaveId) {
		
	}
	
	class Vistor implements Callable<SlaveStatus> {
		private Machine machine;
		public Vistor(Machine machine) {
			this.machine = machine;
		}
		public SlaveStatus call() throws Exception {
			String url = "http://" + machine.getIp() + ":" + machine.getPort() + "/" + SlavePath.PATH + "/" + SlavePath.JOB_RESOURCE_PATH;
			SlaveStatus slaveStatus = null;
			com.sun.jersey.api.client.Client client = com.sun.jersey.api.client.Client.create();
			WebResource webResource = client.resource(url);
			try {
				String text = webResource.get(String.class);
				NumNum tm = new Gson().fromJson(text, NumNum.class);
				slaveStatus = new SlaveStatus(machine, tm.runningNum, tm.historyNum, 2000, "success", State.RUNNING);
			} catch(ClientHandlerException e) {
				slaveStatus = new SlaveStatus(machine, 0, 0, 4040, e.getMessage(), State.STOP);
			} finally {
				client.destroy();
			}
			return slaveStatus;
		}
	}

	class NumNum {
		int runningNum;
		int historyNum;
	}
	
	/**
	 *  choose a url to send job
	 */
	public String chooseUrl() {
		ScoredMachine sm = scheduler.selectSlave();
		Assert.notNull(sm, "没有Slave启动");
		
		URL url = null;
		try {
	        url = new URL("http", sm.machine.getIp(), sm.machine.getPort(), "/" + SlavePath.PATH + "/" + SlavePath.JOB_RESOURCE_PATH);
        } catch (MalformedURLException e) {
	        e.printStackTrace();
        }
		return url.toExternalForm();
	}
	
	@Override
	public String create(final Map<String, Object> args) throws Exception {
		int i = 0;
		while (i++ < SlaveCache.machines.size()) {
			String url = chooseUrl();
			com.sun.jersey.api.client.Client client = com.sun.jersey.api.client.Client.create();
			WebResource webResource = client.resource(url);
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String json = gson.toJson(args, Map.class);
			ClientResponse response = null;
			try {
				response = webResource.type("application/json").put(ClientResponse.class, json);
			} catch (ClientHandlerException e) {
				LOG.error(e.getMessage());
				LOG.error("URL为" + url + "的slave不能执行任务,他可能很忙,也可能挂了. 准备换一个slave试试...");
				list(); // 重新获取每个slave状态
				continue;
			} finally {
				if (response != null) {
					response.close();
				}
				if (client != null) {
					client.destroy();
				}
			}
			URL u = new URL(url);
			LOG.info("选中slave(" + u.getHost() + ":" + u.getPort() + ")执行任务");
			return new JobCode(22, "success choose slave", u.getHost() + ":" + u.getPort() ).toString();
		}
		LOG.error("Oh My God! 所有slave都罢工了, 都不能执行任务.");
		return new JobCode(55, "no slaves work").toString();
	}

	@Override
	public boolean abort(String slaveId, String crawlId, String id) throws Exception {
		// find running job
		// for (SlaveStatus job : jobRunning) {
		// if (job.id.equals(id)) {
		// job.state = State.KILLING;
		// boolean res = job.tool.killJob();
		// job.state = State.KILLED;
		// return res;
		// }
		// }
		return false;
	}

	@Override
	public boolean stop(String slaveId, String crawlId, String id) throws Exception {
		// find running job
		// for (SlaveStatus job : jobRunning) {
		// if (job.id.equals(id)) {
		// job.state = State.STOPPING;
		// boolean res = job.tool.stopJob();
		// return res;
		// }
		// }
		return false;
	}
	
	
}
