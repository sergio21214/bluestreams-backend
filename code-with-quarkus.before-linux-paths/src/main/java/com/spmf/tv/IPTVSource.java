package com.spmf.tv;


import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;

import java.time.LocalDateTime;

@Entity
public class IPTVSource extends PanacheEntity {

    public String name;

    public String type;

    public String url;

    public String filename;

    public Boolean enabled = true;

    public Integer refreshIntervalMinutes = 60;

    public LocalDateTime lastRefresh;

    public boolean localFile = false;

    public String filePath;

    public LocalDateTime lastImported;

}