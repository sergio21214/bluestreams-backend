package com.spmf.iptv;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregated status for a download batch.
 *
 * <p>{@code status} is one of: {@code running}, {@code completed},
 * {@code completed_with_errors}, {@code failed}.</p>
 */
public class BatchStatus {

    public String batchId;

    public String folder;

    public int total;

    public int queued;

    public int running;

    public int completed;

    public int failed;

    public String status;

    public long createdAt;

    public long finishedAt;

    public List<JobStatus> jobs = new ArrayList<>();

    public static class JobStatus {

        public String jobId;

        public String filename;

        public String status;

        public long bytesWritten;

        public long totalBytes;

        public String error;

        public long finishedAt;
    }
}
