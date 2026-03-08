package com.geekersjoel237.koracore.infrastructure.persistence;

import com.geekersjoel237.koracore.domain.model.Customer;
import com.geekersjoel237.koracore.domain.model.User;
import com.geekersjoel237.koracore.domain.port.CustomerRepository;
import com.geekersjoel237.koracore.domain.vo.Id;
import com.geekersjoel237.koracore.domain.vo.PhoneNumber;
import com.geekersjoel237.koracore.infrastructure.persistence.entity.CustomerEntity;
import com.geekersjoel237.koracore.infrastructure.persistence.entity.UserEntity;
import com.geekersjoel237.koracore.infrastructure.persistence.repository.JpaCustomerRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class PostgresCustomerRepository implements CustomerRepository {

    private final JpaCustomerRepository jpaRepository;

    public PostgresCustomerRepository(JpaCustomerRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(Customer customer) {
        jpaRepository.save(toEntity(customer));
    }

    @Override
    public Optional<Customer> findById(Id id) {
        return jpaRepository.findById(id.value()).map(this::toDomain);
    }

    @Override
    public Optional<Customer> findByEmail(String email) {
        return jpaRepository.findByUserEmail(email).map(this::toDomain);
    }

    @Override
    public Optional<Customer> findByPhoneNumber(String fullNumber) {
        return jpaRepository.findByFullPhoneNumber(fullNumber).map(this::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpaRepository.existsByUserEmail(email);
    }

    private CustomerEntity toEntity(Customer customer) {
        Customer.Snapshot snap = customer.snapshot();
        User.Snapshot userSnap = snap.user();

        UserEntity userEntity = UserEntity.builder()
                .fullName(userSnap.fullName())
                .email(userSnap.email())
                .role(userSnap.role())
                .status(userSnap.status())
                .build();
        userEntity.setId(userSnap.id().value());

        CustomerEntity entity = CustomerEntity.builder()
                .user(userEntity)
                .phonePrefix(snap.phoneNumber().prefix())
                .phoneNumber(snap.phoneNumber().number())
                .hashedPin(snap.hashedPin())
                .build();
        entity.setId(userSnap.id().value());
        return entity;
    }

    private Customer toDomain(CustomerEntity entity) {
        UserEntity u = entity.getUser();
        User.Snapshot userSnap = new User.Snapshot(
                new Id(u.getId()),
                u.getFullName(),
                u.getEmail(),
                u.getRole(),
                u.getStatus()
        );
        Customer.Snapshot snap = new Customer.Snapshot(
                new Id(entity.getId()),
                userSnap,
                PhoneNumber.of(entity.getPhonePrefix(), entity.getPhoneNumber()),
                entity.getHashedPin()
        );
        return Customer.createFromSnapshot(snap);
    }
}