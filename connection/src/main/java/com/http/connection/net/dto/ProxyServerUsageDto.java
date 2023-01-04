package com.http.connection.net.dto;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class ProxyServerUsageDto {

	private Long id;
	private Long serviceId;
	private Long jobId;
	private Long serverId;
	private Long proxyServerId;
	private volatile Long reqCnt;
	private volatile Long cumulativeReqCnt;
	private short status = 0;
	private short entryYn = 0;
	private String regId;
	private Date regDt;

	private volatile Long failCnt = 0L;
	private AtomicInteger useCnt = new AtomicInteger(0);
	private AtomicInteger waitCnt = new AtomicInteger(0);
	private Set<String> domains = new HashSet<>();
	private boolean waitingTest = false;

	private ProxyServerDto proxyServerDto = null;
	private String modifiedString = "";

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getServiceId() {
		return serviceId;
	}

	public void setServiceId(Long serviceId) {
		this.serviceId = serviceId;
	}

	public Long getJobId() {
		return jobId;
	}

	public void setJobId(Long jobId) {
		this.jobId = jobId;
	}

	public Long getServerId() {
		return serverId;
	}

	public void setServerId(Long serverId) {
		this.serverId = serverId;
	}

	public Long getProxyServerId() {
		return proxyServerId;
	}

	public void setProxyServerId(Long proxyServerId) {
		this.proxyServerId = proxyServerId;
	}

	public Long getReqCnt() {
		return reqCnt;
	}

	public void setReqCnt(Long reqCnt) {
		this.reqCnt = reqCnt;
	}
	
	public void setFailCnt(Long failCnt) {
		this.failCnt = failCnt;
	}

	public Long getCumulativeReqCnt() {
		return cumulativeReqCnt;
	}

	public void setCumulativeReqCnt(Long cumulativeReqCnt) {
		this.cumulativeReqCnt = cumulativeReqCnt;
	}

	public synchronized void increaseReqCnt() {
		this.reqCnt++;
		this.cumulativeReqCnt++;
	}
	
	public synchronized void increaseFailCnt(){
		this.failCnt++;
	}

	public Integer getUseCnt() {
		return useCnt.get();
	}

	public void increaseUseCnt() {
		useCnt.incrementAndGet();
	}

	public void decreaseUseCnt() {
		useCnt.decrementAndGet();
	}
	
	public Integer getWaitCnt() {
		return waitCnt.get();
	}

	public void increaseWaitCnt() {
		waitCnt.incrementAndGet();
	}

	public void decreaseWaitCnt() {
		waitCnt.decrementAndGet();
	}

	public short getStatus() {
		return status;
	}

	public void setStatus(short status) {
		this.status = status;
	}
	
	public short getEntryYn() {
		return entryYn;
	}

	public void setEntryYn(short entryYn) {
		this.entryYn = entryYn;
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

	public void addDomain(String domain) {
		synchronized (domains) {
			domains.add(domain);
		}
	}

	public boolean isDomainExists(String domain) {
		return domains.contains(domain);
	}
	
	public boolean isWaitingTest() {
		return waitingTest;
	}

	public void setWaitingTest(boolean waitingTest) {
		this.waitingTest = waitingTest;
	}

	public ProxyServerDto getProxyServerDto() {
		return proxyServerDto;
	}

	public void setProxyServerDto(ProxyServerDto proxyServerDto) {
		this.proxyServerDto = proxyServerDto;
	}
	
	public void setModified(){
		this.modifiedString = String.format("%s.%s.%s.%s", reqCnt, cumulativeReqCnt, status, entryYn);
	}
	
	public boolean isModified(){
		String currentString = String.format("%s.%s.%s.%s", reqCnt, cumulativeReqCnt, status, entryYn);
		return !currentString.equals(modifiedString);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();

		sb.append("[");
		sb.append("jobId=" + jobId);
		sb.append(", proxyServerId=" + proxyServerId);
		sb.append(", reqCnt=" + reqCnt);
		sb.append(", cumulativeReqCnt=" + cumulativeReqCnt);
		sb.append(", failCnt=" + failCnt);
		sb.append(", useCnt=" + useCnt.get());
		sb.append(", waitCnt=" + waitCnt.get());
		sb.append(", status=" + status + (!waitingTest ? "" : "(WT)"));
		sb.append(", domains=" + domains);
		sb.append("]");

		return sb.toString();
	}
}
