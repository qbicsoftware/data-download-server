package life.qbic.data_download.rest.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Policy;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;

class AclCacheConfigurationTest {

  @Test
  @DisplayName("aclCacheManager builds a native Caffeine cache with an expireAfterWrite policy equal to the TTL")
  void aclCacheManagerHasExpireAfterWritePolicyForTtl() {
    Duration ttl = Duration.ofSeconds(30);

    CaffeineCacheManager cacheManager = SecurityConfig.aclCacheManager(ttl);
    CaffeineCache cache = (CaffeineCache) cacheManager.getCache("acl_cache");
    assertNotNull(cache, "cache 'acl_cache' must be created");

    Cache<Object, Object> nativeCache = cache.getNativeCache();
    Policy<Object, Object> policy = nativeCache.policy();

    assertTrue(policy.expireAfterWrite().isPresent(),
        "an expireAfterWrite policy must be configured");
    assertEquals(ttl, policy.expireAfterWrite().get().getExpiresAfter(),
        "the cache TTL must equal the configured duration");
  }

  @Test
  @DisplayName("aclCacheManager with a custom TTL configures the same TTL on the cache")
  void aclCacheManagerHonorsCustomTtl() {
    Duration ttl = Duration.ofSeconds(120);

    CaffeineCacheManager cacheManager = SecurityConfig.aclCacheManager(ttl);
    CaffeineCache cache = (CaffeineCache) cacheManager.getCache("acl_cache");

    Policy<Object, Object> policy = cache.getNativeCache().policy();
    assertEquals(ttl, policy.expireAfterWrite().orElseThrow().getExpiresAfter());
  }
}