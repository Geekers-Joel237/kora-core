package com.geekersjoel237.koracore.shared.unit.application.paging;

import com.geekersjoel237.koracore.shared.application.cqrs.paging.PageResult;
import com.geekersjoel237.koracore.shared.application.cqrs.paging.Pagination;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResultTest {

    private static final Pagination FIRST_OF_TEN = new Pagination(0, 10);

    @Test
    void should_take_its_metadata_from_the_pagination_it_answered() {
        PageResult<String> page = PageResult.of(List.of("a", "b"), new Pagination(2, 10), 25);

        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(10);
        assertThat(page.totalElements()).isEqualTo(25);
    }

    @Test
    void should_count_the_partial_last_page() {
        assertThat(PageResult.of(List.of(), FIRST_OF_TEN, 25).totalPages()).isEqualTo(3);
        assertThat(PageResult.of(List.of(), FIRST_OF_TEN, 30).totalPages()).isEqualTo(3);
        assertThat(PageResult.of(List.of(), FIRST_OF_TEN, 0).totalPages()).isZero();
    }

    @Test
    void should_announce_a_next_page_only_while_one_remains() {
        assertThat(PageResult.of(List.of(), new Pagination(0, 10), 25).hasNext()).isTrue();
        assertThat(PageResult.of(List.of(), new Pagination(2, 10), 25).hasNext()).isFalse();
    }

    /**
     * An empty page is not evidence of an empty set — it may be past the end. The count
     * comes from the query, never from the content.
     */
    @Test
    void should_keep_the_total_of_an_empty_page_past_the_end() {
        PageResult<String> beyond = PageResult.of(List.of(), new Pagination(9, 10), 25);

        assertThat(beyond.content()).isEmpty();
        assertThat(beyond.totalElements()).isEqualTo(25);
        assertThat(beyond.hasNext()).isFalse();
    }

    @Test
    void should_carry_the_metadata_across_a_mapping() {
        PageResult<Integer> lengths = PageResult
                .of(List.of("aa", "bbb"), new Pagination(1, 10), 25)
                .map(String::length);

        assertThat(lengths.content()).containsExactly(2, 3);
        assertThat(lengths.page()).isEqualTo(1);
        assertThat(lengths.size()).isEqualTo(10);
        assertThat(lengths.totalElements()).isEqualTo(25);
    }

    @Test
    void should_build_an_empty_page_for_a_pagination() {
        PageResult<String> empty = PageResult.empty(new Pagination(1, 10));

        assertThat(empty.content()).isEmpty();
        assertThat(empty.totalElements()).isZero();
        assertThat(empty.page()).isEqualTo(1);
    }
}
