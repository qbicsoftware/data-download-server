package life.qbic.data_download.rest.security;

import static java.util.Objects.requireNonNull;

import life.qbic.data_download.rest.security.jpa.token.EncodedAccessToken;
import life.qbic.data_download.rest.security.jpa.token.EncodedAccessTokenRepository;
import life.qbic.data_download.rest.security.jpa.user.QBiCUserDetails;
import life.qbic.data_download.rest.security.jpa.user.UserDetailsRepository;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

/**
 * An {@link AuthenticationProvider} capable of authenticating a
 * {@link QBiCTokenAuthenticationRequest} and turning it into a {@link QBiCTokenAuthentication}
 */
public class QBiCTokenAuthenticationProvider implements AuthenticationProvider {

  private final TokenEncoder tokenEncoder;
  private final EncodedAccessTokenRepository encodedAccessTokenRepository;
  private final UserDetailsRepository userDetailsRepository;

  public QBiCTokenAuthenticationProvider(TokenEncoder tokenEncoder,
      EncodedAccessTokenRepository encodedAccessTokenRepository,
      UserDetailsRepository userDetailsRepository) {
    this.tokenEncoder = requireNonNull(tokenEncoder, "tokenEncoder must not be null");
    this.encodedAccessTokenRepository = requireNonNull(encodedAccessTokenRepository,
        "encodedAccessTokenRepository must not be null");
    this.userDetailsRepository = requireNonNull(userDetailsRepository,
        "userDetailsRepository must not be null");
  }

  @Override
  public Authentication authenticate(Authentication authentication) throws AuthenticationException {
    if (authentication instanceof QBiCTokenAuthenticationRequest authenticationRequest) {
      String token = authenticationRequest.getToken();
      EncodedAccessToken encodedAccessToken = encodedAccessTokenRepository.findAll()
          .parallelStream()
          .filter(storedToken -> tokenEncoder.matches(token.toCharArray(),
              storedToken.getAccessToken()))
          .findAny().orElseThrow(
              () -> new BadCredentialsException("not a valid token")
          );
      if (encodedAccessToken.isExpired()) {
        throw new CredentialsExpiredException("expired token");
      }

      QBiCUserDetails userDetails = userDetailsRepository.findById(encodedAccessToken.getUserId())
          .orElseThrow(
              () -> new InternalAuthenticationServiceException("No user found for valid token."));
      return new QBiCTokenAuthentication(userDetails);
    }
    return null;
  }

  @Override
  public boolean supports(Class<?> authentication) {
    return authentication.equals(QBiCTokenAuthenticationRequest.class);
  }
}
