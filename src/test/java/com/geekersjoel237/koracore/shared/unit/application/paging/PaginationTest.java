package com.geekersjoel237.koracore.shared.unit.application.paging;

import com.geekersjoel237.koracore.shared.application.cqrs.paging.Pagination;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The paging rules, in the one place that owns them.
 *
 * <p>Every paginated query in the system inherits what is asserted here, so a new one
 * cannot arrive with its own ceiling or its own idea of a default.
 */
class PaginationTest {

    @Test
    void should_default_to_the_first_page() {
        Pagination pagination = Pagination.firstPage();

        assertThat(pagination.page()).isZero();
        assertThat(pagination.size()).isEqualTo(Pagination.DEFAULT_SIZE);
    }

    @Test
    void should_fill_in_what_a_client_did_not_send() {
        assertThat(Pagination.of(null, null)).isEqualTo(Pagination.firstPage());
        assertThat(Pagination.of(3, null).size()).isEqualTo(Pagination.DEFAULT_SIZE);
        assertThat(Pagination.of(null, 5).page()).isZero();
    }

    @Test
    void should_keep_what_a_client_did_send() {
        Pagination pagination = Pagination.of(2, 50);

        assertThat(pagination.page()).isEqualTo(2);
        assertThat(pagination.size()).isEqualTo(50);
    }

    /**
     * A negative page used to reach Postgres as a negative OFFSET, which is an error
     * there, so a mistyped query string came back a 500 instead of a 400.
     */
    @Test
    void should_refuse_a_negative_page() {
        assertThatThrownBy(() -> new Pagination(-1, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void should_refuse_an_empty_page() {
        assertThatThrownBy(() -> new Pagination(0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 1");
    }

    @Test
    void should_refuse_a_page_over_the_ceiling() {
        assertThatThrownBy(() -> new Pagination(0, Pagination.MAX_SIZE + 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(String.valueOf(Pagination.MAX_SIZE));
    }

    @Test
    void should_accept_the_ceiling_itself() {
        assertThatCode(() -> new Pagination(0, Pagination.MAX_SIZE))
                .doesNotThrowAnyException();
    }

    @Test
    void should_validate_what_arrives_through_the_null_tolerant_factory_too() {
        assertThatThrownBy(() -> Pagination.of(-1, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Pagination.of(null, 101))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_skip_the_pages_before_it() {
        assertThat(Pagination.firstPage().offset()).isZero();
        assertThat(new Pagination(3, 20).offset()).isEqualTo(60);
    }

    /**
     * Page and size are ints, and a deep enough page multiplies past their range. The
     * offset is a long for that reason, and this pins it.
     */
    @Test
    void should_compute_a_deep_offset_without_overflowing() {
        assertThat(new Pagination(Integer.MAX_VALUE, 100).offset())
                .isEqualTo(214_748_364_700L);
    }
}
