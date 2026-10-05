package com.spmf;


public class Movie {

    public int id;

    public String title;

    public String filename;

    public String streamUrl;

    public String thumbnailUrl;

    public String hlsUrl;

    public String overview;

    public String poster;

    public String genre;

    public String year;

    /*
    public Movie(int id, String title, String filename) {

        this.id = id;

        this.title = title;

        this.filename = filename;

        this.streamUrl =
                "http://192.168.1.15:8080/stream/" + filename;

        this.thumbnailUrl =
                "http://192.168.1.15:8080/thumbnails/"
                        + filename
                        .replace(".mp4", ".jpg")
                        .replace(".mkv", ".jpg");

        String movieName = filename
                .replace(".mp4", "")
                .replace(".mkv", "");

        this.hlsUrl =
                "http://192.168.1.15:8080/hls/"
                        + movieName
                        + "/playlist.m3u8";
    }
    */
    public Movie(int id, String title, String filename) {

        this.id = id;
        this.title = title;
        this.filename = filename;

        this.streamUrl =
                "http://localhost:8080/stream/" + filename;

        this.thumbnailUrl =
                "http://localhost:8080/thumbnails/" +
                        filename
                                .replace(".mp4", ".jpg")
                                .replace(".mkv", ".jpg");

        String movieName = filename
                .replace(".mp4", "")
                .replace(".mkv", "");

        this.hlsUrl =
                "http://localhost:8080/hls/"
                        + movieName
                        + "/playlist.m3u8";
    }
}