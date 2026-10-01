package life.qbic.data_download.rest.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import javax.cache.configuration.CompleteConfiguration;
import javax.cache.expiry.ExpiryPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.jcache.JCacheCache;
import org.springframework.cache.jcache.JCacheCacheManager;

class AclCacheConfigurationTest {

  @Test
  @DisplayName("aclCacheManager configures acl_cache with the requested TTL on an Ehcache-backed JCache")
  void aclCacheManagerConfiguresTtl() {
    CacheManager cacheManager = new SecurityConfig().aclCacheManager(Duration.ofSeconds(30));
    assertTrue(cacheManager instanceof JCacheCacheManager,
        "the ACL cache must run on the Ehcache JCache provider");

    Cache cache = cacheManager.getCache("acl_cache");
    assertNotNull(cache, "acl_cache must be created");
    assertTrue(cache instanceof JCacheCache);

    javax.cache.Cache<?, ?> nativeCache = (javax.cache.Cache<?, ?>) cache.getNativeCache();
    CompleteConfiguration<?, ?> configuration =
        nativeCache.getConfiguration(CompleteConfiguration.class);
    ExpiryPolicy policy = configuration.getExpiryPolicyFactory().create();

    assertEquals(new javax.cache.expiry.Duration(TimeUnit.SECONDS, 30),
        policy.getExpiryForCreation(), "the ACL cache TTL must match the configured duration");

    ((JCacheCacheManager) cacheManager).getCacheManager().close();
  }
}
