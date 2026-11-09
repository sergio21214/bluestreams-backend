package com.spmf.iptv;

import java.util.ArrayList;
import java.util.List;

/**
 * A group of downloads queued together (for example, one season of a show).
 */
public class DownloadBatch {

    public String batchId;

    /** Shared folder for items that did not specify their own folder. */
    public String folder;

    /** Number of jobs in this batch. */
    public int total;

    public long createdAt;

    public List<DownloadJob> jobs = new ArrayList<>();

    public DownloadBatch() {
    }

    public DownloadBatch(String batchId, String folder) {
        this.batchId = batchId;
        this.folder = folder;
        this.createdAt = System.currentTimeMillis();
    }
}
