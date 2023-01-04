package com.http.connection.proxy.instance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.Resource;

import com.epopcon.wspider.common.logger.Logger;
import com.epopcon.wspider.common.logger.WSLogger;
import com.epopcon.wspider.common.logger.WSLogger.WSTYPE;
import com.epopcon.wspider.db.dflt.DbHandler;
import com.epopcon.wspider.net.proxy.holder.ProxyHolder;

public class ProxyConfig implements InitializingBean {

	private Logger logger = WSLogger.getLogger(WSTYPE.PROXY);

	@Autowired
	private DbHandler dbHandler;
	private Map<Long, Integer> maxActiveMap = new ConcurrentHashMap<>();
	private Map<Long, Boolean> skipLastMap = new ConcurrentHashMap<>();

	private Properties properties;

	@Value("${proxy.distribution.policy:}")
	private String policy;

	private int maxActivePerJob = 30;

	private Map<Long, Map<String, String>> props = new HashMap<>();

	public void setMaxActivePerJob(int maxActivePerJob) {
		this.maxActivePerJob = maxActivePerJob;
	}

	public void setLocation(Resource resource) {
		if (resource.exists()) {
			YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
			factory.setResources(resource);
			factory.afterPropertiesSet();
			properties = factory.getObject();
		} else {
			logger.warn(getClass().getSimpleName(), "Resource file not exists -> " + resource.getDescription());
		}
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		if (properties != null) {
			for (ServiceName e : getServiceNames()) {
				Long id = e.getId();
				String name = e.getName();
				addConfig(id, name);
			}
			addConfig(Long.MIN_VALUE, "service.default");
		}
	}

	private void addConfig(Long id, String name) {
		Map<String, String> map = new HashMap<>();

		String policy = properties.getProperty(String.format("%s.distribution.policy", name));
		//18-10-22 KSE application 에서 holler 종류를 컨트롤.
		if(StringUtils.isNotBlank(this.policy))
			policy=this.policy;
		String maxActive = properties.getProperty(String.format("%s.maxActivePerJob", name), String.valueOf(maxActivePerJob));
		String skipLastServer = properties.getProperty(String.format("%s.skipLastServer", name), String.valueOf("false"));

		if (StringUtils.isNotBlank(policy)) {

			switch (policy) {
			case "DEFAULT":
				map.put("distributionPolicy", String.valueOf(ProxyHolder.DISTRIBUTION_POLICY_DEFAULT));
				break;
			case "ROTATION":
				String memberCount = properties.getProperty(String.format("%s.distribution.memberCount", name));
				String rotationInterval = properties.getProperty(String.format("%s.distribution.rotationInterval", name));

				map.put("distributionPolicy", String.valueOf(ProxyHolder.DISTRIBUTION_POLICY_ROTATION));
				map.put("distributionPolicy.memberCount", memberCount);
				map.put("distributionPolicy.rotationInterval", rotationInterval);
				break;
			case "GROUP":
				String groupNames = properties.getProperty(String.format("%s.distribution.groupNames", name));
				map.put("distributionPolicy", String.valueOf(ProxyHolder.DISTRIBUTION_POLICY_GROUP));
				map.put("distributionPolicy.groupNames", groupNames);
				break;
			case "SIMPLE":
				map.put("distributionPolicy", String.valueOf(ProxyHolder.DISTRIBUTION_POLICY_SIMPLE));
				break;
			}
		}

		if (StringUtils.isNotBlank(maxActive))
			map.put("maxActivePerJob", maxActive);
		if (StringUtils.isNotBlank(skipLastServer))
			map.put("skipLastServer", skipLastServer);
		if (id.longValue() == Long.MIN_VALUE)
			maxActivePerJob = Integer.parseInt(maxActive);
		props.put(id, map);
		logger.info(getClass().getSimpleName(), String.format("=> id: %s, name: %s, prop : %s", id, name, map));
	}

	private List<ServiceName> getServiceNames() {
		List<ServiceName> serviceNames = new ArrayList<>();
		if (properties != null) {
			String[] names = properties.getProperty("service.names", "").split(",");

			for (String name : names) {
				name = name.trim();
				if (StringUtils.isEmpty(name))
					continue;
				String id = properties.getProperty(String.format("%s.id", name));
				serviceNames.add(new ServiceName(Long.parseLong(id), name));
			}
		}
		return serviceNames;
	}

	public Map<String, String> getProperties(Long serviceId) {
		return props.get(serviceId);
	}

	public int getMaxActive(Long jobId) {
		int maxActive = maxActivePerJob;
		if (maxActiveMap.containsKey(jobId)) {
			maxActive = maxActiveMap.get(jobId);
		} else {
			maxActive = getMaxActiveInternal(jobId);
			maxActiveMap.put(jobId, maxActive);
		}
		return maxActive;
	}
	
	public void setMaxActive(Long jobId, int maxActive){
		maxActiveMap.put(jobId, maxActive);
	}

	private int getMaxActiveInternal(Long jobId) {
		if (properties != null) {
			if (jobId == null || jobId == -1L)
				return maxActivePerJob;
			long serviceId = dbHandler.getServiceId(jobId);

			if (serviceId > -1 && props.containsKey(serviceId)) {
				Map<String, String> map = props.get(serviceId);
				String maxActive = map.get("maxActivePerJob");
				return Integer.parseInt(maxActive);
			}
		}
		return maxActivePerJob;
	}

	public boolean canSkipLast(Long jobId) {
		Boolean canSkip = false;
		if (skipLastMap.containsKey(jobId)) {
			canSkip = skipLastMap.get(jobId);
		} else {
			canSkip = canSkipLastInternal(jobId);
			skipLastMap.put(jobId, canSkip);
		}
		return canSkip;
	}

	private Boolean canSkipLastInternal(Long jobId) {
		Long serviceId = Long.MIN_VALUE;
		if (jobId != null && jobId > -1L)
			serviceId = dbHandler.getServiceId(jobId);
		if (!props.containsKey(serviceId))
			serviceId = Long.MIN_VALUE;

		Map<String, String> map = props.get(serviceId);
		String canSkip = map.get("skipLastServer");
		return Boolean.parseBoolean(canSkip);
	}

	private class ServiceName {
		private Long id;
		private String name;

		ServiceName(Long id, String name) {
			this.id = id;
			this.name = name;
		}

		Long getId() {
			return id;
		}

		String getName() {
			return name;
		}
	}
}
