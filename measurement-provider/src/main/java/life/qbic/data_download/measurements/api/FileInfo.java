package life.qbic.data_download.measurements.api;

/**
 * Information about a file
 */
public record FileInfo(String path, long length, long crc32, long registrationMillis, long lastModifiedMillis) {

}
