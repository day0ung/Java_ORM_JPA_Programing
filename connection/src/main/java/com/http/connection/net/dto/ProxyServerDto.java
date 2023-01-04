package com.http.connection.net.dto;

import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;

public class ProxyServerDto {

	private Long id;
	private String serverName;
	private String groupName;
	private String ip;
	private String domain;
	private Short accessYn;
	private Short status;
	private String regId;
	private Date regDt;
	private String uptId;
	private Date uptDt;

	private List<ProxyAccessDto> proxyAccess;

	private HttpHost host;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getGroupName() {
		return groupName;
	}

	public void setGroupName(String groupName) {
		this.groupName = groupName;
	}

	public String getServerName() {
		return serverName;
	}

	public void setServerName(String serverName) {
		this.serverName = serverName;
	}

	public String getIp() {
		return ip;
	}

	public void setIp(String ip) {
		this.ip = ip;
	}

	public String getDomain() {
		return domain;
	}

	public void setDomain(String domain) {
		this.domain = domain;
	}

	public Short getAccessYn() {
		return accessYn;
	}

	public void setAccessYn(Short accessYn) {
		this.accessYn = accessYn;
	}

	public Short getStatus() {
		return status;
	}

	public void setStatus(Short status) {
		this.status = status;
	}

	public String getRegId() {
		return regId;
	}

	public void setRegId(String regId) {
		this.regId = regId;
	}

	public Date getRegDt() {
		return regDt;
	}

	public void setRegDt(Date regDt) {
		this.regDt = regDt;
	}

	public String getUptId() {
		return uptId;
	}

	public void setUptId(String uptId) {
		this.uptId = uptId;
	}

	public Date getUptDt() {
		return uptDt;
	}

	public void setUptDt(Date uptDt) {
		this.uptDt = uptDt;
	}

	public List<ProxyAccessDto> getProxyAccess() {
		return proxyAccess;
	}

	public void setProxyAccess(List<ProxyAccessDto> proxyAccess) {
		this.proxyAccess = proxyAccess;
	}

	public void addProxyAccess(ProxyAccessDto dto) {
		proxyAccess.add(dto);
	}

	public HttpHost getHost() {
		return host;
	}

	public void setHost(HttpHost host) {
		this.host = host;
	}

	public boolean compareTo(ProxyServerDto dto) {
		if (dto != null) {
			Long id = dto.getId();
			String serverName = StringUtils.defaultString(dto.getServerName());
			String groupName = StringUtils.defaultString(dto.getGroupName());
			String ip = StringUtils.defaultString(dto.getIp());

			if (id != null && StringUtils.isNotBlank(serverName) && StringUtils.isNotBlank(ip) && this.id != null
					&& StringUtils.isNotBlank(this.serverName) && StringUtils.isNotBlank(this.ip))
				return this.id.longValue() == id.longValue() && StringUtils.defaultString(this.serverName).equals(serverName)
						&& StringUtils.defaultString(this.groupName).equals(groupName) && StringUtils.defaultString(this.ip).equals(ip);
		}
		return false;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof ProxyServerDto) {
			Long id = ((ProxyServerDto) obj).getId();
			if (this.id != null && id != null)
				return this.id.longValue() == id.longValue();
		}
		return false;
	}

	@Override
	public int hashCode() {
		return (id == null) ? -1 : id.hashCode();
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();

		sb.append("[");
		sb.append("id=" + id);
		if (StringUtils.isNotBlank(groupName))
			sb.append(", groupName=" + groupName);
		sb.append(", serverName=" + serverName);
		sb.append(", ip=" + ip);
		sb.append(", status=" + status);
		sb.append("]");

		return sb.toString();
	}
}
