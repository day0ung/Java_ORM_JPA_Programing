package com.http.connection.net.proxy.instance;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;

import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpHost;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.util.ClassUtils;
import org.springframework.util.SystemPropertyUtils;

import com.epopcon.eums.exception.PException;
import com.epopcon.wspider.common.code.WSErrorCode;
import com.epopcon.wspider.common.logger.Logger;
import com.epopcon.wspider.common.logger.WSLogger;
import com.epopcon.wspider.common.logger.WSLogger.WSTYPE;
import com.epopcon.wspider.common.util.URLUtils;
import com.epopcon.wspider.db.dflt.DbHandler;
import com.epopcon.wspider.db.dflt.dto.ProxyAccessDto;
import com.epopcon.wspider.db.dflt.dto.ProxyServerDto;
import com.epopcon.wspider.db.dflt.dto.ProxyServerUsageDto;
import com.epopcon.wspider.lock.SharedLockPool;
import com.epopcon.wspider.process.MultiProcessUnitDecider;

public class ProxyServerPool implements InitializingBean {

	protected Logger logger = WSLogger.getLogger(WSTYPE.PROXY);
	protected Logger monitorLogger = WSLogger.getLogger(WSTYPE.PROXY_MONITOR);

	private final static String BASE_PACKAGE = "com.epopcon.wspider";

	private final static int TEST_ACCESS_INTERVAL = 10 * 60 * 1000; // 10분
	private final static int UPDATE_DB_INTERVAL = 5 * 1000; // 5초
	private final static int UPDATE_PROXY_INTERVAL = 30 * 1000; // 30초
	private final static int MONITOR_LOG_INTERVAL = 60 * 1000; // 1분

	private final static int TEST_WOKER_SIZE = 10;
	private final static int MAX_TEST_COUNT = 10;
	private final static int MAX_WAIT_TIME = 30 * 1000;

	private final static int RESULT_CONTINUE = 0;
	private final static int RESULT_FAIL = 1;
	private final static int RESULT_TESTING = 2;

	public final static short ACCESS_FAIL = 0;
	public final static short ACCESS_SUCCESS = 1;
	public final static short ACCESS_TEST_NEED = 2;
	public final static short ACCESS_TEST_WAITING = 3;

	public final static short STATUS_INACTIVE = 0;
	public final static short STATUS_ACTIVE = 1;
	public final static short ENTRY_NO = 0;
	public final static short ENTRY_YES = 1;

	private ThreadLocal<Long> serverKeys = new ThreadLocal<>();

	@Autowired
	private DbHandler dbHandler;
	@Autowired
	private ProxyServerValidator validator;
	@Autowired(required = false)
	private MultiProcessUnitDecider decider;
	@Autowired
	private SharedLockPool lockPool;
	@Autowired
	private ProxyRepository repo;
	@Autowired
	private ProxyConfig config;

	private Map<String, ProxyTester> testers;
	private MaintenanceWorker maintenanceWorker;
	private Timer jobScheduler = new Timer();

	private int testInterval = 30 * 1000;
	private int dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);

	public void setTestInterval(int testInterval) {
		this.testInterval = testInterval * 1000;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		initialize();
	}

	private void initialize() throws Exception {
		testers = getProxyTesters(BASE_PACKAGE);
		maintenanceWorker = new MaintenanceWorker();
		jobScheduler.schedule(maintenanceWorker, 50, 1000);
	}

	public Proxy get(URI uri, long jobId) {
		Proxy proxy = null;
		int totalCount = repo.getServerSize();
		int activeCount = 0;
		String domain = URLUtils.getProtocolAndDomain(uri);
		repo.addServiceIfAbsent(jobId);
		
		if (totalCount > 0) {
			List<ProxyServerUsageDto> list = null;
			List<ProxyServerDto> servers = repo.getServers(jobId);
			int lastServerIndex = getLastUsedServerIndex(jobId, servers);
			activeCount = servers.size();

			if (repo.addDomainIfAbsent(domain)) {
				for (ProxyDomain e : ProxyDomain.convert(servers, domain))
					repo.add(ProxyRepository.QUEUE_TEST_WAITING_SERVER, e);
			}

			AtomicInteger result = new AtomicInteger(0);

			for (int i = 0; i < activeCount; i++) {
				// 마지막 쓰여진 프록시의 경우에는 우선순위를 제일 마지막으로 처리
				if (lastServerIndex == i)
					continue;
				result.set(RESULT_CONTINUE);
				ProxyServerDto dto = servers.get(i);
				ProxyServerUsageDto usage = repo.getProxyServerUsage(jobId, dto);
				proxy = getProxy(uri, jobId, domain, dto, usage, result);

				if (result.get() > RESULT_CONTINUE)
					continue;
				// Proxy 사용건수가 특정건수를 넘을 경우 null로 반환
				if (proxy != null) {
					return proxy;
				} else {
					if (list == null)
						list = new ArrayList<>();
					list.add(usage);
				}
			}

			if (lastServerIndex > -1) {
				ProxyServerDto dto = servers.get(lastServerIndex);
				ProxyServerUsageDto usage = repo.getProxyServerUsage(jobId, dto);
				proxy = getProxy(uri, jobId, domain, dto, usage, result);
				if (proxy != null)
					return proxy;
			}

			// 동시 요청수가 많아 당장 가용한 프록시가 없다면 특정시간동안 대기
			if (list != null && !list.isEmpty()) {
				if (logger.isDebugEnabled())
					logger.debug(getClass().getSimpleName(),
							String.format("# Waiting for proxy information => jobId : %s, domain : %s (%s)", jobId, domain, list.size()));
				proxy = getProxyWait(jobId, list, uri);
				if (proxy != null)
					return proxy;
			}
		}

		logger.warn(getClass().getSimpleName(),
				String.format("# Proxy not available!! => jobId : %s, domain : %s (%s/%s)", jobId, domain, activeCount, totalCount));
		throw PException.buildException(WSErrorCode.PROXY_NOT_AVAILABLE,
				String.format("Proxy not available!! => totalCount : %s, activeCount : %s", totalCount, activeCount));
	}

	private Proxy getProxy(URI uri, long jobId, String domain, ProxyServerDto dto, ProxyServerUsageDto usage, AtomicInteger result) {
		Proxy proxy = null;
		Lock lock = getLock(domain, dto.getId());
		try {
			lock.lock();
			result.set(RESULT_CONTINUE);

			int a = canAccess(domain, usage);
			if (a == ACCESS_FAIL) {
				result.set(RESULT_FAIL);
			} else if (a == ACCESS_SUCCESS) {
				proxy = getProxy(jobId, usage, uri);
			} else if (a == ACCESS_TEST_NEED) {
				usage.setWaitingTest(true);
				boolean success = test(uri, dto);

				proxy = getProxy(jobId, usage, uri);
				setAccess(domain, dto, success);

				if (success || proxy == null)
					usage.setWaitingTest(false);
				if (!success) {
					addFailProxy(proxy);
					result.set(RESULT_TESTING);
				}
			} else if (a == ACCESS_TEST_WAITING) {

			}
		} finally {
			lock.unlock();
		}

		if (proxy != null)
			serverKeys.set(dto.getId());
		return proxy;
	}

	private int getLastUsedServerIndex(Long jobId, List<ProxyServerDto> servers) {
		if (config.canSkipLast(jobId)) {
			Long proxyServerId = serverKeys.get();
			if (proxyServerId != null) {
				for (int i = 0; i < servers.size(); i++) {
					ProxyServerDto dto = servers.get(i);
					if (proxyServerId.longValue() == dto.getId())
						return i;
				}
			}
		}
		return -1;
	}

	private short canAccess(String domain, ProxyServerUsageDto usage) {
		ProxyServerDto dto = usage.getProxyServerDto();
		boolean contains = false;

		if (dto.getStatus() == STATUS_INACTIVE || usage.getStatus() == STATUS_INACTIVE)
			return ACCESS_FAIL;
		else if (usage.isWaitingTest())
			return ACCESS_TEST_WAITING;

		for (ProxyAccessDto e : dto.getProxyAccess()) {
			String d = e.getDomain();
			short accessYn = e.getAccessYn();
			if (d.equals(domain)) {
				contains = true;

				if (accessYn == ACCESS_FAIL) {
					return ACCESS_FAIL;
				} else {
					long lastTestTime = e.getLastTestTime();
					if (System.currentTimeMillis() - lastTestTime > testInterval)
						return ACCESS_TEST_NEED;
				}
				break;
			}
		}

		if (!contains) {
			setAccess(domain, dto, false);
			return ACCESS_TEST_NEED;
		}
		return ACCESS_SUCCESS;
	}

	private void setAccess(String domain, ProxyServerDto dto, boolean access) {
		ProxyAccessDto pad = getProxyAccess(domain, dto);
		setAccess(pad, access);
	}

	private void setAccess(ProxyAccessDto pad, boolean access) {
		boolean changed = (pad.getAccessYn() == ACCESS_SUCCESS) != access;

		if (logger.isInfoEnabled()) {
			if (changed) {
				ProxyServerDto dto = repo.getServer(pad.getProxyServerId());

				if (access)
					logger.info(getClass().getSimpleName(),
							String.format("# Access(S) | domain - %s, proxy -> %s/%s", pad.getDomain(), dto.getId(), dto.getIp()));
				else
					logger.info(getClass().getSimpleName(),
							String.format("# Access(F) | domain - %s, proxy -> %s/%s", pad.getDomain(), dto.getId(), dto.getIp()));
			}
		}

		pad.setAccessYn(access ? ACCESS_SUCCESS : ACCESS_FAIL);
		pad.setLastTestTime(System.currentTimeMillis());
		dbHandler.mergeProxyAccess(pad);
	}

	private void setStatus(ProxyServerDto dto, boolean active) {
		boolean changed = (dto.getStatus() == STATUS_ACTIVE) != active;

		if (changed) {
			if ((dto.getStatus() == STATUS_ACTIVE) != active) {
				if (active)
					logger.info(getClass().getSimpleName(), String.format("# Status(A) | proxy -> %s/%s", dto.getId(), dto.getIp()));
				else
					logger.info(getClass().getSimpleName(), String.format("# Status(I) | proxy -> %s/%s", dto.getId(), dto.getIp()));
			}
		}

		dto.setStatus(active ? STATUS_ACTIVE : STATUS_INACTIVE);
		dbHandler.updateProxyServerStatus(dto);
	}

	private ProxyAccessDto getProxyAccess(String domain, ProxyServerDto dto) {
		ProxyAccessDto pad = null;

		synchronized (dto) {
			for (ProxyAccessDto e : dto.getProxyAccess()) {
				String d = e.getDomain();
				if (d.equals(domain)) {
					pad = e;
					break;
				}
			}

			if (pad == null) {
				long proxyServerId = dto.getId();

				pad = new ProxyAccessDto();
				pad.setDomain(domain);
				pad.setProxyServerId(proxyServerId);
				pad.setAccessYn(ACCESS_FAIL);

				dto.addProxyAccess(pad);
			}
		}
		return pad;
	}

	private boolean test(URI uri, ProxyServerDto dto) {
		String domain = URLUtils.getProtocolAndDomain(uri);
		HttpHost host = dto.getHost();

		ProxyTester tester = testers.get(domain);
		if (tester == null)
			return validator.test(uri, dto.getHost());
		return validator.test(tester, host);
	}

	public void test(Proxy proxy) {
		ProxyServerUsageDto usage = proxy.getProxyServerUsage();
		ProxyServerDto dto = proxy.getProxyServer();

		Lock lock = getLock(proxy.getDomain(), dto.getId());
		try {
			lock.lock();

			if (!usage.isWaitingTest()) {
				boolean access = false;
				usage.setWaitingTest(true);
				if (validator.isAlive(dto.getHost())) {
					setStatus(dto, true);
					URI uri = proxy.getURI();
					access = test(uri, dto);
					setAccess(proxy.getDomain(), dto, access);
				} else {
					setStatus(dto, false);
				}

				if (!access) {
					addFailProxy(proxy);
					return;
				}
				usage.setWaitingTest(false);
			}
		} finally {
			lock.unlock();
		}
	}

	private boolean addFailProxy(Proxy proxy) {
		if (proxy != null) {
			proxy.increaseTestCnt();
			int testCnt = proxy.getTestCnt();

			if (testCnt < MAX_TEST_COUNT) {
				proxy.setNextTestTime(3 * testCnt);
				repo.add(ProxyRepository.QUEUE_TEST_FAIL_SERVER, proxy);
				return true;
			}
		}
		return false;
	}

	private Proxy getProxy(long jobId, ProxyServerUsageDto usage, URI uri) {
		String domain = URLUtils.getProtocolAndDomain(uri);
		if (!usage.isDomainExists(domain)) {
			usage.addDomain(domain);
			dbHandler.insertProxyAccessMapp(jobId, domain);
		}

		int maxActivePerJob = config.getMaxActive(jobId);
		if (usage.getUseCnt() >= maxActivePerJob)
			return null;
		usage.increaseUseCnt();
		return new Proxy(uri, this, usage);
	}

	private Proxy getProxyWait(long jobId, List<ProxyServerUsageDto> list, URI uri) {

		Proxy proxy = null;
		String domain = URLUtils.getProtocolAndDomain(uri);
		ProxyComparator.sort(list, ProxyComparator.WAIT_CNT_COMPARATOR);
		AtomicInteger result = new AtomicInteger(0);

		long elapsedTime = System.currentTimeMillis();

		for (int i = 0; i < list.size(); i++) {
			ProxyServerUsageDto usage = list.get(i);

			if (!usage.isDomainExists(domain)) {
				usage.addDomain(domain);
				dbHandler.insertProxyAccessMapp(jobId, domain);
			}

			int a = canAccess(domain, usage);
			if (a == ACCESS_FAIL) {
				list.remove(i--);
				continue;
			}

			long waitTime = MAX_WAIT_TIME - (System.currentTimeMillis() - elapsedTime);
			synchronized (usage) {
				int maxActivePerJob = config.getMaxActive(jobId);
				if (usage.getUseCnt() >= maxActivePerJob && waitTime > 0) {
					try {
						usage.increaseWaitCnt();
						if (logger.isDebugEnabled()) {
							logger.debug(getClass().getSimpleName(), String.format("# Waiting => jobId : %s, proxy : %s, domain : %s - [%s][%s]",
									jobId, usage.getProxyServerId(), domain, usage.getUseCnt(), usage.getWaitCnt()));
						}
						usage.wait(waitTime);
					} catch (InterruptedException e) {
						return null;
					} finally {
						usage.decreaseWaitCnt();
						waitTime = MAX_WAIT_TIME - (System.currentTimeMillis() - elapsedTime);
					}
				}
			}

			if (waitTime <= 0L)
				break;

			result.set(RESULT_CONTINUE);
			ProxyServerDto dto = usage.getProxyServerDto();
			proxy = getProxy(uri, jobId, domain, dto, usage, result);

			if (result.get() == RESULT_FAIL)
				list.remove(i--);
			if (result.get() > RESULT_CONTINUE)
				continue;

			if (proxy == null) {
				if (waitTime > 0) {
					if (i >= (list.size() - 1))
						i = 0;
				}
				continue;
			} else {
				break;
			}
		}

		double interval = (System.currentTimeMillis() - elapsedTime) / 1000D;
		if (proxy == null) {
			if (logger.isWarnEnabled())
				logger.warn(getClass().getSimpleName(),
						String.format("# Fail to get a proxy information => jobId : %s, domain : %s (%.3f sec.)", jobId, domain, interval));
			if (interval < MAX_WAIT_TIME / 1000L)
				throw PException.buildException(WSErrorCode.PROXY_NOT_AVAILABLE,
						String.format("Proxy not available!! => jobId : %s, domain : %s (%.3f sec.)", jobId, domain, interval));
			else
				throw PException.buildException(WSErrorCode.PROXY_NOT_AVAILABLE,
						String.format("Time out waiting for proxy information => jobId : %s, domain : %s (%.3f sec.)", jobId, domain, interval));
		} else if (logger.isDebugEnabled()) {
			ProxyServerUsageDto usage = proxy.getProxyServerUsage();
			logger.debug(getClass().getSimpleName(),
					String.format("# Success to get a proxy information => jobId : %s, domain : %s (%.3f sec.) - [%s][%s]", jobId, domain, interval,
							usage.getUseCnt(), usage.getWaitCnt()));
		}
		return proxy;
	}

	public void release(Proxy server) {
		ProxyServerUsageDto usage = server.getProxyServerUsage();
		usage.decreaseUseCnt();

		synchronized (usage) {
			int maxActivePerJob = config.getMaxActive(usage.getJobId());
			if (usage.getUseCnt() < maxActivePerJob)
				usage.notify();
		}
	}

	private Lock getLock(String domain, long proxyServerId) {
		return lockPool.getLock(String.format("%s(%s)", domain, proxyServerId));
	}

	private Map<String, ProxyTester> getProxyTesters(String basePackage) throws Exception {
		ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();
		MetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory(resourcePatternResolver);

		Map<String, ProxyTester> testers = new HashMap<>();
		String packageSearchPath = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX
				+ ClassUtils.convertClassNameToResourcePath(SystemPropertyUtils.resolvePlaceholders(basePackage)) + "/" + "**/*.class";

		Resource[] resources = resourcePatternResolver.getResources(packageSearchPath);

		for (Resource resource : resources) {
			if (resource.isReadable()) {
				MetadataReader metadataReader = metadataReaderFactory.getMetadataReader(resource);
				Class<?> cls = Class.forName(metadataReader.getClassMetadata().getClassName());

				if (!cls.isInterface() && ProxyTester.class.isAssignableFrom(cls)) {
					try {
						ProxyTester tester = (ProxyTester) cls.newInstance();
						String testUrl = tester.getTestUrl();

						if (StringUtils.isNotBlank(testUrl)) {
							String domain = URLUtils.getProtocolAndDomain(testUrl);
							testers.put(domain, tester);
							logger.info(getClass().getSimpleName(), String.format("# Add proxy tester -> %s / %s", domain, cls.getName()));
						}
					} catch (InstantiationException e) {
						if (e.getCause() instanceof NoSuchMethodException)
							logger.warn(getClass().getSimpleName(),
									String.format("# Instantiation failed!! -> %s | %s", cls.getName(), e.getMessage()));
					}
				}
			}
		}
		return testers;
	}

	public boolean isReachable(long jobId) {
		int status = STATUS_INACTIVE;
		for (ProxyServerDto dto : repo.getServers()) {
			ProxyServerUsageDto usage = repo.getProxyServerUsage(jobId, dto);
			status = Math.max(status, usage.getStatus());
			if (status == STATUS_ACTIVE)
				break;
		}
		return status == STATUS_ACTIVE;
	}

	public boolean isReachable(String domain) {
		int status = STATUS_INACTIVE;
		int count = 0;
		for (ProxyServerDto dto : repo.getServers()) {
			for (ProxyAccessDto d : dto.getProxyAccess()) {
				if (domain.equals(d.getDomain())) {
					count++;
					status = Math.max(status, d.getAccessYn());
					if (status == STATUS_ACTIVE)
						break;
				}
				if (status == STATUS_ACTIVE)
					break;
			}
		}
		return count == 0 || status == STATUS_ACTIVE;
	}

	private class MaintenanceWorker extends TimerTask {

		private long lastTestTime = 0;
		private long lastUpdateDBTime = 0;
		private long lastUpdateProxyTime = System.currentTimeMillis();
		private long lastUpdateMonitorTime = System.currentTimeMillis();

		private boolean running = false;

		public MaintenanceWorker() {
			startTest();
		}

		private TestWorker[] wokers = new TestWorker[TEST_WOKER_SIZE];

		public void startTest() {
			if (!running) {
				for (int i = 0; i < wokers.length; i++) {
					wokers[i] = new TestWorker(i + 1);
					wokers[i].start();
				}
				running = true;
			}
		}

		public void stopTest() {
			if (running) {
				for (int i = 0; i < wokers.length; i++) {
					if (wokers[i] != null) {
						if (!wokers[i].isInterrupted())
							wokers[i].interrupt();
						wokers[i] = null;
					}
				}
				running = false;
			}
		}

		public void run() {
			try {
				logging();
				testProxy();
				testAccess();
				updateDatabase();
				updateProxies();
			} catch (Exception e) {
				logger.error(getClass().getSimpleName(), e.getMessage(), e);
			}
		}

		private void logging() {
			if (monitorLogger.isDebugEnabled()) {
				long currentTime = System.currentTimeMillis();

				if (currentTime - lastUpdateMonitorTime > MONITOR_LOG_INTERVAL) {
					for (ProxyServerUsageDto e : repo.getProxyServerUsages()) {
						ProxyServerDto d = e.getProxyServerDto();
						String message = String.format("jobId:%s, proxy:%s, status/entry:%s/%s => R/U/W:%s/%s/%s ", e.getJobId(), d.toString(),
								e.getStatus(), e.getEntryYn(), e.getReqCnt(), e.getUseCnt(), e.getWaitCnt());
						monitorLogger.debug(getClass().getSimpleName(), message);
					}
					lastUpdateMonitorTime = currentTime;
				}
			}
		}

		private void testProxy() {
			if (!repo.isEmpty(ProxyRepository.QUEUE_TEST_FAIL_SERVER)) {
				int size = repo.size(ProxyRepository.QUEUE_TEST_FAIL_SERVER);
				for (int i = 0; i < size; i++) {
					Proxy e = repo.poll(ProxyRepository.QUEUE_TEST_FAIL_SERVER);
					long nextTestTime = e.getNextTestTime();
					if (nextTestTime > System.currentTimeMillis())
						repo.add(ProxyRepository.QUEUE_TEST_FAIL_SERVER, e);
					else
						repo.add(ProxyRepository.QUEUE_TEST_WAITING_SERVER, e);
				}
			}
		}

		private void testAccess() {
			long currentTime = System.currentTimeMillis();

			if (currentTime - lastTestTime > TEST_ACCESS_INTERVAL) {
				// startTest();
				for (Object e : repo.getServers())
					repo.add(ProxyRepository.QUEUE_TEST_WAITING_SERVER, e);
				// 종료 코드
				// repo.add(ProxyRepository.QUEUE_TEST_WAITING_SERVER, -1);
				lastTestTime = currentTime;
			}
		}

		private void updateDatabase() {
			long currentTime = System.currentTimeMillis();

			if (currentTime - lastUpdateDBTime > UPDATE_DB_INTERVAL) {
				Map<Long, Short> us = repo.getProxyServerUsageStatus();

				int current = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
				boolean changed = dayOfYear != current;

				for (ProxyServerUsageDto dto : repo.getProxyServerUsages()) {
					Short status = us.get(dto.getId());
					dto.setStatus(status != null ? status : STATUS_ACTIVE);

					if (changed) {
						dto.setReqCnt(0L);
						dto.setFailCnt(0L);
					}
					if (dto.isModified()) {
						dbHandler.updateProxyServerUsage(dto);
						dto.setModified();
					}
				}

				if (changed)
					dayOfYear = current;
				if (decider != null) {
					Map<Long, Integer> sc = repo.getAvailableProxyServerCount();
					for (Entry<Long, Integer> entry : sc.entrySet())
						decider.put(entry.getKey(), entry.getValue());
				}
				lastUpdateDBTime = currentTime;
			}
		}

		private void updateProxies() {
			long currentTime = System.currentTimeMillis();

			if (currentTime - lastUpdateProxyTime > UPDATE_PROXY_INTERVAL) {
				repo.reloadIfUpdated();
				lastUpdateProxyTime = currentTime;
			}
		}
	}

	private class TestWorker extends Thread {

		public TestWorker(int i) {
			setName("ProxyServerPool.TestWorker-" + i);
		}

		@Override
		public void run() {
			while (!isInterrupted()) {
				try {
					if (repo == null) {
						Thread.sleep(1000);
						continue;
					}

					Object object = repo.poll(ProxyRepository.QUEUE_TEST_WAITING_SERVER, 5, TimeUnit.SECONDS);

					if (object != null) {
						if (object instanceof ProxyServerDto) {
							ProxyServerDto dto = (ProxyServerDto) object;

							if (validator.isAlive(dto.getHost())) {
								setStatus(dto, true);
								testDomain(dto);
							} else {
								setStatus(dto, false);
							}
						} else if (object instanceof ProxyDomain) {
							ProxyDomain pd = (ProxyDomain) object;
							ProxyServerDto dto = pd.getProxyServerDto();
							String domain = pd.getDomain();

							if (validator.isAlive(dto.getHost())) {
								setStatus(dto, true);
								testDomain(domain, dto, false);
							} else {
								setStatus(dto, false);
							}
						} else if (object instanceof Proxy) {
							Proxy proxy = (Proxy) object;
							ProxyServerDto dto = proxy.getProxyServer();

							if (validator.isAlive(dto.getHost())) {
								setStatus(dto, true);
								testDomain(proxy);
							} else {
								setStatus(dto, false);

								if (!addFailProxy(proxy)) {
									ProxyServerUsageDto usage = proxy.getProxyServerUsage();
									usage.setWaitingTest(false);
								}
							}
						} else if (object instanceof Integer) {
							Integer code = (Integer) object;
							if (code == -1)
								stopSelf();
						}
					}
				} catch (InterruptedException e) {
					break;
				} catch (IllegalArgumentException e) {
					logger.warn(getClass().getSimpleName(), String.format("Illegal Argument => %s", e.getMessage()));
				} catch (Exception e) {
					logger.error(getClass().getSimpleName(), e.getMessage(), e);
				}
			}
		}

		private void stopSelf() {
			maintenanceWorker.stopTest();
		}

		private void testDomain(ProxyServerDto dto) {
			for (String domain : repo.getAllDomain())
				testDomain(domain, dto, false);
		}

		private void testDomain(Proxy proxy) {
			String domain = proxy.getDomain();

			ProxyServerUsageDto usage = proxy.getProxyServerUsage();
			ProxyServerDto dto = proxy.getProxyServer();

			if (!testDomain(domain, dto, true)) {
				if (addFailProxy(proxy))
					return;
			}
			usage.setWaitingTest(false);
		}

		private boolean testDomain(String domain, ProxyServerDto dto, boolean immediately) {
			ProxyAccessDto pad = getProxyAccess(domain, dto);
			HttpHost host = dto.getHost();
			long elapsedTime = System.currentTimeMillis();
			boolean access = false;

			if (!immediately) {
				long lastTestTime = pad.getLastTestTime();
				if (System.currentTimeMillis() - lastTestTime < testInterval)
					return pad.getAccessYn() == ACCESS_SUCCESS;
			}

			if (testers.containsKey(domain)) {
				ProxyTester tester = testers.get(domain);
				access = validator.test(tester, host);
			} else {
				URI uri = null;
				try {
					uri = new URI(domain);
				} catch (URISyntaxException e) {
				}
				access = validator.test(uri, dto.getHost());
			}

			if (logger.isDebugEnabled())
				logger.debug(getClass().getSimpleName(), String.format("# Test result -> %s, domain -> %s, proxy -> %s/%s (%s ms)", access, domain,
						dto.getId(), host, (System.currentTimeMillis() - elapsedTime)));

			setAccess(pad, access);
			return access;
		}
	}
}
