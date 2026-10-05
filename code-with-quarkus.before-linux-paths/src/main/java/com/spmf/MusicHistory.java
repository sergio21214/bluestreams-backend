package com.spmf;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
public class MusicHistory extends PanacheEntity {

    public Long profileId;

    public Long mediaId;

    public int playCount;

    public long lastPositionSeconds;

    public LocalDateTime lastPlayed;
    
    public Boolean counted = false;
}