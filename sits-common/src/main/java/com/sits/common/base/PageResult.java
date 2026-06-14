package com.sits.common.base;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

/**
 * Generic page result.
 */
public class PageResult<T> {

    private long total;
    private long pages;
    private List<T> records;

    public PageResult() {}

    public PageResult(long total, long pages, List<T> records) {
        this.total = total;
        this.pages = pages;
        this.records = records;
    }

    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(page.getTotal(), page.getPages(), page.getRecords());
    }

    public long getTotal() { return total; }
    public long getPages() { return pages; }
    public List<T> getRecords() { return records; }
}
