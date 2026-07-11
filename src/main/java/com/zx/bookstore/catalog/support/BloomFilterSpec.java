package com.zx.bookstore.catalog.support;

/**
 * 布隆过滤器参数规格：根据预期元素数 {@code n} 与目标误判率 {@code p}，
 * 计算 Redis Bitmap 位数组长度 {@code m} 与哈希函数个数 {@code k}。
 * <p>
 * 经典公式：{@code m = -n·ln(p) / (ln2)²}，{@code k = (m/n)·ln2}。
 * 详见 {@code docs/Redis布隆过滤器Bitmap实现原理.md}。
 */
public final class BloomFilterSpec {

    /** 位数组长度 m（Redis SETBIT/GETBIT 的 offset 范围 [0, m-1]）。 */
    private final long bitSize;

    /** 哈希函数个数 k（每个元素需置 k 个 bit）。 */
    private final int hashFunctions;

    private BloomFilterSpec(long bitSize, int hashFunctions) {
        this.bitSize = bitSize;
        this.hashFunctions = hashFunctions;
    }

    /**
     * 由业务规模与可接受误判率推导 m、k。
     *
     * @param expectedElements   预期插入元素数 n（如图书总量）
     * @param falsePositiveRate  目标假阳性率 p（如 0.01 表示 1% 误判）
     */
    public static BloomFilterSpec of(long expectedElements, double falsePositiveRate) {
        long n = Math.max(1, expectedElements);
        // 非法 p 回退到 1%，避免 m/k 计算异常
        double p = falsePositiveRate <= 0 || falsePositiveRate >= 1 ? 0.01 : falsePositiveRate;
        // m = ceil(-n * ln(p) / (ln 2)²)
        long m = (long) Math.ceil(-n * Math.log(p) / (Math.log(2) * Math.log(2)));
        m = Math.max(m, 64);
        // k = round((m/n) * ln 2)，限制在 [1,16] 防止 k 过大拖慢每次 GETBIT
        int k = (int) Math.round((double) m / n * Math.log(2));
        k = Math.max(1, Math.min(k, 16));
        return new BloomFilterSpec(m, k);
    }

    public long bitSize() {
        return bitSize;
    }

    public int hashFunctions() {
        return hashFunctions;
    }
}
