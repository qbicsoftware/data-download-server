package life.qbic.data_download.rest.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class QBiCTokenAuthenticationFilterTest {

  private static final String TOKEN_TYPE = "Bearer";

  private record Result(MockHttpServletResponse response, MockFilterChain chain) {

    /** A {@link MockFilterChain} records the request only once the chain is actually invoked. */
    boolean chainContinued() {
      return chain.getRequest() != null;
    }
  }

  @BeforeEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("a valid token authenticates and continues the filter chain")
  void validTokenContinuesChain() throws Exception {
    TestingAuthenticationToken principal = new TestingAuthenticationToken("user-1", null);
    AuthenticationManager manager = authentication -> {
      principal.setAuthenticated(true);
      return principal;
    };
    var filter = newFilter(manager);

    Result result = doFilter(filter, "Bearer valid-token");

    assertEquals(200, result.response().getStatus());
    assertTrue(result.chainContinued(), "the chain must continue for a valid token");
    assertNull(result.response().getHeader(HttpHeaders.WWW_AUTHENTICATE));
    assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    assertEquals("user-1", SecurityContextHolder.getContext().getAuthentication().getName());
  }

  @Test
  @DisplayName("an unknown token answers with 401 and stops the filter chain")
  void unknownTokenIsRejected() throws Exception {
    var filter = newFilter(authentication -> {
      throw new BadCredentialsException("not a valid token");
    });

    Result result = doFilter(filter, "Bearer unknown-token");

    assertEquals(401, result.response().getStatus());
    assertNull(SecurityContextHolder.getContext().getAuthentication(),
        "no authentication must be stored for a rejected token");
    assertNotNull(result.response().getHeader(HttpHeaders.WWW_AUTHENTICATE));
  }

  @Test
  @DisplayName("an expired token answers with 401 and an expiry hint")
  void expiredTokenIsRejected() throws Exception {
    var filter = newFilter(authentication -> {
      throw new CredentialsExpiredException("expired token");
    });

    Result result = doFilter(filter, "Bearer expired-token");

    assertEquals(401, result.response().getStatus());
    assertEquals("The personal access token has expired.", result.response().getContentAsString());
  }

  @Test
  @DisplayName("a request without an Authorization header is left to the chain")
  void missingHeaderContinuesChain() throws Exception {
    var filter = newFilter(authentication -> {
      throw new AssertionError("must not authenticate without a token");
    });

    Result result = doFilter(filter, null);

    assertEquals(200, result.response().getStatus());
    assertTrue(result.chainContinued());
    assertNull(result.response().getHeader(HttpHeaders.WWW_AUTHENTICATE));
  }

  @Test
  @DisplayName("a request with a different scheme is left to the chain")
  void differentSchemeContinuesChain() throws Exception {
    var filter = newFilter(authentication -> {
      throw new AssertionError("must not authenticate a non-matching scheme");
    });

    Result result = doFilter(filter, "Basic dXNlcjpwYXNz");

    assertEquals(200, result.response().getStatus());
    assertTrue(result.chainContinued());
  }

  @Test
  @DisplayName("a blank token is treated as an invalid credential and challenges with 401")
  void blankTokenIsRejected() throws Exception {
    var filter = newFilter(authentication -> {
      throw new AssertionError("must not authenticate a blank token");
    });

    Result result = doFilter(filter, "Bearer    ");

    assertEquals(401, result.response().getStatus());
    assertNull(SecurityContextHolder.getContext().getAuthentication());
    assertTrue(result.response().getHeader(HttpHeaders.WWW_AUTHENTICATE)
        .contains("error=\"invalid_token\""));
  }

  @Test
  @DisplayName("a bare scheme without a credential is rejected as an invalid token")
  void bareSchemeIsRejected() throws Exception {
    var filter = newFilter(authentication -> {
      throw new AssertionError("must not authenticate a bare scheme");
    });

    Result result = doFilter(filter, "Bearer");

    assertEquals(401, result.response().getStatus());
    assertNull(SecurityContextHolder.getContext().getAuthentication());
  }

  @Test
  @DisplayName("the filter never lets a blank token reach the authentication manager")
  void blankTokenNeverReachesManager() throws Exception {
    List<String> invocations = new ArrayList<>();
    AuthenticationManager manager = authentication -> {
      invocations.add("invoked");
      return new TestingAuthenticationToken("user", null);
    };
    var filter = newFilter(manager);

    doFilter(filter, "Bearer");

    assertTrue(invocations.isEmpty(), "the authentication manager must not be invoked for a blank token");
  }

  private QBiCTokenAuthenticationFilter newFilter(AuthenticationManager manager) {
    return new QBiCTokenAuthenticationFilter(manager, TOKEN_TYPE,
        new QbicTokenAuthenticationEntryPoint(TOKEN_TYPE));
  }

  private Result doFilter(QBiCTokenAuthenticationFilter filter, String header) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    if (header != null) {
      request.addHeader(HttpHeaders.AUTHORIZATION, header);
    }
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();
    filter.doFilter(request, response, chain);
    return new Result(response, chain);
  }
}
