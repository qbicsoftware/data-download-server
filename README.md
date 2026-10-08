# Data Download Server

[![dependency vulnerabilities](https://img.shields.io/endpoint?url=https://gist.githubusercontent.com/KochTobi/73f06fda8116a3ca71bb27546a7ae719/raw/vulnerabilities.json)](https://github.com/qbicsoftware/data-download-server/security/code-scanning)

Spring Boot service for downloading (bio)measurement data via the QBiC download-API.

## Configuration

The service is configured via an external `application.properties` file. A commented,
fully-documented template is available at
[`rest-api/src/dist/application.properties`](rest-api/src/dist/application.properties).

### Loading the external file (important)

Spring Boot looks for `application.properties` relative to the **process working directory**
(the directory you are *in* when you launch `java`), **not** relative to the JAR file's own
location. If you just drop the file "next to the JAR" it is very easy for Spring to not find
it — that happens whenever the JAR is launched from a different directory (or under systemd /
a service manager that does not set the working directory).

To make loading deterministic, always pass the file by absolute path on the command line:

```
java -jar /opt/app/rest-server-1.3.0.jar \
  --spring.config.additional-location=/opt/app/application.properties
```

Use `--spring.config.additional-location` (not `--spring.config.location`) so the bundled
defaults inside the JAR are still used for any key the external file does not set. The
`file:` prefix is optional when the path is absolute.

Alternatively, when running under systemd, set `WorkingDirectory` so the file is found
relative to it:

```ini
[Service]
WorkingDirectory=/opt/app
ExecStart=/usr/bin/java -jar /opt/app/rest-server-1.3.0.jar
```

### Env vars still work (optional)

Spring Boot's property precedence still applies, so specific values can be overridden without
editing the file, e.g. `SERVER_PORT=9000 java -jar rest-server.jar`. The env var wins over
the external file. The bundled `application.properties` inside the JAR also still supports the
original environment-variable placeholders for backward compatibility.

## Building for production

### Prerequisites

- JDK 21
- Maven 3.x

### Build everything

The project is a multi-module Maven build:

```
data-download-server (POM parent)
├── zip                        (library)
├── measurement-provider       (library)
├── storage-provider           (library)
├── openbis-connector          (library)
└── rest-api                   (Spring Boot application "rest-server")
```

The four library modules are **not** standalone services - they are dependencies of the
Spring Boot application in `rest-api`. Building a package compiles every module and bundles
all four libraries into a single executable JAR.

From the project root:

```bash
# Build everything, including tests
mvn package

# Build everything, skip tests (faster, for a release artifact)
mvn package -DskipTests
```

This produces one deployable artifact:

```
rest-api/target/rest-server-<version>.jar
```

### Build only the application (faster)

The `-am` flag ("also make") builds `rest-api` together with all upstream modules it
depends on - so this produces the same deployable JAR, but skips nothing it needs:

```bash
mvn -pl rest-api -am package
```

### Clean rebuild

```bash
mvn -pl rest-api -am clean package
```

### Deploy to Nexus (release)

```bash
mvn deploy
```

Publishes all modules to the QBiC Nexus repository configured in `distributionManagement`.

## Versioning

The whole project uses **one version, defined in one place**: the `<revision>` property in the
root [`pom.xml`](pom.xml). All modules inherit this version and their build artifacts are named
accordingly (e.g. `rest-server-1.3.0.jar`).

To change the project version, edit the single property:

```xml
<properties>
  <revision>1.3.0</revision>
</properties>
```

There is no per-module `<version>` anywhere - child POMs reference the parent via
`${revision}` and inter-module dependencies via `${project.version}`, so a bump is
applied consistently to every module in one edit. The release workflow bumps this property
automatically (`.github/workflows/create-release.yml`).

## API documentation

Run the server and visit
[http://localhost:8090/swagger-ui.html](http://localhost:8090/swagger-ui.html)
(port depends on your configuration; default `8090`).

## Security

Dependencies are scanned for known CVEs daily (and on pushes to `main`/`development` and PRs to
`main`) by the
[Dependency Vulnerability Scan](.github/workflows/dependency-vulnerability-scan.yml) workflow.
It builds a CycloneDX SBOM and scans it with [Grype](https://github.com/anchore/grype). Findings
are published to **Security → Code scanning** and as a workflow artifact, and summarised in the
run's job summary.

- Process, mitigations and how to accept a risk: [`docs/security/vulnerability-management.md`](docs/security/vulnerability-management.md)
- Accepted risks (reason + expiry required): [`.github/grype.yaml`](.github/grype.yaml)

The badge at the top of this README is an endpoint badge served from a gist written by the
workflow. Only `main` publishes it, so a `development` or PR run never overwrites it. See the
one-time badge setup in the [vulnerability management doc](docs/security/vulnerability-management.md#5-one-time-setup-for-the-badge).
