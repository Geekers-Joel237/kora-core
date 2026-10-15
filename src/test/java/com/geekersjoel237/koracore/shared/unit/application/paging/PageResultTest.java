package com.geekersjoel237.koracore.shared.unit.application.paging;

import com.geekersjoel237.koracore.shared.application.cqrs.paging.PageResult;
import com.geekersjoel237.koracore.shared.application.cqrs.paging.Pagination;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResultTest {

    private static final Pagination TWO = Pagination.of(2);

    @Test
    void should_announce_a_next_page_when_more_than_the_limit_was_fetched() {
        PageResult<String> page = PageResult.fromFetched(List.of("a", "b", "c"), TWO);

        assertThat(page.content()).containsExactly("a", "b");
        assertThat(page.hasNext()).isTrue();
    }

    @Test
    void should_announce_no_next_page_when_the_limit_was_not_exceeded() {
        PageResult<String> exact = PageResult.fromFetched(List.of("a", "b"), TWO);
        PageResult<String> short_ = PageResult.fromFetched(List.of("a"), TWO);

        assertThat(exact.content()).containsExactly("a", "b");
        assertThat(exact.hasNext()).isFalse();
        assertThat(short_.hasNext()).isFalse();
    }

    @Test
    void should_be_empty_without_a_next_page() {
        PageResult<String> page = PageResult.fromFetched(List.of(), TWO);

        assertThat(page.content()).isEmpty();
        assertThat(page.hasNext()).isFalse();
    }

    @Test
    void should_keep_the_next_page_flag_through_a_mapping() {
        PageResult<Integer> lengths = PageResult.fromFetched(List.of("a", "bb", "ccc"), TWO)
                .map(String::length);

        assertThat(lengths.content()).containsExactly(1, 2);
        assertThat(lengths.hasNext()).isTrue();
    }

    @Test
    void should_expose_the_last_item_to_build_the_next_cursor_from() {
        assertThat(PageResult.fromFetched(List.of("a", "b", "c"), TWO).lastItem()).contains("b");
        assertThat(PageResult.fromFetched(List.<String>of(), TWO).lastItem()).isEmpty();
    }
}
