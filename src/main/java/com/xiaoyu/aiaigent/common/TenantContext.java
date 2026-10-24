package com.xiaoyu.aiaigent.common;

/**
 * 多租户上下文：ThreadLocal 承载当前请求的 user/work。
 * 注意：ThreadLocal 不跨线程传播，异步场景需在异步前显式取出 tenant 并传递。
 */
public final class TenantContext {

    private TenantContext() {
    }

    public record Tenant(long userId, long workId) {
    }

    private static final ThreadLocal<Tenant> HOLDER = new ThreadLocal<>();

    public static void set(Tenant tenant) {
        HOLDER.set(tenant);
    }

    public static Tenant get() {
        Tenant tenant = HOLDER.get();
        if (tenant == null) {
            throw new IllegalStateException("TenantContext 未初始化");
        }
        return tenant;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
