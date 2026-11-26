package com.converter.gateway.ratelimit;

import com.converter.gateway.config.GatewayProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Atomic Redis Lua ZSET sliding-window rate limiter with bounded local fallback
 * if Redis is temporarily unavailable.
 */
@Component
public class SlidingWindowRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(SlidingWindowRateLimiter.class);
    private static final int MAX_FALLBACK_KEYS = 10_000;

    private static final DefaultRedisScript<Long> SLIDING_WINDOW_SCRIPT = new DefaultRedisScript<>("""
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local windowMs = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            local member = ARGV[4]

            redis.call('ZREMRANGEBYSCORE', key, 0, now - windowMs)
            local current = redis.call('ZCARD', key)
            if current >= limit then
                local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
                local retryMs = windowMs
                if #oldest >= 2 then
                    retryMs = math.max(1000, windowMs - (now - tonumber(oldest[2])))
                end
                return -retryMs
            end

            redis.call('ZADD', key, now, member)
            redis.call('PEXPIRE', key, windowMs)
            return limit - current - 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final GatewayProperties properties;
    private final Clock clock;
    private final ConcurrentHashMap<String, Deque<Long>> fallbackBuckets = new ConcurrentHashMap<>();

    public SlidingWindowRateLimiter(
            StringRedisTemplate redisTemplate,
            GatewayProperties properties,
            Clock clock
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    public record RateLimitDecision(boolean allowed, long retryAfterSeconds, long remaining) {}

    public RateLimitDecision check(String bucket, String clientKey, int limit) {
        if (!properties.rateLimit().enabled()) {
            return new RateLimitDecision(true, 0, limit);
        }
        long nowMs = clock.millis();
        long windowMs = properties.rateLimit().windowSeconds() * 1000L;
        String redisKey = "rl:" + bucket + ":" + clientKey;
        String member = nowMs + "-" + UUID.randomUUID();

        try {
            Long res = redisTemplate.execute(
                    SLIDING_WINDOW_SCRIPT,
                    List.of(redisKey),
                    String.valueOf(nowMs),
                    String.valueOf(windowMs),
                    String.valueOf(limit),
                    member
            );
            if (res != null) {
                if (res >= 0) {
                    return new RateLimitDecision(true, 0, res);
                }
                long retrySec = Math.max(1L, (-res + 999L) / 1000L);
                return new RateLimitDecision(false, retrySec, 0);
            }
        } catch (Exception ex) {
            log.warn("Redis rate limiter unavailable, using bounded local fallback: {}", ex.getMessage());
        }
        return checkLocalFallback(redisKey, nowMs, windowMs, limit);
    }

    private RateLimitDecision checkLocalFallback(String key, long nowMs, long windowMs, int limit) {
        if (fallbackBuckets.size() > MAX_FALLBACK_KEYS) {
            fallbackBuckets.clear();
        }
        Deque<Long> deque = fallbackBuckets.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            long cutoff = nowMs - windowMs;
            while (!deque.isEmpty() && deque.peekFirst() <= cutoff) {
                deque.pollFirst();
            }
            if (deque.size() >= limit) {
                long oldest = deque.peekFirst() != null ? deque.peekFirst() : nowMs;
                long retryMs = Math.max(1000L, windowMs - (nowMs - oldest));
                return new RateLimitDecision(false, (retryMs + 999L) / 1000L, 0);
            }
            deque.addLast(nowMs);
            return new RateLimitDecision(true, 0, limit - deque.size());
        }
    }
}
