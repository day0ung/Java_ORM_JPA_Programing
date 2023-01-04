package com.http.connection.net.proxy.oxylabs;

import com.google.gson.*;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Value;


import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class OxylabsHandler {
    @Value("${proxy.oxylabs.username}")
    private String username;
    @Value("${proxy.oxylabs.password}")
    private String password;
    @Value("${proxy.oxylabs.key}")
    private String key;

    private int port = 60000;
    private List<String> ips = null;
    private int ipSize = 0;

    public CloseableHttpClient client;

    private String oxylabs_Url = "https://api.oxylabs.io/v1/proxies/lists/%s";

    @PostConstruct
    private void init() throws ClientProtocolException, IOException {
        ips = new ArrayList<String>();


        client = HttpClients.custom()
                .setConnectionManager(new BasicHttpClientConnectionManager())
                .build();

        HttpGet httpGet = new HttpGet(String.format(oxylabs_Url, key));
        byte[] encoder = Base64.encodeBase64((username+":"+password).getBytes());
        String encodeStr = new String(encoder);

        httpGet.addHeader("Authorization",String.format("Basic %s", encodeStr));

        CloseableHttpResponse response = client.execute(httpGet);

        String body = EntityUtils.toString(response.getEntity());

        JsonArray jArr = new JsonParser().parse(body).getAsJsonArray();

        jArr.forEach(x -> {
            ips.add(x.getAsJsonObject().get("ip").getAsString());
        });

        ipSize = ips.size();

    }
}
