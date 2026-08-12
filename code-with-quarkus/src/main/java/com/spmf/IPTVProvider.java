package com.spmf;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;

@Entity
public class IPTVProvider extends PanacheEntity {

    public String name;

    public String type;

    public String url;

    public String username;

    public String password;

    public boolean enabled=true;

}