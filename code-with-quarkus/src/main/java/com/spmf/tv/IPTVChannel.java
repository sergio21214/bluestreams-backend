package com.spmf.tv;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;

@Entity
public class IPTVChannel extends PanacheEntity {

    public Long providerId;

    public String providerName;

    public String providerType;

    public String name;

    @Column(length = 4096)
    public String streamUrl;

    public String providerChannelId;

    public String tvgId;

    public String groupName;

    public String groupTitle;

    @Column(length = 4096)
    public String logo;

    public boolean enabled = true;

}