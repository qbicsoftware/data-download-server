# Data Download Server

Spring Boot service for downloading (bio)measurement data via the QBiC download-API.

## Configuration

The service is configured via an **external `application.properties` file**. Place it in the
directory from which the JAR is launched, or in a `config/` sub-directory:

```
rest-server-1.0.10.jar
config/
  └── application.properties
```

Spring Boot automatically loads this external file and it **overrides** the settings bundled
inside the JAR. This replaces the previous environment-variable based configuration: all
settings (openBIS credentials, database, token salt, storage providers, ports, etc.) are now
documented and editable in one transparent place.

A commented template is available at:
[`rest-api/src/dist/application.properties`](rest-api/src/dist/application.properties)

### Env vars still work (optional)

Spring Boot's property precedence still applies, so specific values can be overridden without
editing the file, e.g. `SERVER_PORT=9000 java -jar rest-server.jar`. In that case the env var
wins over the external file.

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

## API documentation

Run the server and visit
[http://localhost:8090/swagger-ui.html](http://localhost:8090/swagger-ui.html)
(port depends on your configuration; default `8090`).