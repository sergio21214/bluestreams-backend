package com.spmf.iptv;

import com.fasterxml.jackson.annotation.JsonAlias;

/**
 * Payload for {@code POST /iptv/downloads/series}.
 *
 * <p>Resolves an Xtream account, asks the provider for the series metadata and
 * queues every episode of the requested season (or every season when
 * {@code Season} is omitted).</p>
 */
public class SeriesDownloadRequest {

    @JsonAlias({ "AccountId", "accountId", "Id", "id" })
    public Long accountId;

    @JsonAlias({ "SeriesId", "seriesId" })
    public Long seriesId;

    /** Null means all seasons. */
    @JsonAlias({ "Season", "season" })
    public Integer season;

    /** Optional destination subfolder inside the download root. */
    @JsonAlias({ "Folder", "folder" })
    public String folder;

    /** Optional display name when the provider does not return one. */
    @JsonAlias({ "SeriesTitle", "seriesTitle", "Title", "title" })
    public String seriesTitle;
}
