package com.spmf.dto;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

@Entity
@Table(name = "profiles")
public class Profile extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "user_id")
    public Long userId;

    @Column(name = "profile_name")
    public String profileName;

    @Column(name = "avatar_url")
    public String avatarUrl;

    @Column(name = "is_kids_profile")
    public Boolean isKidsProfile;
}