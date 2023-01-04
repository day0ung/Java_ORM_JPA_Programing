package com.http.connection.net.dto;

public class ProxyAccessDto {

	private Long id;
	private String domain;
	private Long proxyServerId;
	private short accessYn;

	private long lastTestTime = 0;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getDomain() {
		return domain;
	}

	public void setDomain(String domain) {
		this.domain = domain;
	}

	public Long getProxyServerId() {
		return proxyServerId;
	}

	public void setProxyServerId(Long proxyServerId) {
		this.proxyServerId = proxyServerId;
	}

	public short getAccessYn() {
		return accessYn;
	}

	public void setAccessYn(short accessYn) {
		this.accessYn = accessYn;
	}

	public long getLastTestTime() {
		return lastTestTime;
	}

	public void setLastTestTime(long lastTestTime) {
		this.lastTestTime = lastTestTime;
	}

}
