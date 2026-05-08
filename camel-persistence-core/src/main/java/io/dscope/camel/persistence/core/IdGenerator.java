/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.dscope.camel.persistence.core;

import java.security.SecureRandom;
import java.util.UUID;

public final class IdGenerator {

    private static final char[] CROCKFORD_BASE32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Object LOCK = new Object();
    private static final byte[] LAST_RANDOMNESS = new byte[10];

    private static long lastTimestamp = -1L;

    private IdGenerator() {
    }

    public static String newUuid() {
        return UUID.randomUUID().toString();
    }

    public static String newUlid() {
        long timestamp;
        byte[] randomness = new byte[10];
        synchronized (LOCK) {
            long now = System.currentTimeMillis();
            if (now > lastTimestamp) {
                lastTimestamp = now;
                RANDOM.nextBytes(LAST_RANDOMNESS);
            } else if (!increment(LAST_RANDOMNESS)) {
                do {
                    now = System.currentTimeMillis();
                } while (now <= lastTimestamp);
                lastTimestamp = now;
                RANDOM.nextBytes(LAST_RANDOMNESS);
            }
            timestamp = lastTimestamp;
            System.arraycopy(LAST_RANDOMNESS, 0, randomness, 0, LAST_RANDOMNESS.length);
        }
        return encode(timestamp, randomness);
    }

    private static String encode(long timestamp, byte[] randomness) {
        char[] value = new char[26];
        value[0] = CROCKFORD_BASE32[(int) ((timestamp >>> 45) & 0x07)];
        value[1] = CROCKFORD_BASE32[(int) ((timestamp >>> 40) & 0x1F)];
        value[2] = CROCKFORD_BASE32[(int) ((timestamp >>> 35) & 0x1F)];
        value[3] = CROCKFORD_BASE32[(int) ((timestamp >>> 30) & 0x1F)];
        value[4] = CROCKFORD_BASE32[(int) ((timestamp >>> 25) & 0x1F)];
        value[5] = CROCKFORD_BASE32[(int) ((timestamp >>> 20) & 0x1F)];
        value[6] = CROCKFORD_BASE32[(int) ((timestamp >>> 15) & 0x1F)];
        value[7] = CROCKFORD_BASE32[(int) ((timestamp >>> 10) & 0x1F)];
        value[8] = CROCKFORD_BASE32[(int) ((timestamp >>> 5) & 0x1F)];
        value[9] = CROCKFORD_BASE32[(int) (timestamp & 0x1F)];

        int buffer = 0;
        int bitsLeft = 0;
        int index = 10;
        for (byte chunk : randomness) {
            buffer = (buffer << 8) | (chunk & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                bitsLeft -= 5;
                value[index++] = CROCKFORD_BASE32[(buffer >> bitsLeft) & 0x1F];
            }
        }

        return new String(value);
    }

    private static boolean increment(byte[] randomness) {
        for (int index = randomness.length - 1; index >= 0; index--) {
            int next = (randomness[index] & 0xFF) + 1;
            randomness[index] = (byte) next;
            if ((next & 0x100) == 0) {
                return true;
            }
        }
        return false;
    }
}