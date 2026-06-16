package com.sits.common.base;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

/**
 * 通用分页查询结果，封装分页返回数据。
 *
 * @param <T> 记录数据类型
 */
public class PageResult<T> {

    /** 总记录数 */
    private long total;
    /** 总页数 */
    private long pages;
    /** 当前页记录列表 */
    private List<T> records;

    /**
     * 空构造方法，用于序列化框架。
     */
    public PageResult() {}

    /**
     * 通过总数、页数和记录列表构造分页结果。
     *
     * @param total   总记录数
     * @param pages   总页数
     * @param records 当前页记录列表
     */
    public PageResult(long total, long pages, List<T> records) {
        this.total = total;
        this.pages = pages;
        this.records = records;
    }

    /**
     * 从 MyBatis-Plus 分页对象构建 PageResult。
     *
     * @param page MyBatis-Plus 分页结果
     * @param <T>  记录数据类型
     * @return 分页结果对象
     */
    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(page.getTotal(), page.getPages(), page.getRecords());
    }

    public long getTotal() { return total; }
    public long getPages() { return pages; }
    public List<T> getRecords() { return records; }
}
