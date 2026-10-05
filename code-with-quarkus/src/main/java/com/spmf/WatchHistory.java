package com.spmf;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "watch_history")
public class WatchHistory extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "profile_id")
    public Long profileId;

    @Column(name = "media_id")
    public Long mediaId;

    @Column(name = "position_seconds")
    public Integer positionSeconds;

    public Boolean completed;

    @Column(name = "last_watched")
    public LocalDateTime lastWatched;
}