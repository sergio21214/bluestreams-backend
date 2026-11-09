package com.spmf.iptv;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.List;

/**
 * Payload for {@code POST /iptv/downloads/batch}.
 *
 * <pre>
 * {
 *   "Folder": "Series/Breaking Bad/Season 1",
 *   "Items": [
 *     { "Url": "https://host/S01E01.mkv", "Filename": "S01E01 - Pilot.mkv" },
 *     { "Url": "https://host/S01E02.mkv", "Filename": "S01E02 - Cat in the Bag.mkv" }
 *   ]
 * }
 * </pre>
 */
public class BatchDownloadRequest {

    @JsonAlias({ "Folder", "folder" })
    public String folder;

    @JsonAlias({ "Items", "items", "Downloads", "downloads", "Files", "files" })
    public List<DownloadRequest> items;
}
