package life.qbic.data_download.rest.config;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.ModifiedExpiryPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.cache.autoconfigure.JCacheManagerCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.acls.model.MutableAcl;

/**
 * Configures the Spring Security ACL cache.
 * <p>
 * The ACL cache ({@value #ACL_CACHE_NAME}) intentionally has a short, configurable time-to-live.
 * The download server never writes ACLs, so without expiry a group grant or revocation written by
 * the data-manager would go unnoticed until the server restarts. A TTL keeps the required
 * revocation window (see {@code docs/plans/user-group-download-authorization.md}) while avoiding a
 * broker integration.
 * <p>
 * The cache runs on the Spring Boot managed JCache (JSR-107) provider. With
 * {@code spring.cache.type=jcache} and Ehcache 3 on the classpath, Boot's cache auto-configuration
 * owns the {@code javax.cache.CacheManager}; this class only customizes the single ACL cache
 * through the idiomatic {@link JCacheManagerCustomizer} seam. No Ehcache XML and no hand-built
 * {@code CacheManager} bean are needed, so Boot's {@code CacheManagerCustomizer} contract keeps
 * working.
 * <p>
 * {@link ModifiedExpiryPolicy} expires entries after the write (creation/update) that set them;
 * {@code getExpiryForAccess()} returns {@code null}, so reads do not extend the entry's lifetime.
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching
public class AclCacheConfig {

  /** The name of the ACL cache, shared with {@link SecurityConfig#aclCache}. */
  static final String ACL_CACHE_NAME = "acl_cache";

  /**
   * Registers the ACL cache with the requested TTL on the Boot managed JCache manager.
   *
   * @param aclCacheTtl the time-to-live of ACL cache entries
   * @return the customizer applied by Boot's JCache auto-configuration
   */
  @Bean
  JCacheManagerCustomizer aclCacheCustomizer(
      @Value("${qbic.access-management.acl-cache-ttl:30s}") Duration aclCacheTtl) {
    return cacheManager -> cacheManager.createCache(ACL_CACHE_NAME,
        aclCacheConfiguration(aclCacheTtl));
  }

  /**
   * Builds the JCache configuration for the ACL cache: keyed by object identity, storing ACLs by
   * reference, and expiring entries after {@code aclCacheTtl}.
   *
   * @param aclCacheTtl the time-to-live of ACL cache entries
   * @return the cache configuration used for {@value #ACL_CACHE_NAME}
   */
  static MutableConfiguration<Object, MutableAcl> aclCacheConfiguration(Duration aclCacheTtl) {
    return new MutableConfiguration<Object, MutableAcl>()
        .setTypes(Object.class, MutableAcl.class)
        .setStoreByValue(false)
        .setExpiryPolicyFactory(ModifiedExpiryPolicy.factoryOf(
            new javax.cache.expiry.Duration(TimeUnit.MILLISECONDS, aclCacheTtl.toMillis())));
  }
}
