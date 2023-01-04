package com.http.connection.proxy.instance;

import java.util.ArrayList;
import java.util.List;

import com.epopcon.wspider.db.dflt.dto.ProxyServerDto;

class ProxyDomain {

	private ProxyServerDto proxyServerDto;
	private String domain;

	ProxyDomain(ProxyServerDto proxyServerDto, String domain) {
		this.proxyServerDto = proxyServerDto;
		this.domain = domain;
	}

	ProxyServerDto getProxyServerDto() {
		return proxyServerDto;
	}

	String getDomain() {
		return domain;
	}

	static List<ProxyDomain> convert(List<ProxyServerDto> servers, String domain) {
		List<ProxyDomain> list = new ArrayList<>(servers.size());
		for (ProxyServerDto e : servers)
			list.add(new ProxyDomain(e, domain));
		return list;
	}
}
