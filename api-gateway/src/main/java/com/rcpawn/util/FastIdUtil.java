package com.rcpawn.util;

import java.util.concurrent.ThreadLocalRandom;

public class FastIdUtil {
    // 生成无横杠的 UUID (高性能版)
    public static String fastUUID() {
        return new java.util.UUID(ThreadLocalRandom.current().nextLong(), ThreadLocalRandom.current().nextLong())
                .toString().replace("-", "");
    }
}