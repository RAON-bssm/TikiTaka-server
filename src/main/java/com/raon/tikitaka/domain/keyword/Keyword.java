package com.raon.tikitaka.domain.keyword;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "keyword")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Keyword {

    @Id
    @Column(name = "keyword", nullable = false)
    private String keyword;

    @Column(name = "type", nullable = false)
    private String type;

}