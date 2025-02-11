package life.qbic.data_download.measurements.api;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * PathFormatter applies filters to a given String value that represents a path.
 *
 * @since 1.1.0
 */
public class PathFormatter {

  private final List<String> filter;

  private PathFormatter() {
    filter = new ArrayList<>();
  }

  private PathFormatter(Collection<String> filter) {
    this.filter = new ArrayList<>(filter);
  }

  /**
   * Creates a new instance of {@link PathFormatter} with a collection of filter regex expression.
   *
   * @param filter the terms to filter out
   * @return a {@link PathFormatter} instance
   * @since 1.1.0
   */
  public static PathFormatter with(Collection<String> filter) {
    Objects.requireNonNull(filter);
    return new PathFormatter(filter);
  }

  private static String apply(Path p, Collection<String> filter) {
    Path result = p.isAbsolute() ? p.getRoot() : Paths.get("");

    for (Path part : p) {
      if (matchesAny(part.toString(), filter)) {
        continue;
      }
      result = result.resolve(part);
    }
    return result.toString();
  }

  private static boolean matchesAny(String s, Collection<String> filter) {
    return filter.stream().anyMatch(s::matches);
  }

  /**
   * Applies the configured filter to the provided String.
   * <p>
   * The provided String is expected to be a valid {@link java.nio.file.Path}, otherwise an
   * {@link InvalidPathException} is thrown.
   * <p>
   * Format iterates through every part of the path and checks them agains the configured filters.
   * <p>
   * The filter matching works the same as {@link Pattern#matches(String, CharSequence)}.
   * <p>
   * The resulting String representation of a path will be removed of any matching filter terms.
   *
   * <pre>
   *   List<String> filters = Arrays.asList("removeme");
   *   var formatter = PathFormatter.with(filters);
   *
   *   System.out.println(formatter("/example/removeme/path"));
   *
   *   // Prints out "/example/path"
   * </pre>
   * <p>
   * Format has an implicit check for {@link Path#isAbsolute()} and preserves an absolute or
   * relative path.
   *
   * @param s the String value to format
   * @return the formatted new String, after the filter has been applied
   * @throws InvalidPathException in case the String is not a valid path
   * @since 1.1.0
   */
  public String format(String s) throws InvalidPathException {
    return apply(Path.of(s), filter);
  }
}
