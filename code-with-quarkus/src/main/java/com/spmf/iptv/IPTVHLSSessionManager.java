package com.spmf.iptv;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class IPTVHLSSessionManager {

    private final ConcurrentHashMap<Long, IPTVHLSSession> sessions =
            new ConcurrentHashMap<>();

    public IPTVHLSSession get(Long channelId) {

        return sessions.get(channelId);

    }

    public void put(IPTVHLSSession session) {

        sessions.put(session.channelId, session);

    }

    public void remove(Long channelId) {

        sessions.remove(channelId);

    }
    public Collection<IPTVHLSSession> getAll() {
        return sessions.values();
    }
   

}