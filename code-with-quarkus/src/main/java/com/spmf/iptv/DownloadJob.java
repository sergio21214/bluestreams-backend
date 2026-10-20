package com.spmf.iptv;

/**
 * State of one download job, returned by {@code POST /iptv/downloads} and polled
 * through {@code GET /iptv/downloads/{jobId}}.
 *
 * {@code status} is one of: {@code started}, {@code running}, {@code completed},
 * {@code failed}.
 */
public class DownloadJob {

    public String jobId;

    /** Original URL as sent by the client. */
    public String url;

    /** Filename the file is being saved as. */
    public String filename;

    /** Optional subfolder inside the download directory. */
    public String folder;

    /** Absolute path of the file on this machine. */
    public String path;

    public String status;

    /** Bytes written so far (main file). */
    public long bytesWritten;

    /** Total size when the server reported Content-Length, otherwise -1. */
    public long totalBytes = -1;

    public String error;

    public long startedAt;

    public long finishedAt;

    public DownloadJob() {
    }

    public DownloadJob(String jobId, String url, String filename) {
        this.jobId = jobId;
        this.url = url;
        this.filename = filename;
        this.status = "started";
        this.startedAt = System.currentTimeMillis();
    }
}
