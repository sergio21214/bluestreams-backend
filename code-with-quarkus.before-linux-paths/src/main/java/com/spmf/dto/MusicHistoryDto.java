package com.spmf.dto;

import java.time.LocalDateTime;

public class MusicHistoryDto {

    public Long id;

    public Long mediaId;

    public String title;

    public String artist;

    public String album;

    public String poster;

    public int playCount;

    public long positionSeconds;

    public LocalDateTime lastPlayed;

}