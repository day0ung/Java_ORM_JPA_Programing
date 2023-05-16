package com.inf.jpabasic.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user")
public class UserEntity {

    @Id // primary Key
    private String email;

    private String name;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public UserEntity(String email, String name, LocalDateTime createdAt,
                      LocalDateTime updatedAt) {
        this.email = email;
        this.name = name;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    //JPA 규약으로 기본 생성자를 제공해야함
    public UserEntity(){

    }

    public void changeName(String newName){
        this.name = newName;
        updateDateTime();
    }

    private void updateDateTime(){
        this.updatedAt = LocalDateTime.now();
    }
}

