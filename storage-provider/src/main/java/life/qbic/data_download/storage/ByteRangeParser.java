package life.qbic.data_download.storage;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import life.qbic.data_download.storage.exception.InvalidByteRangeException;

/**
 * Parses a single byte-range request from an HTTP {@code Range} header value, per RFC 9110 §14.2
 * and §14.1.2. Only a single range spec is supported.
 *
 * <p>Accepted forms:
 *
 * <ul>
 *   <li>{@code bytes=0-499} → {@link FromToRange}</li>
 *   <li>{@code bytes=9500-} → {@link FromStartRange}</li>
 *   <li>{@code bytes=-500} → {@link SuffixRange}</li>
 * </ul>
 *
 * <p>A blank or {@code null} header yields an empty result, which callers interpret as the whole
 * file. Malformed values or multiple range specs throw {@link InvalidByteRangeException}.
 */
public final class ByteRangeParser {

  private static final Pattern BYTE_RANGE = Pattern.compile("bytes=([0-9]*)(-)([0-9]*)");

  private ByteRangeParser() {
  }

  /**
   * Parses a single byte-range-spec from a {@code Range} header value.
   *
   * @param rangeHeader the {@code Range} header value, or {@code null}/{@code blank} for none
   * @return the parsed {@link ByteRange}, or {@code null} if the header is absent/blank
   * @throws InvalidByteRangeException if the header is malformed or contains multiple ranges
   */
  public static ByteRange parse(String rangeHeader) {
    if (rangeHeader == null || rangeHeader.isBlank()) {
      return null;
    }
    Matcher matcher = BYTE_RANGE.matcher(rangeHeader.trim());
    if (!matcher.matches()) {
      throw new InvalidByteRangeException("malformed Range header: " + rangeHeader);
    }
    String first = matcher.group(1);
    String last = matcher.group(3);
    if (first.isEmpty() && last.isEmpty()) {
      throw new InvalidByteRangeException("malformed Range header: " + rangeHeader);
    }
    if (first.isEmpty()) {
      // suffix-range: "-length" -> last N bytes
      return new SuffixRange(parseLong(last, rangeHeader));
    }
    long start = parseLong(first, rangeHeader);
    if (last.isEmpty()) {
      // int-range with absent last-pos: "start-"
      return new FromStartRange(start);
    }
    // int-range with both bounds: "start-end"
    return new FromToRange(start, parseLong(last, rangeHeader));
  }

  private static long parseLong(String digits, String header) {
    try {
      return Long.parseLong(digits);
    } catch (NumberFormatException e) {
      throw new InvalidByteRangeException("range offset out of range: " + header);
    }
  }
}