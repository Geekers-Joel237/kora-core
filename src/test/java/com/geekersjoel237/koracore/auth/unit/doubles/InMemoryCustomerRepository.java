package com.geekersjoel237.koracore.auth.unit.doubles;

import com.geekersjoel237.koracore.auth.domain.model.Customer;
import com.geekersjoel237.koracore.auth.ports.out.repository.CustomerRepository;
import com.geekersjoel237.koracore.shared.domain.vo.Id;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryCustomerRepository implements CustomerRepository {

    private final Map<String, Customer> store = new HashMap<>();

    @Override
    public void save(Customer customer) {
        store.put(customer.snapshot().customerId().value(), customer);
    }

    @Override
    public Optional<Customer> findById(Id id) {
        return Optional.ofNullable(store.get(id.value()));
    }

    @Override
    public Optional<Customer> findByEmail(String email) {
        return store.values().stream()
                .filter(c -> c.snapshot().user().email().equals(email))
                .findFirst();
    }

    @Override
    public Optional<Customer> findByPhoneNumber(String fullNumber) {
        return store.values().stream()
                .filter(c -> c.snapshot().phoneNumber().fullNumber().equals(fullNumber))
                .findFirst();
    }

    @Override
    public boolean existsByEmail(String email) {
        return store.values().stream()
                .anyMatch(c -> c.snapshot().user().email().equals(email));
    }

    public void reset() {
        store.clear();
    }
}