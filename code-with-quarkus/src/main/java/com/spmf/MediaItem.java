package com.spmf;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

@Entity
@Table(name = "media_items")
public class MediaItem extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "media_type")
    public String mediaType;

    @Column(name = "series_title")
    public String seriesTitle;

    @Column(name = "season_number")
    public Integer seasonNumber;

    @Column(name = "episode_number")
    public Integer episodeNumber;

    @Column(name = "folder_name")
    public String folderName;

    @Column(name = "manifest_url")
    public String manifestUrl;

    @Column(name = "subtitle_path")
    public String subtitlePath;

    public String title;
    public String filename;

    @Column(name = "poster_url")
    public String poster;

    @Column(name = "artist")
    public String artist;
    @Column(name = "album")
    public String album;
    @Column(name = "track_number")
    public Integer trackNumber;
    @Column(name = "duration_ms")
    public Long durationMs;
    @Column(name = "file_path")
    public String filePath;
    @Column(name = "genre")
    public String genre;


}