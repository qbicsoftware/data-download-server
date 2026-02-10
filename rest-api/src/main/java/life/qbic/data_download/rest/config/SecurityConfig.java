package life.qbic.data_download.rest.config;

import static org.springframework.security.authorization.AuthorizationManagers.anyOf;

import javax.sql.DataSource;
import life.qbic.data_download.rest.security.QBiCTokenAuthenticationFilter;
import life.qbic.data_download.rest.security.QBiCTokenAuthenticationProvider;
import life.qbic.data_download.rest.security.QBicTokenEncoder;
import life.qbic.data_download.rest.security.RequestAuthorizationManagerFactory;
import life.qbic.data_download.rest.security.TokenEncoder;
import life.qbic.data_download.rest.security.acl.MeasurementMappingService;
import life.qbic.data_download.rest.security.acl.QBiCMeasurementMappingService;
import life.qbic.data_download.rest.security.acl.QbicPermissionEvaluator;
import life.qbic.data_download.rest.security.jpa.measurement.NGSMeasurementRepository;
import life.qbic.data_download.rest.security.jpa.measurement.ProteomicsMeasurementRepository;
import life.qbic.data_download.rest.security.jpa.token.EncodedAccessTokenRepository;
import life.qbic.data_download.rest.security.jpa.user.UserDetailsRepository;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.acls.domain.AclAuthorizationStrategy;
import org.springframework.security.acls.domain.AclAuthorizationStrategyImpl;
import org.springframework.security.acls.domain.AuditLogger;
import org.springframework.security.acls.domain.DefaultPermissionGrantingStrategy;
import org.springframework.security.acls.domain.SpringCacheBasedAclCache;
import org.springframework.security.acls.jdbc.BasicLookupStrategy;
import org.springframework.security.acls.jdbc.JdbcMutableAclService;
import org.springframework.security.acls.jdbc.LookupStrategy;
import org.springframework.security.acls.model.AclCache;
import org.springframework.security.acls.model.AclService;
import org.springframework.security.acls.model.AuditableAccessControlEntry;
import org.springframework.security.acls.model.MutableAclService;
import org.springframework.security.acls.model.PermissionGrantingStrategy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.expression.DefaultHttpSecurityExpressionHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.util.Assert;

/**
 * The security config setting up endpoint security for the controllers.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final String[] ignoredEndpoints = {
      "/v3/api-docs/**",
      "/swagger-ui/**",
      "/swagger-ui.html"
  };


  @Bean("accessTokenEncoder")
  public TokenEncoder tokenEncoder(
      @Value("${qbic.access-token.salt}") String salt,
      @Value("${qbic.access-token.iteration-count}") int iterationCount) {
    return new QBicTokenEncoder(salt, iterationCount);
  }

  @Bean("tokenAuthenticationProvider")
  public QBiCTokenAuthenticationProvider authenticationProvider(
      @Qualifier("accessTokenEncoder") TokenEncoder tokenEncoder,
      EncodedAccessTokenRepository encodedAccessTokenRepository,
      UserDetailsRepository userDetailsRepository) {
    return new QBiCTokenAuthenticationProvider(tokenEncoder, encodedAccessTokenRepository,
        userDetailsRepository);
  }

  @Bean("tokenAuthenticationManager")
  public AuthenticationManager authenticationManager(
      @Qualifier("tokenAuthenticationProvider") QBiCTokenAuthenticationProvider authenticationProvider) {
    return new ProviderManager(authenticationProvider);
  }

  @Bean("tokenAuthenticationFilter")
  public QBiCTokenAuthenticationFilter authenticationFilter(
      @Qualifier("tokenAuthenticationManager") AuthenticationManager authenticationManager,
      @Value("${server.download.token-name}") String tokenName) {
    return new QBiCTokenAuthenticationFilter(authenticationManager, tokenName);
  }


  @Bean
  public SecurityFilterChain apiFilterChain(HttpSecurity http,
      @Qualifier("tokenAuthenticationProvider") QBiCTokenAuthenticationProvider authenticationProvider,
      @Qualifier("tokenAuthenticationFilter") QBiCTokenAuthenticationFilter tokenAuthenticationFilter,
      @Qualifier("authorizationManagerFactory") RequestAuthorizationManagerFactory requestAuthorizationManagerFactory
  ) throws Exception {
    http
        .authorizeHttpRequests(authorizedRequest ->
            authorizedRequest
                .requestMatchers(ignoredEndpoints)
                .permitAll())
        //require https
        .requiresChannel(channel -> channel.anyRequest().requiresSecure())
        .authenticationProvider(authenticationProvider)
        .addFilterAt(tokenAuthenticationFilter, BasicAuthenticationFilter.class)
        .authorizeHttpRequests(authorizedRequest ->
            authorizedRequest
                .requestMatchers("measurements/{measurementId}")
                .access(anyOf(
                    requestAuthorizationManagerFactory.spel(
                        "hasPermission(#measurementId, 'qbic.measurement', 'READ')")
                )));

    return http.build();
  }

  @Bean("authorizationManagerFactory")
  public RequestAuthorizationManagerFactory authorizationManagerFactory(
      @Qualifier("permissionEvaluator") PermissionEvaluator permissionEvaluator) {

    DefaultHttpSecurityExpressionHandler expressionHandler = new DefaultHttpSecurityExpressionHandler();
    expressionHandler.setPermissionEvaluator(permissionEvaluator);
    return new RequestAuthorizationManagerFactory(expressionHandler);
  }

  // ACL
  @Bean("auditLogger")
  public AuditLogger auditLogger() {
    var logger = LoggerFactory.getLogger(SecurityConfig.class);
    return (granted, ace) -> {
      Assert.notNull(ace, "AccessControlEntry required");
      if (ace instanceof AuditableAccessControlEntry auditableAce) {
        if (!granted) {
          logger.info("DENIED due to ACE: %s".formatted(ace));
        } else {
          auditableAce.isAuditSuccess();
        }
      }
    };
  }

  @Bean("aclAuthorizationStrategy")
  public AclAuthorizationStrategy aclAuthorizationStrategy() {
    return new AclAuthorizationStrategyImpl(
        new SimpleGrantedAuthority("acl:change-owner"), //give this to ROLE_ADMIN
        new SimpleGrantedAuthority("acl:change-audit"), // give this to ROLE_ADMIN
        new SimpleGrantedAuthority("acl:change-access")
        //give this to ROLE_ADMIN, ROLE_PROJECT_MANAGER
    );
  }

    @Bean("permissionGrantingStrategy")
    public PermissionGrantingStrategy permissionGrantingStrategy() {
      return new DefaultPermissionGrantingStrategy(auditLogger());
    }

  @Bean
  protected AclCache aclCache() {
    CacheManager cacheManager = new ConcurrentMapCacheManager();
    return new SpringCacheBasedAclCache(
        cacheManager.getCache("acl_cache"),
        permissionGrantingStrategy(),
        aclAuthorizationStrategy());
  }

  @Bean(name="securityDataSourceProperties")
  @ConfigurationProperties("qbic.access-management.datasource")
  public DataSourceProperties dataSourceProperties() {
    return new DataSourceProperties();
  }

  @Bean("securityDataSource")
  public DataSource dataSource() {
    return dataSourceProperties()
        .initializeDataSourceBuilder()
        .build();
  }

  @Bean("idSupportingLookupStrategy")
  public LookupStrategy lookupStrategy(
      @Qualifier("securityDataSource") DataSource dataSource) {
    BasicLookupStrategy basicLookupStrategy = new BasicLookupStrategy(
        dataSource,
        aclCache(),
        aclAuthorizationStrategy(),
        auditLogger()
    );
    basicLookupStrategy.setAclClassIdSupported(true);
    return basicLookupStrategy;
  }


  @Bean("aclService")
  public MutableAclService mutableAclService(
      @Qualifier("securityDataSource") DataSource dataSource,
      @Qualifier("idSupportingLookupStrategy") LookupStrategy lookupStrategy) {
    JdbcMutableAclService jdbcMutableAclService = new JdbcMutableAclService(dataSource,
        lookupStrategy, aclCache());
    // allow for non-long type ids
    jdbcMutableAclService.setAclClassIdSupported(true);

    return jdbcMutableAclService;
  }

  @Bean("measurementMappingService")
  public MeasurementMappingService measurementMappingService(
      ProteomicsMeasurementRepository proteomicsMeasurementRepository,
      NGSMeasurementRepository ngsMeasurementRepository) {
    return new QBiCMeasurementMappingService(ngsMeasurementRepository, proteomicsMeasurementRepository);
  }

  @Bean("permissionEvaluator")
  public PermissionEvaluator permissionEvaluator(
      @Qualifier("aclService") AclService aclService,
      @Qualifier("measurementMappingService") MeasurementMappingService measurementMappingService) {
    return new QbicPermissionEvaluator(aclService, measurementMappingService);
  }

  @Bean
  public MethodSecurityExpressionHandler defaultMethodSecurityExpressionHandler(
      @Qualifier("permissionEvaluator") PermissionEvaluator permissionEvaluator) {
    var expressionHandler = new DefaultMethodSecurityExpressionHandler();
    expressionHandler.setPermissionEvaluator(permissionEvaluator);
    return expressionHandler;
  }
}
