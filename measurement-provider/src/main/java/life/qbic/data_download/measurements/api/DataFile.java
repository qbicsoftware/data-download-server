package life.qbic.data_download.measurements.api;

import static java.util.Objects.requireNonNull;

import java.io.InputStream;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * A file with data
 */
public class DataFile {

  private final FileInfo fileInfo;
  private final InputStream inputStream;

  public DataFile(FileInfo fileInfo, InputStream inputStream) {
    this.fileInfo = requireNonNull(fileInfo, "fileInfo must not be null");
    this.inputStream = requireNonNull(inputStream, "inputStream must not be null");
  }

  public FileInfo fileInfo() {
    return fileInfo;
  }

  public InputStream inputStream() {
    return inputStream;
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", DataFile.class.getSimpleName() + "[", "]")
        .add("fileInfo=" + fileInfo)
        .add("inputStream=" + inputStream)
        .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    DataFile dataFile = (DataFile) o;
    return fileInfo.equals(dataFile.fileInfo) && Objects.equals(inputStream,
        dataFile.inputStream);
  }

  @Override
  public int hashCode() {
    int result = fileInfo.hashCode();
    result = 31 * result + Objects.hashCode(inputStream);
    return result;
  }
}
