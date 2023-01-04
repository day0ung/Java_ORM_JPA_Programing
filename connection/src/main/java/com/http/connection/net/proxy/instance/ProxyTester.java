package com.http.connection.proxy.instance;

import com.epopcon.wspider.net.Result;

public interface ProxyTester {

	public String getTestUrl();

	public boolean assertValue(Result result);
}
