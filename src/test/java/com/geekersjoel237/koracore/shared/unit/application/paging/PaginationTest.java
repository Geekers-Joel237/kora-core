package com.geekersjoel237.koracore.shared.unit.application.paging;

import com.geekersjoel237.koracore.shared.application.cqrs.paging.Pagination;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaginationTest {

    @Test
    void should_default_the_limit_when_none_is_given() {
        assertThat(Pagination.of(null).limit()).isEqualTo(Pagination.DEFAULT_LIMIT);
        assertThat(Pagination.firstPage().limit()).isEqualTo(Pagination.DEFAULT_LIMIT);
    }

    @Test
    void should_keep_the_limit_it_was_given() {
        assertThat(Pagination.of(5).limit()).isEqualTo(5);
    }

    @Test
    void should_accept_the_ceiling_itself() {
        assertThat(Pagination.of(Pagination.MAX_LIMIT).limit()).isEqualTo(Pagination.MAX_LIMIT);
    }

    @Test
    void should_refuse_a_limit_over_the_ceiling() {
        assertThatThrownBy(() -> Pagination.of(Pagination.MAX_LIMIT + 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_refuse_an_empty_or_negative_limit() {
        assertThatThrownBy(() -> Pagination.of(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Pagination.of(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    // One row past the limit tells whether a next page exists, without counting the set.
    @Test
    void should_fetch_one_row_past_the_limit() {
        assertThat(Pagination.of(20).fetchSize()).isEqualTo(21);
    }
}
