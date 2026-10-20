package com.spmf.iptv;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.List;

/**
 * Payload accepted by {@code POST /iptv/downloads}.
 *
 * Minimal form:
 * <pre>
 * { "Url": "https://host/movie.mp4" }
 * </pre>
 *
 * Full form:
 * <pre>
 * {
 *   "Url": "https://host/movie.mp4",
 *   "Filename": "My Movie",                  // optional, derived from Url when omitted
 *   "Folder": "Movies/My Movie",             // optional subfolder inside the download dir
 *   "Subtitles": [ { "Lang": "en", "Url": "https://host/sub.vtt" } ]
 * }
 * </pre>
 *
 * {@code @JsonAlias} lets the same field arrive as {@code Url} or {@code url},
 * {@code URL} or {@code streamUrl}, so the Android client does not have to match
 * Java's casing.
 */
public class DownloadRequest {

    @JsonAlias({ "Url", "url", "URL", "StreamUrl", "streamUrl", "stream_url", "SourceUrl" })
    public String url;

    @JsonAlias({ "Filename", "FileName", "filename", "fileName", "Name", "name", "Title", "title" })
    public String filename;

    @JsonAlias({ "Folder", "folder", "Category", "category", "SeriesTitle", "seriesTitle" })
    public String folder;

    @JsonAlias({ "Subtitles", "subtitles", "Subtitle", "subtitle" })
    public List<Subtitle> subtitles;

    public static class Subtitle {

        @JsonAlias({ "Lang", "lang", "Language", "language", "Label", "label" })
        public String lang;

        @JsonAlias({ "Url", "url", "URL", "File", "file" })
        public String url;
    }
}
