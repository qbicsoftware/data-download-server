package life.qbic.data_download.rest.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

class QbicTokenAccessDeniedHandlerTest {

  private final QbicTokenAccessDeniedHandler handler = new QbicTokenAccessDeniedHandler();

  @Test
  @DisplayName("a denied request results in 403")
  void deniedRequestIsForbidden() throws Exception {
    MockHttpServletResponse response = handle();

    assertEquals(403, response.getStatus());
  }

  @Test
  @DisplayName("no WWW-Authenticate challenge is sent, as a 403 is not an authentication request")
  void doesNotChallenge() throws Exception {
    MockHttpServletResponse response = handle();

    assertNull(response.getHeader(HttpHeaders.WWW_AUTHENTICATE));
  }

  @Test
  @DisplayName("the response body informs the user without exposing internals")
  void writesBody() throws Exception {
    MockHttpServletResponse response = handle();

    assertEquals("You do not have permission to access this resource.",
        response.getContentAsString());
    assertTrue(response.getContentType().startsWith("text/plain"), response.getContentType());
  }

  private MockHttpServletResponse handle() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    handler.handle(request, response, new AccessDeniedException("denied"));
    return response;
  }
}
