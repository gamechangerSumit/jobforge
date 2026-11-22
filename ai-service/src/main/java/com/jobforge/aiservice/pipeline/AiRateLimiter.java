package com.jobforge.aiservice.pipeline;

import com.jobforge.aiservice.error.AiServiceException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/** Per-user rolling minute guard; Redis is preferred and a bounded in-process fallback keeps the service available. */
@Component
public class AiRateLimiter {
    private record LocalBucket(long minute, AtomicInteger count) {}
    private final StringRedisTemplate redis;
    private final ConcurrentHashMap<String,LocalBucket> local = new ConcurrentHashMap<>();

    public AiRateLimiter(StringRedisTemplate redis) { this.redis=redis; }

    public void check(String userId,int limit) {
        long minute=System.currentTimeMillis()/60_000L;
        String redisKey="jobforge:ai:rate:"+userId+":"+minute;
        try {
            Long count=redis.opsForValue().increment(redisKey);
            if(count!=null&&count==1L) redis.expire(redisKey,Duration.ofSeconds(65));
            if(count!=null&&count>limit) throw AiServiceException.rateLimited(60-(System.currentTimeMillis()/1000L)%60);
            return;
        } catch (DataAccessException ex) {
            // Fall through to process-local guard. PostgreSQL quota ledger remains authoritative for credits.
        }
        LocalBucket bucket=local.compute(userId,(key,current) ->
                current==null||current.minute()!=minute?new LocalBucket(minute,new AtomicInteger()):current);
        if(bucket.count().incrementAndGet()>limit)
            throw AiServiceException.rateLimited(60-(System.currentTimeMillis()/1000L)%60);
        if(local.size()>10_000) local.entrySet().removeIf(entry->entry.getValue().minute()<minute-1);
    }
}
