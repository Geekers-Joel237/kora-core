package com.geekersjoel237.koracore.infrastructure.persistence.entity;

import com.geekersjoel237.koracore.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "customers", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"phone_prefix", "phone_number"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerEntity extends BaseEntity {

    @OneToOne(cascade = CascadeType.ALL)
    @MapsId
    @JoinColumn(name = "id")
    private UserEntity user;

    @Column(name = "phone_prefix", nullable = false)
    private String phonePrefix;

    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;

    @Column(name = "hashed_pin", nullable = false)
    private String hashedPin;
}
