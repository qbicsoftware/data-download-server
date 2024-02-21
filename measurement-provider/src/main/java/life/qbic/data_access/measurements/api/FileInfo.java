package life.qbic.data_access.measurements.api;

/**
 * TODO!
 * <b>short description</b>
 *
 * <p>detailed description</p>
 *
 * @since <version tag>
 */
public record FileInfo(String path, long length, long crc32, long registrationMillis, long lastModifiedMillis) {

}
