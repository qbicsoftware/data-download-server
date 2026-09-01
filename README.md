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

## API documentation

Run the server and visit
[http://localhost:8090/swagger-ui.html](http://localhost:8090/swagger-ui.html)
(port depends on your configuration; default `8090`).