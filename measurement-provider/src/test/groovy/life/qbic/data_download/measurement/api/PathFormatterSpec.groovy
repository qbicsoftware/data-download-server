package life.qbic.data_download.measurement.api

import life.qbic.data_download.measurements.api.PathFormatter
import spock.lang.Specification

class PathFormatterSpec extends Specification {

    def "Given a String with a to filtered part, the filtered part must not be part of the resulting String"() {
        given:
        var filter = Arrays.asList("awesome", "wehaa")

        and:
        var f = PathFormatter.with(filter)

        when:
        var result = f.format(input)

        then:
        result == expected

        where:
        input | expected
        "/my/awesome/path/wehaa" | "/my/path"
        "a/relative/wehaa/awesome/path" | "a/relative/path"
    }

    def "Given a String that contains a UUID-4 in its path, the formatter shall filter it out"() {
        given:
        var filter = Arrays.asList("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\$")

        and:
        var f = PathFormatter.with(filter)

        when:
        var result = f.format(input)

        then:
        result == expected

        where:
        input | expected
        "/my/f47ac10b-58cc-4372-a567-0e02b2c3d479/path/" | "/my/path"
        "a/relative/f47ac10b-58cc-4372-a567-0e02b2c3d479/path" | "a/relative/path"
    }
}
