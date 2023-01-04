package com.http.connection.net.proxy.instance;

import java.net.URI;

import org.apache.http.HttpHost;

import com.epopcon.wspider.common.util.URLUtils;
import com.epopcon.wspider.db.dflt.dto.ProxyServerDto;
import com.epopcon.wspider.db.dflt.dto.ProxyServerUsageDto;

public class Proxy {

	private ProxyServerPool pool;
	private ProxyServerDto proxyServerDto;
	private ProxyServerUsageDto proxyServerUsageDto;

	private URI uri;
	private String domain;
	private HttpHost host;
	private boolean destroy = false;

	private int testCnt = 0;
	private long nextTestTime = 0;

	Proxy(URI uri, ProxyServerPool pool, ProxyServerUsageDto proxyServerUsageDto) {
		this.uri = uri;
		this.domain = URLUtils.getProtocolAndDomain(uri);
		this.pool = pool;
		this.proxyServerUsageDto = proxyServerUsageDto;
		this.proxyServerDto = proxyServerUsageDto.getProxyServerDto();
		this.host = proxyServerDto.getHost();
	}

	URI getURI() {
		return uri;
	}

	public String getDomain() {
		return domain;
	}

	ProxyServerDto getProxyServer() {
		return proxyServerDto;
	}

	ProxyServerUsageDto getProxyServerUsage() {
		return proxyServerUsageDto;
	}

	int getTestCnt() {
		return testCnt;
	}

	void increaseTestCnt() {
		testCnt++;
	}

	long getNextTestTime() {
		return nextTestTime;
	}

	void setNextTestTime(int sec) {
		this.nextTestTime = System.currentTimeMillis() + (sec * 1000);
	}

	public void increaseReqCnt() {
		proxyServerUsageDto.increaseReqCnt();
	}

	public void increaseFailCnt() {
		proxyServerUsageDto.increaseFailCnt();
	}

	public HttpHost getHost() {
		return host;
	}

	public void destroy() {
		if (!destroy) {
			pool.release(this);
			destroy = true;
		}
	}

	@Override
	public int hashCode() {
		return String.format("(%s)%s", proxyServerDto.getId(), URLUtils.getProtocolAndDomain(uri)).hashCode();
	}
}
