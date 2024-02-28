package life.qbic.data_access.util.zip.manipulation;

import static java.util.Objects.isNull;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import life.qbic.data_access.util.zip.api.FileInfo;

public class BufferedZippingFunctions {

  private BufferedZippingFunctions() {
    //no need to instantiate. Static class
  }


  private static final int DEFAULT_BUFFER_SIZE = 1024; //1 KB buffer


  public static ZipOutputStream zipInto(OutputStream outputStream) {
    return zipInto(outputStream, ZipOutputStream.STORED);
  }

  /**
   * @param outputStream the output stream to zip into
   * @param method the method to use
   * @return a zip output stream zipping into the output stream using the zipping method
   */
  public static ZipOutputStream zipInto(OutputStream outputStream, int method) {
    ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream);
    zipOutputStream.setMethod(method);
    return zipOutputStream;
  }

  public static void addToZip(ZipOutputStream zipOutputStream, FileInfo fileInfo,
      InputStream inputStream) {
    addToZip(zipOutputStream, fileInfo, inputStream, DEFAULT_BUFFER_SIZE);
  }

  public static void addToZip(ZipOutputStream zipOutputStream, FileInfo fileInfo,
      InputStream inputStream, int bufferSize) {
    requireNonNull(zipOutputStream, "zipOutputStream must not be null");
    requireNonNull(fileInfo, "fileInfo must not be null");
    if (bufferSize <= 0) {
      throw new IllegalArgumentException("buffer size must be greater than 0");
    }
    if (isNull(fileInfo.filePath()) || fileInfo.filePath().isBlank()) {
      throw new IllegalArgumentException("no file path provided");
    }
    ZipEntry zipEntry = new ZipEntry(fileInfo.filePath());
    zipEntry.setSize(fileInfo.fileSize());
    zipEntry.setCrc(fileInfo.expectedCrc());
    fileInfo.fileTimes().creationTime().ifPresent(zipEntry::setCreationTime);
    fileInfo.fileTimes().lastModifiedTime().ifPresent(zipEntry::setLastModifiedTime);
    fileInfo.fileTimes().lastAccessTime().ifPresent(zipEntry::setLastAccessTime);
    try {
      zipOutputStream.putNextEntry(zipEntry);
      byte[] buffer = new byte[bufferSize];
      int length;
      while ((length = inputStream.read(buffer)) >= 0) {
        zipOutputStream.write(buffer, 0, length);
      }
    } catch (IOException e) {
      throw new ZippingException("Could not add file: " + fileInfo.filePath(), e);
    }
  }


}
