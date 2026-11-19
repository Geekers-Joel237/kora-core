package com.geekersjoel237.koracore.auth.adapters.out.persistence;

import com.geekersjoel237.koracore.auth.adapters.out.persistence.entities.CustomerEntity;
import com.geekersjoel237.koracore.auth.adapters.out.persistence.mapping.CustomerPersistenceMapper;
import com.geekersjoel237.koracore.auth.adapters.out.persistence.repository.SpringDataCustomerRepository;
import com.geekersjoel237.koracore.auth.domain.model.Customer;
import com.geekersjoel237.koracore.auth.ports.out.repository.CustomerRepository;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JpaCustomerRepository implements CustomerRepository {

    private final SpringDataCustomerRepository jpaRepository;
    private final CustomerPersistenceMapper mapper;

    public JpaCustomerRepository(SpringDataCustomerRepository jpaRepository, CustomerPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public void save(Customer customer) {
        jpaRepository.save(mapper.toEntity(customer.snapshot()));
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

    private Customer toDomain(CustomerEntity entity) {
        return Customer.createFromSnapshot(mapper.toSnapshot(entity));
    }
}
