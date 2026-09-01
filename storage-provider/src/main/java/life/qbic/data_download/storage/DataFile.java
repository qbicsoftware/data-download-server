package life.qbic.data_download.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * A file together with a stream to its content, as returned by a {@link StorageProvider}.
 *
 * <p>For a {@link ByteRangeProvider}, the stream returned by {@link #inputStream()} starts at the
 * requested range offset.
 */
public interface DataFile {

  /**
   * A stream over the file content. For range requests the stream starts at the range offset.
   */
  InputStream inputStream() throws IOException;

  /**
   * The metadata of the file.
   */
  FileInfo fileInfo();
}