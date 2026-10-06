# Implementation Plan — Group-based download authorization

**Status:** Ready for implementation
**Branch:** `feature/group-based-download-authorization`
**Owner (implementation):** worker subagent
**Requirement:** A user who is a member of an active **user group** that has been shared onto a
project shall be able to download that project's measurements, exactly like a directly-added
collaborator.

---

## 1. Problem

The data-manager app now supports **user groups** that can be shared onto projects. Group access is
persisted in the Spring Security ACL as a `GrantedAuthoritySid` with the reserved string
`GROUP_<groupId>` (see data-manager `ProjectAccessServiceImpl.addAuthorityAccess` and
`docs/user-groups-strategy.md`, §4.2/§4.3).

The data-download-server authorizes downloads by checking the project ACL that backs the requested
measurement:

1. `QBiCTokenAuthenticationFilter` → `QBiCTokenAuthenticationProvider` resolves the personal access
   token to a `QBiCUserDetails`.
2. `QBiCUserDetails.getAuthorities()` returns **only** `ROLE_*` authorities.
3. `QbicPermissionEvaluator` maps `measurementId → projectId` and delegates to Spring's
   `AclPermissionEvaluator`.
4. `AclPermissionEvaluator` derives SIDs with the **default** `SidRetrievalStrategyImpl` (principal
   + one `GrantedAuthoritySid` per authority). `GroupAwareSidRetrievalStrategy` was never added.

A group ACE (`GROUP_<id>`) therefore never matches any derived SID, so group members are denied.
The mechanism itself already works for `ROLE_ADMIN`/`ROLE_PROJECT_MANAGER` grants because those are
real authorities on the authentication — only group SIDs are missing.

A second, pre-existing defect blocks the feature as well: `SecurityConfig.aclCache()` builds a
`ConcurrentMapCacheManager` with **no TTL**. The download server never writes ACLs, so once a project
ACL is read it is cached for the process lifetime. A group shared *after* that first read would not
grant access until the server restarts. `rest-api/src/main/resources/ehcache3.xml` (30 s TTL) and
`spring.cache.jcache.config` exist but are dead configuration: no `CachingProvider` is on the
classpath, and the app neither enables caching nor lets Boot manage a `CacheManager`, so Boot's
`CacheAutoConfiguration` (and with it the `JCacheCacheConfiguration` that reads the property) never
runs. Ehcache's XML parser additionally needs `javax.xml.bind` (JAXB), which no longer ships with
the JDK. The code ignores all of this.

The message broker is **not** available to the download server, so the data-manager broadcast
eviction mechanism (strategy §4.6 / plan D5) cannot be reused. The ACL cache must instead get a
short TTL (≤ 60 s revocation NFR) so grants/revocations are picked up from the shared database.

---

## 2. Scope

In scope:

- Resolve the active group SIDs of the authenticated user from the `data_management` database.
- Extend the ACL SID derivation with those group SIDs.
- Give the ACL cache a configurable TTL (default 30 s).
- Unit tests + focused validation.

Out of scope / non-goals:

- No changes to the data-manager repo (it is under active development; read-only).
- No message-broker integration, no broadcast eviction.
- No role/permission changes, no endpoint or API changes.
- No changes to group *management* (the download server only reads memberships).

---

## 3. Design

### 3.1 Group membership lookup (read-only)

The group tables live in the `data_management` database, i.e. the same datasource the download
server already uses for ACLs (`qbic.access-management.datasource` → `securityDataSource` bean).
Add a small read-only port and a JDBC adapter.

New files (package `life.qbic.data_download.rest.security.acl`):

- `GroupSidProvider.java`

  ```java
  public interface GroupSidProvider {
    String GROUP_SID_PREFIX = "GROUP_";
    /** Returns "GROUP_<groupId>" for every ACTIVE group membership of the user. */
    List<String> listGroupSidsForUser(String userId);
  }
  ```

- `JdbcGroupSidProvider.java` — implements the port with a `JdbcTemplate` that runs against the
  access-management datasource:

  ```sql
  SELECT m.group_id
  FROM group_membership m
  JOIN user_group g ON g.id = m.group_id
  WHERE m.user_id = ? AND g.status = 'ACTIVE'
  ```

  Map each `group_id` to `GROUP_SID_PREFIX + groupId`. Return an empty list for null/blank `userId`.
  Use `jdbcTemplate.queryForList(SQL, String.class, userId)` so it stays trivially testable.
  Keep the SQL prefix exactly `GROUP_` (contract with data-manager; a mismatch silently denies
  access).

### 3.2 Group-aware SID retrieval strategy

New file `rest-api/src/main/java/life/qbic/data_download/rest/security/GroupAwareSidRetrievalStrategy.java`
(mirrors the data-manager class of the same name):

```java
public class GroupAwareSidRetrievalStrategy implements SidRetrievalStrategy {
  private final SidRetrievalStrategy defaultStrategy = new SidRetrievalStrategyImpl();
  private final GroupSidProvider groupSidProvider;

  @Override
  public List<Sid> getSids(Authentication authentication) {
    List<Sid> sids = new ArrayList<>(defaultStrategy.getSids(authentication));
    String userId = authentication == null ? null : authentication.getName();
    if (userId != null && !userId.isBlank()) {
      groupSidProvider.listGroupSidsForUser(userId).stream()
          .filter(sid -> sid != null && !sid.isBlank())
          .map(GrantedAuthoritySid::new)
          .forEach(sids::add);
    }
    return sids;
  }
}
```

Do **not** inject group SIDs into the `Authentication` — deriving them per permission check keeps
grant/revoke effective at the next check (subject to the ACL cache TTL, see §3.4).

### 3.3 Wiring

In `SecurityConfig`:

- New bean:

  ```java
  @Bean("groupSidProvider")
  public GroupSidProvider groupSidProvider(@Qualifier("securityDataSource") DataSource dataSource) {
    return new JdbcGroupSidProvider(new JdbcTemplate(dataSource));
  }
  ```

- Extend the `permissionEvaluator(...)` bean with a `GroupSidProvider` parameter and set the
  strategy on the evaluator:

  ```java
  QbicPermissionEvaluator evaluator = new QbicPermissionEvaluator(aclService, measurementMappingService);
  evaluator.setSidRetrievalStrategy(new GroupAwareSidRetrievalStrategy(groupSidProvider));
  return evaluator;
  ```

`QbicPermissionEvaluator` itself is unchanged; `setSidRetrievalStrategy` is inherited from
`AclPermissionEvaluator`.

### 3.4 ACL cache TTL

Let Spring Boot own the cache. With `spring.cache.type=jcache` and `org.ehcache:ehcache` on the
classpath, Boot's `CacheAutoConfiguration` creates the `JCacheCacheManager`; the single `acl_cache`
cache (and its TTL) is customized through the idiomatic `JCacheManagerCustomizer` seam. This avoids
a hand-rolled `CacheManager` bean — which, due to `@ConditionalOnMissingBean(CacheManager.class)`,
would suppress Boot's cache auto-configuration entirely and defeat `CacheManagerCustomizer` beans.
No XML and no JAXB are involved either way; the XML path is dead config that also requires JAXB,
which no longer ships with the JDK.

New `AclCacheConfig` (package `life.qbic.data_download.rest.config`):

```java
@Configuration(proxyBeanMethods = false)
@EnableCaching
public class AclCacheConfig {

  static final String ACL_CACHE_NAME = "acl_cache";

  @Bean
  JCacheManagerCustomizer aclCacheCustomizer(
      @Value("${qbic.access-management.acl-cache-ttl:30s}") Duration aclCacheTtl) {
    return cacheManager -> cacheManager.createCache(ACL_CACHE_NAME,
        aclCacheConfiguration(aclCacheTtl));
  }

  static MutableConfiguration<Object, MutableAcl> aclCacheConfiguration(Duration aclCacheTtl) {
    return new MutableConfiguration<Object, MutableAcl>()
        .setTypes(Object.class, MutableAcl.class)
        .setStoreByValue(false)
        .setExpiryPolicyFactory(ModifiedExpiryPolicy.factoryOf(
            new javax.cache.expiry.Duration(TimeUnit.MILLISECONDS, aclCacheTtl.toMillis())));
  }
}
```

`ModifiedExpiryPolicy` is `expireAfterWrite`-equivalent: `getExpiryForAccess()` returns `null`, so
reads do not extend an entry's lifetime.

- Add `spring-boot-starter-cache` + `org.ehcache:ehcache` to `rest-api/pom.xml` (Ehcache version
  managed by the Spring Boot BOM; the Ehcache artifact brings `javax.cache:cache-api`).
- Add `spring.cache.type=jcache` and `qbic.access-management.acl-cache-ttl=${ACL_CACHE_TTL:30s}` to
  `application.properties` (30 s keeps the ≤ 60 s revocation window).
- Delete the dead `rest-api/src/main/resources/ehcache3.xml` (unparseable without JAXB).
- `SecurityConfig.aclCache(CacheManager)` injects the Boot-provided `CacheManager`:

  ```java
  @Bean
  protected AclCache aclCache(CacheManager cacheManager) {
    return new SpringCacheBasedAclCache(
        cacheManager.getCache(AclCacheConfig.ACL_CACHE_NAME),
        permissionGrantingStrategy(),
        aclAuthorizationStrategy());
  }
  ```

Boot closes the JCache manager on context shutdown. The cache stays keyed by `ObjectIdentity`; only
the eviction policy changes. No write-path change is needed because the download server never calls
`updateAcl`.

---

## 4. File changes

| File | Action |
|---|---|
| `rest-api/src/main/java/life/qbic/data_download/rest/security/acl/GroupSidProvider.java` | create |
| `rest-api/src/main/java/life/qbic/data_download/rest/security/acl/JdbcGroupSidProvider.java` | create |
| `rest-api/src/main/java/life/qbic/data_download/rest/security/GroupAwareSidRetrievalStrategy.java` | create |
| `rest-api/src/main/java/life/qbic/data_download/rest/config/SecurityConfig.java` | modify (`groupSidProvider` bean, `permissionEvaluator`, `aclCache` uses Boot `CacheManager`) |
| `rest-api/src/main/java/life/qbic/data_download/rest/config/AclCacheConfig.java` | create (Boot-managed JCache `acl_cache` with TTL) |
| `rest-api/pom.xml` | modify (`spring-boot-starter-cache` + `org.ehcache:ehcache`) |
| `rest-api/src/main/resources/application.properties` | modify (`spring.cache.type` + TTL property) |
| `rest-api/src/main/resources/ehcache3.xml` | delete (dead: XML config requires JAXB) |
| `rest-api/src/test/java/life/qbic/data_download/rest/security/GroupAwareSidRetrievalStrategyTest.java` | create |
| `rest-api/src/test/java/life/qbic/data_download/rest/security/acl/JdbcGroupSidProviderTest.java` | create |
| `rest-api/src/test/java/life/qbic/data_download/rest/config/AclCacheConfigurationTest.java` | create |

---

## 5. Tests

All tests are JUnit 5 with plain test doubles, matching existing `rest-api` tests
(`QBicTokenEncoderTest`, `MeasurementFileIndexTest`).

1. `GroupAwareSidRetrievalStrategyTest`
   - default SIDs (principal + authority SIDs) are preserved;
   - one `GrantedAuthoritySid("GROUP_<id>")` is appended per provider result;
   - null/blank user id → only default SIDs;
   - null/blank group SIDs from the provider are ignored.
2. `JdbcGroupSidProviderTest`
   - user id is passed to the query and returned `group_id`s are prefixed with `GROUP_`;
   - null/blank user id → empty list, repository not called.
3. `AclCacheConfigurationTest`
   - Boot's JCache auto-configuration provides the `acl_cache` as a `JCacheCache` on a
     `JCacheCacheManager` (via the `JCacheManagerCustomizer`);
   - the configured TTL is applied to creation and update, and a custom TTL overrides the default;
   - `getExpiryForAccess()` is `null`, so reads do not extend an entry's lifetime;
   - an entry is actually evicted after a short TTL (regression guard for the stale-ACL defect).

---

## 6. Acceptance criteria

1. A user who is a member of an ACTIVE group that has a READ/WRITE/ADMIN ACE on the project backing
   a requested measurement is authorized to download it (verified by unit-level strategy + widget
   wiring; no live DB in CI).
2. Group SIDs are derived from the database on every permission check; only ACTIVE groups count.
3. The ACL cache expires entries after the configured TTL (default 30 s, configurable), so grants
   and revocations are observed within the ≤ 60 s window.
4. Existing principal/`ROLE_*` authorization behavior is unchanged.
5. `mvn -B -pl rest-api -am test` passes; the full build compiles.

---

## 7. Validation commands

```bash
cd /Users/sven1103/git/data-download-server
mvn -B -pl rest-api -am test
mvn -B -pl rest-api -am package -DskipTests
```

---

## 8. Risks / notes

- **SID contract drift:** the `GROUP_` prefix must match data-manager's
  `GroupSidProvider.GROUP_SID_PREFIX` and the id must be the stable group id (never the name).
  Single definition in `GroupSidProvider`.
- **Datasource assumption:** group tables and ACL tables share the access-management database. The
  lookup uses `securityDataSource`, the same datasource as the ACL, so they cannot diverge.
- **Performance:** one extra indexed query per permission check (`idx_group_membership_user`);
  acceptable, and required for fresh revocation. Do not add a long-lived membership cache.
- **TTL window:** 30 s default is within the ≤ 60 s revocation NFR. Keep it configurable so
  operations can tune it.
- **JAXB:** the programmatic/Boot JCache path does not *require* a JAXB runtime, but
  `org.ehcache:ehcache` declares an optional/non-pinned `org.glassfish.jaxb:jaxb-runtime` runtime
  dependency, so a JAXB runtime may still land on the classpath. The point is only that the app no
  longer relies on Ehcache XML parsing (which would hard-require JAXB).
- **`SidRetrievalStrategyImpl` deprecation:** verify the imported symbol against the Spring Security
  version in use; if a replacement is mandated by Spring Boot 4.1, use the current API and note it
  in the report. Do not hand-roll principal/authority extraction if the framework still provides it.
