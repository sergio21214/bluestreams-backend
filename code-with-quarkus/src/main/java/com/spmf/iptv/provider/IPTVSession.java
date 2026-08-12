package com.spmf.iptv.provider;

public class IPTVSession {

    private String token;

    private long expires;

    private String username;

    private String password;

    public boolean expired() {
        return System.currentTimeMillis() >= expires;
    }

    // getters/setters
}