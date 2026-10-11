package com.apiweb.common;

import lombok.Data;

import java.util.List;

/**
 * 统一分页响应结构。
 *
 * <p>用于标准化所有列表接口的分页返回，替代此前各 Controller 中
 * 不一致的返回方式（有的返回 {@code List} 丢弃 total，有的返回 {@code Map{records,total}}）。
 *
 * <p>字段命名与前端既有约定对齐：{@code records}（当前页数据）、{@code total}（总条数）、
 * {@code page}（当前页码，从 1 开始）、{@code size}（每页条数）。
 *
 * <h3>使用示例</h3>
 * <pre>
 * &#64;GetMapping
 * public Result&lt;PageResult&lt;UserVO&gt;&gt; list(&#64;RequestParam int page, &#64;RequestParam int size) {
 *     Page&lt;UserEntity&gt; p = userService.page(page, size);
 *     return Result.ok(PageResult.of(p, UserVO::from));
 * }
 * </pre>
 *
 * @param <T> 列表元素类型（推荐用 VO，而非 Entity）
 */
@Data
public class PageResult<T> {

    /** 当前页数据 */
    private List<T> records;
    /** 总条数 */
    private long total;
    /** 当前页码（从 1 开始） */
    private long page;
    /** 每页条数 */
    private long size;

    public PageResult() {
    }

    public PageResult(List<T> records, long total, long page, long size) {
        this.records = records;
        this.total = total;
        this.page = page;
        this.size = size;
    }

    /**
     * 从 MyBatis-Plus 的 {@code Page<T>} 构建。
     *
     * @param page MyBatis-Plus 分页对象
     * @param <T>  元素类型
     * @return 统一分页结构（records/total/page/size 均已填充）
     */
    public static <T> PageResult<T> of(com.baomidou.mybatisplus.extension.plugins.pagination.Page<T> page) {
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 从已转换的数据列表手动构建（适用于需要 DTO/VO 转换的场景）。
     *
     * @param records 当前页数据（已转换）
     * @param total   总条数
     * @param page    当前页码
     * @param size    每页条数
     * @param <T>     元素类型
     * @return 统一分页结构
     */
    public static <T> PageResult<T> of(List<T> records, long total, long page, long size) {
        return new PageResult<>(records, total, page, size);
    }
}
