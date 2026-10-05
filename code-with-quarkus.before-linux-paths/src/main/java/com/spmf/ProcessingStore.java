package com.spmf;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@ApplicationScoped
public class ProcessingStore {

    public Set<String> processing =
            ConcurrentHashMap.newKeySet();

    public Queue<String> waiting =
            new ConcurrentLinkedQueue<>();

    public Set<String> failed =
            ConcurrentHashMap.newKeySet();
}