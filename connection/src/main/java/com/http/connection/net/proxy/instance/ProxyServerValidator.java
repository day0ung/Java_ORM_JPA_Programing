package com.http.connection.proxy.instance;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URI;
import java.net.UnknownHostException;

import javax.net.ssl.SSLContext;

import org.apache.commons.httpclient.HttpVersion;
import org.apache.commons.httpclient.ProxyClient;
import org.apache.commons.httpclient.ProxyClient.ConnectResponse;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.ssl.SSLContexts;
import org.springframework.beans.factory.InitializingBean;

import com.epopcon.wspider.common.logger.Logger;
import com.epopcon.wspider.common.logger.WSLogger;
import com.epopcon.wspider.common.logger.WSLogger.WSTYPE;
import com.epopcon.wspider.net.Result;
import com.epopcon.wspider.net.TrustAllStrategy;

public class ProxyServerValidator implements InitializingBean {

	protected Logger logger = WSLogger.getLogger(WSTYPE.PROXY);

	private final String USER_AGENT = "Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/66.0.3359.181 Safari/537.36";
	private final static int CONNECTION_TIME_OUT = 5000;
	private final static int SOCKET_TIME_OUT = 10000;

	private CloseableHttpClient client = null;

	public void setHttpClient(CloseableHttpClient client) {
		this.client = client;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		if (client == null) {
			SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(null, new TrustAllStrategy()).build();
			SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);

			HttpClientBuilder builder = HttpClientBuilder.create().setSSLSocketFactory(sslsf).disableRedirectHandling();

			client = builder.build();
		}
	}

	public boolean isAlive(HttpHost host) {
		String hostName = host.getHostName();
		int port = host.getPort();

		if (port == -1) {
			String scheme = host.getSchemeName();
			if (scheme.equals("http"))
				port = 80;
			else if (scheme.equals("https"))
				port = 443;
		}

		Socket socket = new Socket();
		SocketAddress socketAddress = new InetSocketAddress(hostName, port);

		try {
			socket.connect(socketAddress, SOCKET_TIME_OUT);
			return socket.isConnected();
		} catch (Throwable e) {
		} finally {
			IOUtils.closeQuietly(socket);
		}
		return false;
	}

	public boolean test(URI uri, HttpHost host) {

		String hostName = host.getHostName();
		int port = host.getPort();

		if (port == -1) {
			String scheme = host.getSchemeName();
			if (scheme.equals("http"))
				port = 80;
			else if (scheme.equals("https"))
				port = 443;
		}

		String authority = uri.getAuthority();
		String domain = uri.getHost();
		ProxyClient client = new ProxyClient();

		client.getParams().setParameter("http.useragent", USER_AGENT);
		client.getParams().setParameter("http.connection.timeout", CONNECTION_TIME_OUT);
		client.getParams().setParameter("http.socket.timeout", SOCKET_TIME_OUT);
		client.getParams().setVersion(HttpVersion.HTTP_1_1);
		client.getHostConfiguration().setHost(authority);
		client.getHostConfiguration().setProxy(hostName, port);

		Socket socket = null;
		ConnectResponse response = null;
		boolean success = false;

		try {
			response = client.connect();
			socket = response.getSocket();
			success = socket != null;

			if (!success)
				InetAddress.getByName(domain);
		} catch (UnknownHostException e) {
			throw new IllegalArgumentException("DNS Lookup Failed!! -> " + domain, e);
		} catch (Exception e) {
		} finally {
			IOUtils.closeQuietly(socket);
		}
		return success;
	}

	public boolean test(ProxyTester tester, HttpHost host) {
		if (tester instanceof DirectProxyTester)
			return ((DirectProxyTester) tester).test(client, host);
		return testInternal(tester, host);
	}

	private boolean testInternal(ProxyTester tester, HttpHost host) {
		HttpUriRequest request = null;
		CloseableHttpResponse response = null;

		try {
			RequestBuilder requestBuilder = RequestBuilder.get().setUri(tester.getTestUrl());
			String autority = requestBuilder.getUri().getAuthority();

			RequestConfig.Builder config = RequestConfig.custom().setConnectTimeout(CONNECTION_TIME_OUT)
					.setConnectionRequestTimeout(CONNECTION_TIME_OUT).setSocketTimeout(SOCKET_TIME_OUT).setRedirectsEnabled(false);
			if (host != null)
				config.setProxy(host);

			HttpClientContext context = HttpClientContext.create();
			context.setAttribute(HttpClientContext.REQUEST_CONFIG, config.build());
			request = requestBuilder.setHeader("User-Agent", USER_AGENT)
					.setHeader("Accept", "Accept: text/html, image/gif, image/jpeg, *; q=.2, */*; q=.2")
					.setHeader("Accept-Language", "ko,en-US;q=0.8,en;q=0.6").setHeader("Host", autority).build();
			response = client.execute(request, context);

			return tester.assertValue(new Result(requestBuilder.getUri(), context, response));
		} catch (Exception e) {
		} finally {
			IOUtils.closeQuietly(response);
		}
		return false;
	}
}
