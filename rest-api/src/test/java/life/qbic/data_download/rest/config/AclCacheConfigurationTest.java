package life.qbic.data_download.rest.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import javax.cache.configuration.CompleteConfiguration;
import javax.cache.expiry.ExpiryPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.jcache.JCacheCache;
import org.springframework.cache.jcache.JCacheCacheManager;
import org.springframework.security.acls.domain.SpringCacheBasedAclCache;
import org.springframework.security.acls.model.MutableAcl;

class AclCacheConfigurationTest {

  private static final String ACL_CACHE_NAME = AclCacheConfig.ACL_CACHE_NAME;

  private final ApplicationContextRunner context = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(CacheAutoConfiguration.class))
      .withUserConfiguration(AclCacheConfig.class)
      // Boot's ApplicationContextRunner does not install Boot's conversion service by default, which
      // is what turns "30s" into a java.time.Duration for the @Value injection below.
      .withInitializer(ctx -> ctx.getBeanFactory()
          .setConversionService(ApplicationConversionService.getSharedInstance()))
      .withPropertyValues("spring.cache.type=jcache");

  @Test
  @DisplayName("Boot provides the ACL cache as a JCache (Ehcache) cache with the requested TTL")
  void aclCacheRunsOnBootManagedJCacheWithRequestedTtl() {
    context.withPropertyValues("qbic.access-management.acl-cache-ttl=30s")
        .run(ctx -> {
          assertThat(ctx).hasNotFailed();

          CacheManager cacheManager = ctx.getBean(CacheManager.class);
          assertThat(cacheManager).isInstanceOf(JCacheCacheManager.class);

          var cache = cacheManager.getCache(ACL_CACHE_NAME);
          assertThat(cache).isNotNull().isInstanceOf(JCacheCache.class);

          ExpiryPolicy policy = expiryPolicyOf(cache);
          assertThat(policy.getExpiryForCreation())
              .isEqualTo(new javax.cache.expiry.Duration(TimeUnit.SECONDS, 30));
          assertThat(policy.getExpiryForUpdate())
              .isEqualTo(new javax.cache.expiry.Duration(TimeUnit.SECONDS, 30));
        });
  }

  @Test
  @DisplayName("a custom TTL is honoured rather than the default")
  void customTtlIsHonoured() {
    context.withPropertyValues("qbic.access-management.acl-cache-ttl=5s")
        .run(ctx -> {
          assertThat(ctx).hasNotFailed();
          var cache = ctx.getBean(CacheManager.class).getCache(ACL_CACHE_NAME);

          assertThat(expiryPolicyOf(cache).getExpiryForCreation())
              .isEqualTo(new javax.cache.expiry.Duration(TimeUnit.SECONDS, 5));
        });
  }

  @Test
  @DisplayName("accessing an ACL cache entry does not extend its lifetime (expireAfterWrite semantics)")
  void accessDoesNotExtendLifetime() {
    context.withPropertyValues("qbic.access-management.acl-cache-ttl=30s")
        .run(ctx -> {
          ExpiryPolicy policy = expiryPolicyOf(ctx.getBean(CacheManager.class).getCache(ACL_CACHE_NAME));
          assertThat(policy.getExpiryForAccess())
              .as("reads must not extend the entry, otherwise revocations could be delayed")
              .isNull();
        });
  }

  @Test
  @DisplayName("ACL cache entries actually expire after the configured TTL without a write")
  void entriesExpireAfterTtl() {
    context.withPropertyValues("qbic.access-management.acl-cache-ttl=200ms")
        .run(ctx -> {
          var cache = ctx.getBean(CacheManager.class).getCache(ACL_CACHE_NAME);
          Object key = "project-42";
          cache.put(key, mock(MutableAcl.class));

          assertThat(cache.get(key)).as("entry is present right after the write").isNotNull();

          await().atMost(Duration.ofSeconds(10))
              .pollInterval(Duration.ofMillis(50))
              .untilAsserted(() -> assertThat(cache.get(key))
                  .as("a revoked/never-refreshed ACL must disappear once the TTL elapsed")
                  .isNull());
        });
  }

  @Test
  @DisplayName("SecurityConfig builds its AclCache from the Boot-managed acl_cache")
  void securityConfigBuildsAclCacheFromBootManagedCache() {
    context.withPropertyValues("qbic.access-management.acl-cache-ttl=30s")
        .run(ctx -> {
          CacheManager cacheManager = ctx.getBean(CacheManager.class);
          var config = new SecurityConfig();

          var aclCache = config.aclCache(cacheManager);

          assertThat(aclCache).isInstanceOf(SpringCacheBasedAclCache.class);
          // A name mismatch or a missing acl_cache would surface here as a null cache/NPE.
          ctx.getBean(CacheManager.class).getCache(ACL_CACHE_NAME).put("key", mock(MutableAcl.class));
          assertThat(ctx.getBean(CacheManager.class).getCache(ACL_CACHE_NAME).get("key")).isNotNull();
        });
  }

  private static ExpiryPolicy expiryPolicyOf(org.springframework.cache.Cache cache) {
    javax.cache.Cache<?, ?> nativeCache = (javax.cache.Cache<?, ?>) cache.getNativeCache();
    @SuppressWarnings("unchecked")
    CompleteConfiguration<Object, Object> configuration =
        nativeCache.getConfiguration(CompleteConfiguration.class);
    return configuration.getExpiryPolicyFactory().create();
  }
}
