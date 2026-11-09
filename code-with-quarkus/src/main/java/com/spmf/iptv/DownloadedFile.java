package com.spmf.iptv;

/**
 * A file that already exists inside the IPTV download directory.
 */
public class DownloadedFile {

    public String name;

    /** Path relative to the download root, using '/' separators. */
    public String relativePath;

    public long size;

    public long modified;

    public String contentType;

    /** API path that streams the raw file (HTTP Range supported). */
    public String streamUrl;
}
