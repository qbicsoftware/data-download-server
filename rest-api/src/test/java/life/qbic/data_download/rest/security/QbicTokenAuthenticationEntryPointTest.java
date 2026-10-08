package life.qbic.data_download.rest.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;

class QbicTokenAuthenticationEntryPointTest {

  private static final String TOKEN_TYPE = "Bearer";

  private final QbicTokenAuthenticationEntryPoint entryPoint =
      new QbicTokenAuthenticationEntryPoint(TOKEN_TYPE);

  @Test
  @DisplayName("an expired token results in 401 with invalid_token")
  void expiredTokenChallengesWithInvalidToken() throws Exception {
    MockHttpServletResponse response = commence(new CredentialsExpiredException("expired token"));

    assertEquals(401, response.getStatus());
    String challenge = response.getHeader(HttpHeaders.WWW_AUTHENTICATE);
    assertEquals(
        "Bearer error=\"invalid_token\", error_description=\"The personal access token has expired.\"",
        challenge);
  }

  @Test
  @DisplayName("an unknown or malformed token results in 401 with invalid_token")
  void badCredentialsChallengesWithInvalidToken() throws Exception {
    MockHttpServletResponse response = commence(new BadCredentialsException("not a valid token"));

    assertEquals(401, response.getStatus());
    String challenge = response.getHeader(HttpHeaders.WWW_AUTHENTICATE);
    assertEquals(
        "Bearer error=\"invalid_token\", error_description=\"The personal access token is invalid.\"",
        challenge);
  }

  @Test
  @DisplayName("a request without credentials is challenged with the scheme only (RFC 6750 3.1)")
  void missingCredentialsChallengesWithoutErrorCode() throws Exception {
    MockHttpServletResponse response = commence(new InsufficientAuthenticationException("none"));

    assertEquals(401, response.getStatus());
    assertEquals(TOKEN_TYPE, response.getHeader(HttpHeaders.WWW_AUTHENTICATE));
  }

  @Test
  @DisplayName("an unclassified failure still challenges with invalid_token")
  void unclassifiedFailureChallengesWithInvalidToken() throws Exception {
    MockHttpServletResponse response = commence(new AuthenticationException("boom") {
    });

    assertEquals(401, response.getStatus());
    assertTrue(response.getHeader(HttpHeaders.WWW_AUTHENTICATE).contains("error=\"invalid_token\""));
  }

  @Test
  @DisplayName("the response body mirrors the error description for header-agnostic clients")
  void writesDescriptionToBody() throws Exception {
    MockHttpServletResponse response = commence(new CredentialsExpiredException("expired token"));

    assertEquals("The personal access token has expired.", response.getContentAsString());
    assertTrue(response.getContentType().startsWith("text/plain"), response.getContentType());
  }

  private MockHttpServletResponse commence(AuthenticationException exception) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    entryPoint.commence(request, response, exception);
    return response;
  }
}
