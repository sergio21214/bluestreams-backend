package com.spmf;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User extends PanacheEntity {

    @Column(unique = true)
    public String username;

    @Column(unique = true)
    public String email;

    @Column(name = "password_hash")
    public String passwordHash;

    public String role;

    public Boolean enabled;
}