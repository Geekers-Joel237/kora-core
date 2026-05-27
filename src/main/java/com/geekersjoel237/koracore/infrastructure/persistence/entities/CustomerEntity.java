package com.geekersjoel237.koracore.infrastructure.persistence.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "customers", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"phone_prefix", "phone_number"})
})
@Getter
@Setter
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerEntity extends VersionedEntity {

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
