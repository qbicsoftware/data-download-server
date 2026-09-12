package life.qbic.data_download.openbis;

import static java.util.Objects.requireNonNull;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;
import java.io.InputStream;
import life.qbic.data_download.openbis.SessionFactory.OpenBisSession;
import org.slf4j.Logger;

/**
 * Wraps an {@link InputStream} that is bound to an {@link OpenBisSession}. The underlying stream is
 * backed by a connection/session on the openBIS data store server, so the session must stay alive
 * until the stream has been fully consumed.
 *
 * <p>Closing this stream closes the wrapped stream first and then logs out of the openBIS session.
 * Closing is idempotent, so the session is only released once even if {@link #close()} is called
 * multiple times.
 */
public class SessionAwareInputStream extends InputStream {

  private final InputStream delegate;
  private final OpenBisSession session;
  private boolean closed;

  public SessionAwareInputStream(InputStream delegate, OpenBisSession session) {
    this.delegate = requireNonNull(delegate, "delegate must not be null");
    this.session = requireNonNull(session, "session must not be null");
  }

  @Override
  public int read() throws IOException {
    return delegate.read();
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    session.getToken();
    return delegate.read(b, off, len);
  }

  @Override
  public long skip(long n) throws IOException {
    return delegate.skip(n);
  }

  @Override
  public int available() throws IOException {
    return delegate.available();
  }

  @Override
  public void close() throws IOException {
    if (closed) {
      return;
    }
    closed = true;
    IOException failure = null;
    try {
      delegate.close();
    } catch (IOException e) {
      failure = e;
    }
    try {
      session.close();
    } catch (RuntimeException e) {
      if (failure == null) {
        failure = new IOException("Could not close the openBIS session.", e);
      } else {
        failure.addSuppressed(e);
      }
    }
    if (failure != null) {
      throw failure;
    }
  }
}
