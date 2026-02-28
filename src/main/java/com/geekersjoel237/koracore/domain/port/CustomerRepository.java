package com.geekersjoel237.koracore.domain.port;

import com.geekersjoel237.koracore.domain.model.Customer;
import com.geekersjoel237.koracore.domain.vo.Id;

import java.util.Optional;

public interface CustomerRepository {
    void save(Customer customer);
    Optional<Customer> findById(Id id);
    Optional<Customer> findByEmail(String email);
    Optional<Customer> findByPhoneNumber(String fullNumber);
    boolean existsByEmail(String email);
}