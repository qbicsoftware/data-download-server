package life.qbic.data_download.rest.download;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import life.qbic.data_download.rest.exceptions.GlobalException;
import life.qbic.data_download.rest.exceptions.GlobalException.ErrorCode;
import life.qbic.data_download.rest.exceptions.GlobalException.ErrorParameters;
import org.springframework.stereotype.Component;

/**
 * Parses a single RFC 7233 byte range. Only a single range (e.g. {@code bytes=0-499} or
 * {@code bytes=500-}) is supported. Suffix ranges ({@code bytes=-500}) are not supported and are
 * treated as unsatisfiable.
 */
@Component
public class ByteRange {

  private static final Pattern SINGLE_RANGE_PATTERN = Pattern.compile("bytes=(\\d+)-(\\d*)");

  /**
   * An inclusive byte range.
   *
   * @param start     the first byte offset, inclusive
   * @param end       the last byte offset, inclusive
   * @param isPartial whether this represents a partial (206) request rather than the full file
   */
  public record Range(long start, long end, boolean isPartial) {

    long length() {
      return end - start + 1;
    }
  }

  /**
   * Parses the {@code Range} request header against a file of the given length.
   *
   * @param rangeHeader the value of the Range header, or {@code null} if absent
   * @param fileLength  the total length of the file in bytes
   * @return the resolved byte range; a {@code null}/{@code blank} header yields the full file
   * @throws GlobalException with {@link ErrorCode#RANGE_NOT_SATISFIABLE} when the range is invalid
   *                         or unsatisfiable
   */
  public Range parse(String rangeHeader, long fileLength) {
    if (rangeHeader == null || rangeHeader.isBlank()) {
      return new Range(0, fileLength - 1, false);
    }
    Matcher matcher = SINGLE_RANGE_PATTERN.matcher(rangeHeader.trim());
    if (!matcher.matches()) {
      throw unsatisfiable(fileLength);
    }
    long start = Long.parseLong(matcher.group(1));
    String endGroup = matcher.group(2);
    long end = endGroup.isBlank() ? fileLength - 1 : Long.parseLong(endGroup);
    if (start >= fileLength || end < start) {
      throw unsatisfiable(fileLength);
    }
    end = Math.min(end, fileLength - 1);
    return new Range(start, end, true);
  }

  private GlobalException unsatisfiable(long fileLength) {
    return new GlobalException("range not satisfiable", ErrorCode.RANGE_NOT_SATISFIABLE,
        ErrorParameters.of(fileLength));
  }
}