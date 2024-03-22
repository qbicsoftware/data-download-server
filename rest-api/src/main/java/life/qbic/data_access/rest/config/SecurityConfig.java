package life.qbic.data_access.rest.config;

import life.qbic.data_access.rest.security.QBiCTokenAuthenticationFilter;
import life.qbic.data_access.rest.security.QBiCTokenAuthenticationProvider;
import life.qbic.data_access.rest.security.QBiCTokenEncoder;
import life.qbic.data_access.rest.security.TokenEncoder;
import life.qbic.data_access.rest.security.jpa.token.EncodedAccessTokenRepository;
import life.qbic.data_access.rest.security.jpa.user.UserDetailsRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

/**
 * The security config setting up endpoint security for the controllers.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final EncodedAccessTokenRepository encodedAccessTokenRepository;
  private final UserDetailsRepository userDetailsRepository;

  public SecurityConfig(EncodedAccessTokenRepository encodedAccessTokenRepository,
      UserDetailsRepository userDetailsRepository) {
    this.encodedAccessTokenRepository = encodedAccessTokenRepository;
    this.userDetailsRepository = userDetailsRepository;
  }

  @Bean("accessTokenEncoder")
  public TokenEncoder tokenEncoder() {
    return new QBiCTokenEncoder();
  }

  @Bean("tokenAuthenticationProvider")
  public QBiCTokenAuthenticationProvider authenticationProvider(
      @Qualifier("accessTokenEncoder") TokenEncoder tokenEncoder) {
    return new QBiCTokenAuthenticationProvider(tokenEncoder, encodedAccessTokenRepository, userDetailsRepository);
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
      @Qualifier("tokenAuthenticationFilter") QBiCTokenAuthenticationFilter tokenAuthenticationFilter) throws Exception {
    http
//        //only use HTTPS
//        .requiresChannel(channel -> channel.anyRequest().requiresSecure())
        .authenticationProvider(authenticationProvider)
        .addFilterAt(tokenAuthenticationFilter, BasicAuthenticationFilter.class)
        .authorizeHttpRequests(authorizedRequest ->
            authorizedRequest
                .requestMatchers("/download/measurements/**")
                .authenticated())
//                .access(new WebExpressionAuthorizationManager("hasPermission(//TODO)")));
        ;

    return http.build();
  }
}
