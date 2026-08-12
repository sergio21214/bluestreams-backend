package com.spmf.iptv.proxy;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class ConnectionManager {

    private final ConcurrentHashMap<Long, ProviderConnection>
            connections = new ConcurrentHashMap<>();

    public ProviderConnection get(Long id){

        return connections.get(id);

    }

    public void put(Long id,
                    ProviderConnection c){

        connections.put(id,c);

    }

    public void remove(Long id){

        connections.remove(id);

    }

}
