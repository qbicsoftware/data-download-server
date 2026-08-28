package life.qbic.data_download.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import life.qbic.data_download.storage.exception.InvalidByteRangeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ByteRangeTest {

  // --- FromToRange ---

  @Test
  @DisplayName("FromToRange resolves to inclusive start and end")
  void fromToResolves() {
    ByteRange.ResolvedRange r = new FromToRange(0, 99).resolve(1000);
    assertEquals(0, r.start());
    assertEquals(99, r.end());
    assertEquals(100, r.length());
  }

  @Test
  @DisplayName("FromToRange clamps an end beyond the file size")
  void fromToClampsEnd() {
    ByteRange.ResolvedRange r = new FromToRange(950, 2000).resolve(1000);
    assertEquals(950, r.start());
    assertEquals(999, r.end());
    assertEquals(50, r.length());
  }

  @Test
  @DisplayName("FromToRange throws for a start beyond the file size")
  void fromToThrowsForOutOfBoundsStart() {
    assertThrows(InvalidByteRangeException.class, () -> new FromToRange(1000, 2000).resolve(1000));
  }

  @Test
  @DisplayName("FromToRange throws for an empty file")
  void fromToThrowsForEmptyFile() {
    assertThrows(InvalidByteRangeException.class, () -> new FromToRange(0, 99).resolve(0));
  }

  @Test
  @DisplayName("FromToRange rejects a negative start and end before start")
  void fromToRejectsInvalidBounds() {
    assertThrows(IllegalArgumentException.class, () -> new FromToRange(-1, 5));
    assertThrows(IllegalArgumentException.class, () -> new FromToRange(10, 9));
  }

  // --- FromStartRange ---

  @Test
  @DisplayName("FromStartRange resolves to the end of the file")
  void fromStartResolves() {
    ByteRange.ResolvedRange r = new FromStartRange(9500).resolve(10000);
    assertEquals(9500, r.start());
    assertEquals(9999, r.end());
    assertEquals(500, r.length());
  }

  @Test
  @DisplayName("FromStartRange throws for a start beyond the file size")
  void fromStartThrowsForOutOfBoundsStart() {
    assertThrows(InvalidByteRangeException.class, () -> new FromStartRange(10000).resolve(10000));
  }

  // --- SuffixRange ---

  @Test
  @DisplayName("SuffixRange resolves to the last N bytes")
  void suffixResolves() {
    ByteRange.ResolvedRange r = new SuffixRange(500).resolve(10000);
    assertEquals(9500, r.start());
    assertEquals(9999, r.end());
    assertEquals(500, r.length());
  }

  @Test
  @DisplayName("SuffixRange uses the whole file when it is shorter than the suffix length")
  void suffixUsesWholeFileWhenShorter() {
    ByteRange.ResolvedRange r = new SuffixRange(2000).resolve(1000);
    assertEquals(0, r.start());
    assertEquals(999, r.end());
    assertEquals(1000, r.length());
  }

  @Test
  @DisplayName("SuffixRange rejects a non-positive length")
  void suffixRejectsNonPositiveLength() {
    assertThrows(IllegalArgumentException.class, () -> new SuffixRange(0));
    assertThrows(IllegalArgumentException.class, () -> new SuffixRange(-1));
  }

  // --- ByteRangeParser (RFC 9110 header values) ---

  @Test
  @DisplayName("parser handles int-range with both bounds")
  void parseIntRange() {
    ByteRange range = ByteRangeParser.parse("bytes=0-499");
    assertEquals(new FromToRange(0, 499), range);
  }

  @Test
  @DisplayName("parser handles open-ended int-range")
  void parseOpenEndedRange() {
    ByteRange range = ByteRangeParser.parse("bytes=9500-");
    assertEquals(new FromStartRange(9500), range);
  }

  @Test
  @DisplayName("parser handles suffix-range")
  void parseSuffixRange() {
    ByteRange range = ByteRangeParser.parse("bytes=-500");
    assertEquals(new SuffixRange(500), range);
  }

  @Test
  @DisplayName("parser returns null for a blank header")
  void parseBlankReturnsNull() {
    assertEquals(null, ByteRangeParser.parse(null));
    assertEquals(null, ByteRangeParser.parse(" "));
  }

  @Test
  @DisplayName("parser rejects a malformed header")
  void parseRejectsMalformed() {
    assertThrows(InvalidByteRangeException.class, () -> ByteRangeParser.parse("bytes=-"));
    assertThrows(InvalidByteRangeException.class, () -> ByteRangeParser.parse("items=0-99"));
  }
}