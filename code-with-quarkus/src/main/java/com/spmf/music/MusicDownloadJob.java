package com.spmf.music;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "music_download_jobs")
public class MusicDownloadJob extends PanacheEntityBase {

    public enum Status {
        QUEUED,
        DOWNLOADING,
        PROCESSING,
        COMPLETED,
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 1000)
    public String url;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    public Status status;

    @Column(length = 500)
    public String title;

    @Column(length = 500)
    public String artist;

    @Column(length = 500)
    public String album;

    @Column(length = 100)
    public String youtubeId;

    @Column(length = 1000)
    public String thumbnailUrl;

    public Integer durationSeconds;

    @Column(length = 2000)
    public String outputPath;

    @Column(length = 2000)
    public String thumbnailPath;

    @Column(length = 4000)
    public String errorMessage;

    @Column(nullable = false)
    public LocalDateTime createdAt;

    public LocalDateTime startedAt;

    public LocalDateTime completedAt;
}