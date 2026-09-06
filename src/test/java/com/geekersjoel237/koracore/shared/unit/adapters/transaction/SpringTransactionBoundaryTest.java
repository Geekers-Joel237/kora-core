package com.geekersjoel237.koracore.shared.unit.adapters.transaction;

import com.geekersjoel237.koracore.shared.adapters.out.transaction.SpringTransactionBoundary;
import com.geekersjoel237.koracore.shared.unit.doubles.NoopTransactionManager;
import com.geekersjoel237.koracore.shared.unit.doubles.RecordingTransactionManager;
import com.geekersjoel237.koracore.shared.unit.doubles.ThrowingTransactionManager;
import com.geekersjoel237.koracore.shared.application.transaction.ConcurrentUpdateException;
import com.geekersjoel237.koracore.shared.domain.exception.BusinessException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The boundary's two jobs: deciding whether work becomes permanent, and naming its
 * failures in terms the application can act on.
 *
 * <p>Plain JUnit throughout. The transaction manager is a double, so a rollback can be
 * observed as a fact rather than inferred from the absence of rows, and a commit-time
 * failure can be injected where a real one surfaces — no database, no provoked deadlock.
 *
 * <p>The exceptions thrown here are the kernel's own. A test in {@code shared} that
 * reached for {@code InsufficientFundsException} would make the kernel name a module
 * to describe a rule that has nothing to do with payments.
 */
class SpringTransactionBoundaryTest {

    @Test
    void returns_the_value_produced_inside_the_boundary() {
        var boundary = new SpringTransactionBoundary(new NoopTransactionManager());

        assertThat(boundary.execute(() -> "settled")).isEqualTo("settled");
    }

    /**
     * The contract a use case relies on to abort. The port hands out no transaction
     * status, so throwing is the only mechanism available to one — and it works because
     * of a Spring default, which is exactly the kind of thing that stays true until it
     * does not. Pinned here rather than assumed.
     */
    @Nested
    class WhatBecomesPermanent {

        @Test
        void commits_when_the_work_returns() {
            var manager = new RecordingTransactionManager();

            new SpringTransactionBoundary(manager).execute(() -> "done");

            assertThat(manager.committed()).isEqualTo(1);
            assertThat(manager.rolledBack()).isZero();
        }

        @Test
        void rolls_back_when_the_domain_refuses() {
            var manager = new RecordingTransactionManager();
            var boundary = new SpringTransactionBoundary(manager);

            assertThatThrownBy(() -> boundary.<String>execute(() -> {
                throw new BusinessException("balance is 0, required 5000");
            })).isInstanceOf(BusinessException.class);

            assertThat(manager.rolledBack())
                    .describedAs("a business failure undoes the work it had already written")
                    .isEqualTo(1);
            assertThat(manager.committed()).isZero();
        }

        @Test
        void rolls_back_on_any_other_runtime_failure() {
            var manager = new RecordingTransactionManager();
            var boundary = new SpringTransactionBoundary(manager);

            assertThatThrownBy(() -> boundary.<String>execute(() -> {
                throw new IllegalStateException("a bug, not a rule");
            })).isInstanceOf(IllegalStateException.class);

            assertThat(manager.rolledBack())
                    .describedAs("a bug is not a reason to commit half a transfer")
                    .isEqualTo(1);
            assertThat(manager.committed()).isZero();
        }

        @Test
        void rolls_back_on_an_error() {
            var manager = new RecordingTransactionManager();
            var boundary = new SpringTransactionBoundary(manager);

            assertThatThrownBy(() -> boundary.<String>execute(() -> {
                throw new StackOverflowError("deep recursion");
            })).isInstanceOf(StackOverflowError.class);

            assertThat(manager.rolledBack()).isEqualTo(1);
            assertThat(manager.committed()).isZero();
        }

        @Test
        void opens_one_transaction_per_call() {
            var manager = new RecordingTransactionManager();
            var boundary = new SpringTransactionBoundary(manager);

            boundary.execute(() -> "first");
            boundary.execute(() -> "second");

            assertThat(manager.started()).isEqualTo(2);
            assertThat(manager.committed()).isEqualTo(2);
        }
    }

    /**
     * Two unrelated Spring types mean the same thing to a caller: the transaction lost
     * a race and may be replayed. The application retries on one type, so both have to
     * arrive as that type.
     */
    @Nested
    class WhatTheFailuresAreCalled {

        @Test
        void translates_a_stale_version_into_a_concurrent_update() {
            var boundary = new SpringTransactionBoundary(new ThrowingTransactionManager(
                    new ObjectOptimisticLockingFailureException("stale version", null)));

            assertThatThrownBy(() -> boundary.execute(() -> "unreachable"))
                    .isInstanceOf(ConcurrentUpdateException.class)
                    .hasCauseInstanceOf(ObjectOptimisticLockingFailureException.class);
        }

        /**
         * SQLSTATE 40P01, the deadlock Postgres resolves by killing one transaction. It
         * is a <em>pessimistic</em> failure, so a retry loop that only knows about
         * optimistic clashes misses it and it leaves as a 500.
         */
        @Test
        void translates_a_deadlock_into_a_concurrent_update() {
            var boundary = new SpringTransactionBoundary(new ThrowingTransactionManager(
                    new CannotAcquireLockException("deadlock detected")));

            assertThatThrownBy(() -> boundary.execute(() -> "unreachable"))
                    .isInstanceOf(ConcurrentUpdateException.class)
                    .hasCauseInstanceOf(CannotAcquireLockException.class);
        }

        @Test
        void lets_a_business_failure_through_untouched() {
            var boundary = new SpringTransactionBoundary(new NoopTransactionManager());

            assertThatThrownBy(() -> boundary.<String>execute(() -> {
                throw new BusinessException("balance is 0, required 5000");
            }))
                    .describedAs("only concurrency failures are translated")
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("balance is 0, required 5000");
        }
    }
}
