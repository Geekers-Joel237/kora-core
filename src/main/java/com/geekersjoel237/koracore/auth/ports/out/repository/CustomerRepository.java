package com.geekersjoel237.koracore.auth.ports.out.repository;

import com.geekersjoel237.koracore.auth.domain.model.Customer;
import com.geekersjoel237.koracore.shared.domain.vo.Id;

import java.util.Optional;

public interface CustomerRepository {
    void save(Customer customer);
    Optional<Customer> findById(Id id);
    Optional<Customer> findByEmail(String email);
    Optional<Customer> findByPhoneNumber(String fullNumber);
    boolean existsByEmail(String email);
}
