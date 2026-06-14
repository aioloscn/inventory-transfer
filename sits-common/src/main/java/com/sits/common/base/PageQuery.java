package com.sits.common.base;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;

/**
 * Generic page query request.
 */
public class PageQuery {

    private int pageNum = 1;
    private int pageSize = 20;

    public int getPageNum() { return pageNum; }
    public void setPageNum(int pageNum) { this.pageNum = pageNum; }

    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = Math.min(pageSize, 100); }

    public <T> Page<T> toPage() {
        return new Page<>(pageNum, pageSize);
    }
}

