package life.qbic.data_download.rest.download;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import life.qbic.data_download.rest.exceptions.GlobalException;
import life.qbic.data_download.rest.exceptions.GlobalException.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ByteRangeTest {

  private final ByteRange byteRange = new ByteRange();

  @Test
  @DisplayName("no range header returns the whole file as a full response")
  void noRangeReturnsWholeFile() {
    ByteRange.Range range = byteRange.parse(null, 1000);
    assertEquals(0, range.start());
    assertEquals(999, range.end());
    assertTrue(!range.isPartial());
    assertEquals(1000, range.length());
  }

  @Test
  @DisplayName("blank range header returns the whole file")
  void blankRangeReturnsWholeFile() {
    ByteRange.Range range = byteRange.parse("   ", 1000);
    assertEquals(0, range.start());
    assertEquals(999, range.end());
    assertTrue(!range.isPartial());
  }

  @Test
  @DisplayName("single bounded range is parsed correctly")
  void singleBoundedRange() {
    ByteRange.Range range = byteRange.parse("bytes=100-199", 1000);
    assertEquals(100, range.start());
    assertEquals(199, range.end());
    assertTrue(range.isPartial());
    assertEquals(100, range.length());
  }

  @Test
  @DisplayName("open-ended range is bounded by the file length")
  void openEndedRangeIsBoundedByFileLength() {
    ByteRange.Range range = byteRange.parse("bytes=950-", 1000);
    assertEquals(950, range.start());
    assertEquals(999, range.end());
    assertTrue(range.isPartial());
    assertEquals(50, range.length());
  }

  @Test
  @DisplayName("range end beyond file length is clamped")
  void rangeEndBeyondFileLengthIsClamped() {
    ByteRange.Range range = byteRange.parse("bytes=0-5000", 1000);
    assertEquals(0, range.start());
    assertEquals(999, range.end());
  }

  @Test
  @DisplayName("range starting beyond the file is unsatisfiable")
  void rangeStartingBeyondFileIsUnsatisfiable() {
    GlobalException exception = assertThrows(GlobalException.class,
        () -> byteRange.parse("bytes=1000-", 1000));
    assertEquals(ErrorCode.RANGE_NOT_SATISFIABLE, exception.errorCode());
  }

  @Test
  @DisplayName("inverted range is unsatisfiable")
  void invertedRangeIsUnsatisfiable() {
    GlobalException exception = assertThrows(GlobalException.class,
        () -> byteRange.parse("bytes=500-100", 1000));
    assertEquals(ErrorCode.RANGE_NOT_SATISFIABLE, exception.errorCode());
  }

  @Test
  @DisplayName("suffix range is not supported and unsatisfiable")
  void suffixRangeIsUnsatisfiable() {
    GlobalException exception = assertThrows(GlobalException.class,
        () -> byteRange.parse("bytes=-500", 1000));
    assertEquals(ErrorCode.RANGE_NOT_SATISFIABLE, exception.errorCode());
  }

  @Test
  @DisplayName("multi-range header is not supported and unsatisfiable")
  void multiRangeIsUnsatisfiable() {
    GlobalException exception = assertThrows(GlobalException.class,
        () -> byteRange.parse("bytes=0-499,500-999", 1000));
    assertEquals(ErrorCode.RANGE_NOT_SATISFIABLE, exception.errorCode());
  }
}