package com.spmf.iptv.proxy;



import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class ProviderConnection {

    public Long channelId;

    public HttpClient client;

    public HttpResponse<InputStream> response;

    public InputStream input;

    public int viewers;

    public long lastAccess;

}