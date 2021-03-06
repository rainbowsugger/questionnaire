package com.baoshine.questionnaire.config.cache;

import com.baoshine.questionnaire.config.cache.properties.CacheConfigProperties;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RedisCaffeineCacheManager implements CacheManager {

	private final ConcurrentMap<String, Cache> cacheMap = new ConcurrentHashMap<String, Cache>();

	private final CacheConfigProperties cacheConfigProperties;

	private final RedisTemplate<Object, Object> stringKeyRedisTemplate;

	private final boolean dynamic;

	private final Set<String> cacheNames;

	public RedisCaffeineCacheManager(CacheConfigProperties cacheConfigProperties,
			RedisTemplate<Object, Object> stringKeyRedisTemplate) {
		super();
		this.cacheConfigProperties = cacheConfigProperties;
		this.stringKeyRedisTemplate = stringKeyRedisTemplate;
		this.dynamic = cacheConfigProperties.isDynamic();
		this.cacheNames = cacheConfigProperties.getCacheNames();
	}

	@Override
	public Cache getCache(String name) {
		Cache cache = cacheMap.get(name);
		if (cache != null) {
			return cache;
		}
		if (!dynamic && !cacheNames.contains(name)) {
			return cache;
		}

		cache = new RedisCaffeineCache(name, stringKeyRedisTemplate, caffeineCache(), cacheConfigProperties);
		Cache oldCache = cacheMap.putIfAbsent(name, cache);
		log.debug("create cache instance, the cache name is : {}", name);
		return oldCache == null ? cache : oldCache;
	}

	public com.github.benmanes.caffeine.cache.Cache<Object, Object> caffeineCache() {
		Caffeine<Object, Object> cacheBuilder = Caffeine.newBuilder();
		if (cacheConfigProperties.getCaffeine().getExpireAfterAccess() > 0) {
			cacheBuilder.expireAfterAccess(cacheConfigProperties.getCaffeine().getExpireAfterAccess(),
					TimeUnit.MILLISECONDS);
		}
		if (cacheConfigProperties.getCaffeine().getExpireAfterWrite() > 0) {
			cacheBuilder.expireAfterWrite(cacheConfigProperties.getCaffeine().getExpireAfterWrite(),
					TimeUnit.MILLISECONDS);
		}
		if (cacheConfigProperties.getCaffeine().getInitialCapacity() > 0) {
			cacheBuilder.initialCapacity(cacheConfigProperties.getCaffeine().getInitialCapacity());
		}
		if (cacheConfigProperties.getCaffeine().getMaximumSize() > 0) {
			cacheBuilder.maximumSize(cacheConfigProperties.getCaffeine().getMaximumSize());
		}
		if (cacheConfigProperties.getCaffeine().getRefreshAfterWrite() > 0) {
			cacheBuilder.refreshAfterWrite(cacheConfigProperties.getCaffeine().getRefreshAfterWrite(),
					TimeUnit.MILLISECONDS);
		}
		return cacheBuilder.build();
	}

	@Override
	public Collection<String> getCacheNames() {
		return this.cacheNames;
	}

	public void clearLocal(String cacheName, Object key) {
		Cache cache = cacheMap.get(cacheName);
		if (cache == null) {
			return;
		}

		RedisCaffeineCache redisCaffeineCache = (RedisCaffeineCache)cache;
		redisCaffeineCache.clearLocal(key);
	}

}