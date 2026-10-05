package com.spmf;

import jakarta.inject.Inject;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import java.util.Set;

@Path("/processing")
public class ProcessingResource {

    @Inject
    ProcessingStore store;

    @GET
    public Set<String> getProcessing() {

        return store.processing;
    }

    
}