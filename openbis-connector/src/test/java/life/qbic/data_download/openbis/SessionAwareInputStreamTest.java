package life.qbic.data_download.openbis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import life.qbic.data_download.openbis.SessionFactory.OpenBisSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SessionAwareInputStreamTest {

  private static final class CloseTrackingInputStream extends InputStream {

    private boolean closed;

    @Override
    public int read() {
      return -1;
    }

    @Override
    public void close() {
      closed = true;
    }

    boolean isClosed() {
      return closed;
    }
  }

  private static final class FakeSession implements OpenBisSession {

    private boolean closed;
    private boolean loggedOut;

    @Override
    public String getToken() {
      return "fake-token";
    }

    @Override
    public void close() {
      loggedOut = true;
      closed = true;
    }

    boolean isLoggedOut() {
      return loggedOut;
    }

    boolean isClosed() {
      return closed;
    }
  }

  @Test
  @DisplayName("reads delegate data")
  void readsData() throws IOException {
    var delegate = new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8));
    var session = new FakeSession();
    var stream = new SessionAwareInputStream(delegate, session);

    byte[] buffer = stream.readAllBytes();
    assertEquals("hello", new String(buffer, StandardCharsets.UTF_8));
    assertFalse(session.isLoggedOut());
  }

  @Test
  @DisplayName("closing the stream closes the delegate and logs out the session")
  void closeLogsOutSession() throws IOException {
    var delegate = new CloseTrackingInputStream();
    var session = new FakeSession();
    var stream = new SessionAwareInputStream(delegate, session);

    stream.close();

    assertTrue(delegate.isClosed());
    assertTrue(session.isLoggedOut());
  }

  @Test
  @DisplayName("closing the stream twice only logs out the session once")
  void closeIsIdempotent() throws IOException {
    var delegate = new ByteArrayInputStream(new byte[0]);
    var session = new FakeSession();
    var stream = new SessionAwareInputStream(delegate, session);

    stream.close();
    stream.close();

    assertTrue(session.isLoggedOut());
    assertTrue(session.isClosed());
  }
}