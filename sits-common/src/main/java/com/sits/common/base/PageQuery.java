package com.sits.common.base;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;

/**
 * 通用分页查询请求参数，用于接收前端传递的分页信息。
 *
 * <p>默认第 1 页、每页 20 条，单页最大限制为 100 条。可通过 {@link #toPage()} 方法转换为 MyBatis-Plus 分页对象。
 */
public class PageQuery {

    /** 页码，默认为 1 */
    private int pageNum = 1;
    /** 每页条数，默认为 20，最大限制 100 */
    private int pageSize = 20;

    public int getPageNum() { return pageNum; }
    public void setPageNum(int pageNum) { this.pageNum = pageNum; }

    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = Math.min(pageSize, 100); }

    /**
     * 将当前分页参数转换为 MyBatis-Plus 分页对象。
     *
     * @param <T> 实体类型
     * @return MyBatis-Plus Page 对象
     */
    public <T> Page<T> toPage() {
        return new Page<>(pageNum, pageSize);
    }
}
