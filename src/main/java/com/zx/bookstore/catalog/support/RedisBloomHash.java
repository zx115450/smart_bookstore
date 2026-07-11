package com.zx.bookstore.catalog.support;

/**
 * 布隆过滤器哈希：将 {@code bookId} 映射为 Redis Bitmap 的 bit 偏移量。
 * <p>
 * 使用双重哈希从 2 个基础哈希派生 k 个 offset：{@code offset(i) = (h1 + i·h2) mod m}。
 * 底层为 Redis {@code SETBIT}/{@code GETBIT}，无需 RedisBloom 模块。
 */
public final class RedisBloomHash {

    /** 哈希种子，与 bookId 异或后传入 mix64，降低连续 id 的 bit 聚集。 */
    private static final long SEED_1 = 0x9E3779B97F4A7C15L;
    private static final long SEED_2 = 0xBF58476D1CE4E5B9L;

    private RedisBloomHash() {
    }

    /**
     * 计算第 {@code index} 个哈希函数对应的 bit 偏移（0 ≤ offset &lt; bitSize）。
     *
     * @param value   待哈希值（本项目为 bookId）
     * @param index   哈希序号，范围 [0, k-1]
     * @param bitSize 位数组长度 m
     */
    public static long offset(long value, int index, long bitSize) {
        long h1 = mix64(value ^ SEED_1);
        long h2 = mix64(value ^ SEED_2 ^ ((long) index << 32));
        // 双重哈希：用 index 派生 k 个独立 offset，无需维护 k 个哈希函数
        long combined = h1 + (long) index * h2;
        if (bitSize <= 0) {
            return 0;
        }
        // 无符号取模，保证 offset 非负
        return Long.remainderUnsigned(combined, bitSize);
    }

    /**
     * 64 位终混（类似 MurmurHash3 finalizer）：异或、移位、乘法打散位模式，使 offset 分布更均匀。
     */
    private static long mix64(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }
}
