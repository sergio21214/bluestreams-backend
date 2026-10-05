package com.spmf.music;

public class MusicDownloadResponse {

    public Long id;

    public String status;

    public String title;

    public String artist;

    public String album;

    public String youtubeId;

    public String errorMessage;

    public MusicDownloadResponse() {
    }

    public MusicDownloadResponse(
            MusicDownloadJob job
    ) {

        this.id =
                job.id;

        this.status =
                job.status != null
                        ? job.status.name()
                        : null;

        this.title =
                job.title;

        this.artist =
                job.artist;

        this.album =
                job.album;

        this.youtubeId =
                job.youtubeId;

        this.errorMessage =
                job.errorMessage;
    }
}