package com.geekersjoel237.koracore.shared.application.cqrs.paging;

import com.geekersjoel237.koracore.shared.application.cqrs.Query;


public interface PagedQuery<T> extends Query<PageResult<T>> {

    Pagination pagination();
}
