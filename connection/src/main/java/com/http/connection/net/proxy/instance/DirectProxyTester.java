package com.http.connection.net.proxy.instance;

import org.apache.http.HttpHost;
import org.apache.http.impl.client.CloseableHttpClient;

public interface DirectProxyTester extends ProxyTester {
	boolean test(CloseableHttpClient client, HttpHost host);
}
