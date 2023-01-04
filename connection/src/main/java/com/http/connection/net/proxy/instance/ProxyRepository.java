package com.http.connection.proxy.instance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpHost;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import com.epopcon.wspider.common.logger.Logger;
import com.epopcon.wspider.common.logger.WSLogger;
import com.epopcon.wspider.common.logger.WSLogger.WSTYPE;
import com.epopcon.wspider.context.WspiderContext;
import com.epopcon.wspider.db.dflt.DbHandler;
import com.epopcon.wspider.db.dflt.dto.ProxyServerDto;
import com.epopcon.wspider.db.dflt.dto.ProxyServerUsageDto;
import com.epopcon.wspider.net.proxy.holder.ProxyHolder;
import com.epopcon.wspider.net.proxy.holder.ProxyHolderMaker;

public class ProxyRepository implements InitializingBean {

	protected Logger logger = WSLogger.getLogger(WSTYPE.PROXY);

	public final static int QUEUE_TEST_WAITING_SERVER = 0;
	public final static int QUEUE_TEST_FAIL_SERVER = 1;

	@Autowired
	private DbHandler dbHandler;
	@Autowired(required = false)
	private WspiderContext context;
	@Autowired
	private ProxyHolderMaker holderMaker;

	private long serverId = -1;

	private Map<Long, ProxyHolder> holders = new ConcurrentHashMap<>();
	private Map<Long, ProxyServerDto> serverMap = new ConcurrentHashMap<>();
	private List<ProxyServerDto> servers = new CopyOnWriteArrayList<>();

	private LinkedBlockingQueue<Object> testWaitings = new LinkedBlockingQueue<>();
	private LinkedBlockingQueue<Proxy> testFails = new LinkedBlockingQueue<>();

	private Map<Long, Long> serviceMap = new ConcurrentHashMap<>();
	private Map<Long, Map<Long, ProxyServerUsageDto>> usagesByJob = new ConcurrentHashMap<>();
	private Set<String> domains = Collections.synchronizedSet(new HashSet<>());

	private List<String> groupNames = Collections.emptyList();
	private volatile boolean initialized = false;
	private volatile long lastUpdateTime = 0L;

	public void setGroupNames(List<String> groupNames) {
		this.groupNames = groupNames;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		initialize();
	}

	private void initialize() {
		initialized = false;
		if (context != null)
			serverId = context.getSystemId();

		Map<Long, ProxyHolder> holders = new ConcurrentHashMap<>();
		Map<Long, ProxyServerDto> serverMap = new ConcurrentHashMap<>();
		List<ProxyServerDto> servers = new CopyOnWriteArrayList<>();
		Map<Long, Map<Long, ProxyServerUsageDto>> usagesByJob = new ConcurrentHashMap<>();

		Map<Long, List<String>> accessMap = dbHandler.getProxyAccessMap();
		List<ProxyServerUsageDto> list = dbHandler.getProxyServerUsageList(serverId, groupNames);

		for (ProxyServerDto e : dbHandler.getProxyServerList(groupNames)) {
			long proxyServerId = e.getId();

			String ip = e.getIp();
			e.setProxyAccess(dbHandler.getProxyAccessList(proxyServerId));
			e.setHost(HttpHost.create(ip));
			e.setStatus(ProxyServerPool.STATUS_ACTIVE);

			servers.add(e);
			serverMap.put(proxyServerId, e);
		}

		for (ProxyServerUsageDto u : list) {

			long proxyServerId = u.getProxyServerId();
			long jobId = u.getJobId();

			Map<Long, ProxyServerUsageDto> map = usagesByJob.get(jobId);

			if (map == null) {
				map = new HashMap<>();
				usagesByJob.put(jobId, map);
			}

			if (accessMap.containsKey(jobId)) {
				for (String domain : accessMap.get(jobId))
					u.addDomain(domain);
			}
			u.setReqCnt(0L);
			u.setStatus(ProxyServerPool.STATUS_ACTIVE);
			u.setEntryYn(ProxyServerPool.ENTRY_NO);
			u.setProxyServerDto(serverMap.get(proxyServerId));

			map.put(proxyServerId, u);
		}

		for (Entry<Long, Map<Long, ProxyServerUsageDto>> entry : usagesByJob.entrySet()) {
			Long jobId = entry.getKey();
			List<ProxyServerUsageDto> values = new ArrayList<>(entry.getValue().values());

			ProxyHolder holder = holderMaker.make(jobId);
			holder.setProxyServerUsages(values);

			holders.put(jobId, holder);
		}

		this.testWaitings.clear();
		this.testFails.clear();

		this.holders = holders;
		this.serverMap = serverMap;
		this.servers = servers;
		this.usagesByJob = usagesByJob;

		for (ProxyServerDto e : servers)
			testWaitings.add(e);
		for (ProxyHolder holder : holders.values())
			holder.afterPropertiesSet();
		initialized = true;
		lastUpdateTime = System.currentTimeMillis();
	}

	public boolean reloadIfUpdated() {
		boolean changed = false;

		List<ProxyServerDto> list = dbHandler.getProxyServerList(groupNames);
		for (ProxyServerDto e : list) {
			long proxyServerId = e.getId();

			if (!serverMap.containsKey(proxyServerId)) {
				changed = true;
				break;
			} else {
				ProxyServerDto o = serverMap.get(proxyServerId);
				if (!e.compareTo(o)) {
					changed = true;
					break;
				}
			}
		}

		if (!changed && list.size() < getServerSize())
			changed = true;
		if (changed) {
			synchronized (this) {
				long elapsedTime = System.currentTimeMillis();

				initialize();
				logger.info(getClass().getSimpleName(),
						String.format("# Proxy information has been changed.. | elapsedTime : %s ms.", System.currentTimeMillis() - elapsedTime));
			}
		}
		return changed;
	}

	public long getLastUpdateTime() {
		return lastUpdateTime;
	}

	public ProxyServerDto getServer(Long proxyServerId) {
		return serverMap.get(proxyServerId);
	}

	public List<ProxyServerDto> getServers() {
		return servers;
	}

	public int getServerSize() {
		return servers.size();
	}

	public List<ProxyServerDto> getServers(Long jobId) {
		ProxyHolder p = holders.get(jobId);
		if (p == null)
			return servers;
		return p.getAvailableServers();
	}

	private ProxyServerUsageDto getServerUsage(long jobId, long proxyServerId) {
		Map<Long, ProxyServerUsageDto> map = usagesByJob.get(jobId);
		if (map != null)
			return map.get(proxyServerId);
		return null;
	}

	private synchronized void addServerUsage(long jobId, long proxyServerId, ProxyServerUsageDto dto) {
		Map<Long, ProxyServerUsageDto> map = usagesByJob.get(jobId);
		if (map == null) {
			map = new HashMap<>();
			usagesByJob.put(jobId, map);
		}
		map.put(proxyServerId, dto);
		addProxyHolder(jobId, dto);
	}

	private synchronized void addProxyHolder(long jobId, ProxyServerUsageDto dto) {
		ProxyHolder holder = holders.get(jobId);
		if (holder == null) {
			holder = holderMaker.make(jobId);

			holder.addProxyServerUsage(dto);
			holder.afterPropertiesSet();
			holders.put(jobId, holder);
		} else {
			holder.addProxyServerUsage(dto);
		}
	}

	public List<ProxyServerUsageDto> getProxyServerUsages() {
		List<ProxyServerUsageDto> list = new ArrayList<>();
		synchronized (usagesByJob) {
			for (Entry<Long, Map<Long, ProxyServerUsageDto>> entry : usagesByJob.entrySet()) {
				Long jobId = entry.getKey();
				if (serviceMap.containsKey(jobId)) {
					Map<Long, ProxyServerUsageDto> e = entry.getValue();
					list.addAll(e.values());
				}
			}
		}
		return list;
	}

	public Map<Long, List<ProxyServerUsageDto>> getProxyServerUsagesByService() {
		Map<Long, List<ProxyServerUsageDto>> map = new HashMap<>();

		for (ProxyServerUsageDto e : getProxyServerUsages()) {
			Long serviceId = e.getServiceId();
			List<ProxyServerUsageDto> list = map.get(serviceId);

			if (list == null) {
				list = new ArrayList<>();
				map.put(serviceId, list);
			}
			list.add(e);
		}
		return map;
	}

	public List<ProxyServerUsageDto> getTestWaitingServers(long proxyServerId) {
		List<ProxyServerUsageDto> list = new ArrayList<>();
		for (ProxyServerUsageDto e : getProxyServerUsages()) {
			if (proxyServerId == e.getProxyServerId()) {
				if (e.isWaitingTest())
					list.add(e);
			}
		}
		return list;
	}

	public Map<Long, Short> getProxyServerUsageStatus() {
		List<Long> serviceIds = new ArrayList<>(serviceMap.values());
		if (serviceIds.isEmpty())
			return Collections.emptyMap();
		return dbHandler.getProxyServerUsageStatus(serverId, groupNames, serviceIds);
	}

	public Map<Long, Integer> getAvailableProxyServerCount() {
		return dbHandler.getAvailableProxyServerCount(serverId, groupNames);
	}

	public synchronized ProxyServerUsageDto getProxyServerUsage(long jobId, ProxyServerDto dto) {
		long proxyServerId = dto.getId();
		ProxyServerUsageDto usage = getServerUsage(jobId, proxyServerId);

		if (usage == null) {
			Long serviceId = serviceMap.get(jobId);
			if (serviceId == null)
				serviceId = dbHandler.getServiceId(jobId);

			usage = new ProxyServerUsageDto();
			usage.setServiceId(serviceId);
			usage.setJobId(jobId);
			usage.setServerId(serverId);
			usage.setProxyServerId(proxyServerId);
			usage.setReqCnt(getAverageReqCnt(jobId));
			usage.setCumulativeReqCnt(0L);
			usage.setStatus(ProxyServerPool.STATUS_ACTIVE);
			usage.setEntryYn(ProxyServerPool.ENTRY_NO);
			usage.setProxyServerDto(dto);
			usage.setModified();

			dbHandler.insertProxyServerUsage(usage);
			addServerUsage(jobId, proxyServerId, usage);
		}
		return usage;
	}

	private long getAverageReqCnt(long jobId) {
		long reqCnt = 0L;
		ProxyHolder p = holders.get(jobId);

		if (p != null) {
			int i = 0;
			for (ProxyServerUsageDto e : p.getProxyServerUsages()) {
				reqCnt += e.getReqCnt();
				i++;
			}
			if (i > 0)
				reqCnt = reqCnt / i;
		}
		return reqCnt;
	}

	public boolean addServiceIfAbsent(Long jobId) {
		if (jobId.longValue() > 0 && !serviceMap.containsKey(jobId)) {
			Long serviceId = dbHandler.getServiceId(jobId);
			serviceMap.put(jobId, serviceId);
			return true;
		}
		return false;
	}

	public synchronized boolean addDomainIfAbsent(String domain) {
		if (!domains.contains(domain)) {
			domains.add(domain);
			return true;
		}
		return false;
	}

	public List<String> getAllDomain() {
		return new ArrayList<>(domains);
	}

	public boolean contains(int type, Object e) {
		if (initialized) {
			switch (type) {
			case QUEUE_TEST_WAITING_SERVER:
				return testWaitings.contains(e);
			case QUEUE_TEST_FAIL_SERVER:
				return testFails.contains(e);
			}
		}
		return false;
	}

	public boolean add(int type, Object e) {
		if (initialized) {
			switch (type) {
			case QUEUE_TEST_WAITING_SERVER:
				return testWaitings.add(e);
			case QUEUE_TEST_FAIL_SERVER:
				return testFails.add((Proxy) e);
			}
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	public <T> T poll(int type) {
		Object o = null;
		if (initialized) {
			switch (type) {
			case QUEUE_TEST_WAITING_SERVER:
				o = testWaitings.poll();
				break;
			case QUEUE_TEST_FAIL_SERVER:
				o = testFails.poll();
				break;
			}
		}
		return (T) o;
	}

	@SuppressWarnings("unchecked")
	public <T> T poll(int type, long timeout, TimeUnit timeUnit) throws InterruptedException {
		Object o = null;
		if (initialized) {
			switch (type) {
			case QUEUE_TEST_WAITING_SERVER:
				o = testWaitings.poll(timeout, timeUnit);
				break;
			case QUEUE_TEST_FAIL_SERVER:
				o = testFails.poll(timeout, timeUnit);
				break;
			}
		}
		return (T) o;
	}

	public boolean isEmpty(int type) {
		if (initialized) {
			switch (type) {
			case QUEUE_TEST_WAITING_SERVER:
				return testWaitings.isEmpty();
			case QUEUE_TEST_FAIL_SERVER:
				return testFails.isEmpty();
			}
		}
		return true;
	}

	public Object[] toArray(int type) {
		if (initialized) {
			switch (type) {
			case QUEUE_TEST_WAITING_SERVER:
				return testWaitings.toArray();
			case QUEUE_TEST_FAIL_SERVER:
				return testFails.toArray();
			}
		}
		return new Object[0];
	}

	public int size(int type) {
		if (initialized) {
			switch (type) {
			case QUEUE_TEST_WAITING_SERVER:
				return testWaitings.size();
			case QUEUE_TEST_FAIL_SERVER:
				return testFails.size();
			}
		}
		return 0;
	}
}
