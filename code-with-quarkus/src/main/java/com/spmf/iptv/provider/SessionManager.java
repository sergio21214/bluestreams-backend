package com.spmf.iptv.provider;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class SessionManager {

    private final ConcurrentHashMap<Long, IPTVSession> sessions =
            new ConcurrentHashMap<>();

    public IPTVSession get(Long providerId) {
        return sessions.get(providerId);
    }

    public void put(Long providerId,
                    IPTVSession session) {

        sessions.put(providerId, session);

    }

}
