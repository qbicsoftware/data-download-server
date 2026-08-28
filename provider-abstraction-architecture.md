# Storage Provider Abstraction Architecture

## Overview

This document describes the architecture for abstracting storage providers in the data download server. The goal is to support multiple storage backends (NFS, S3, openBIS DSS, etc.) through a unified provider interface while maintaining backward compatibility with existing clients.

## Problem Statement

Currently, the download server is tightly coupled to openBIS DSS for file access. This creates several challenges:

1. **Complex streaming chain**: Client → Download Server → DSS HTTP API → DSS Filesystem
2. **Session management overhead**: DSS sessions can timeout, requiring refresh logic
3. **Limited control**: Cannot optimize for different storage backends
4. **Single point of failure**: DSS issues affect all downloads
5. **Hard to extend**: Adding new storage backends requires significant refactoring

## Solution

Implement a provider abstraction layer that:
- Defines a clean `StorageProvider` interface
- Supports multiple access patterns (InputStream, file path, pre-signed URL)
- Allows gradual migration from openBIS to other backends
- Maintains backward compatibility with existing API endpoints

## Architecture

### High-Level Design

```
┌─────────────────────────────────────────────────────────────┐
│                        Client                                │
│              (curl, browser, scripts)                        │
└────────────────────┬────────────────────────────────────────┘
                     │ HTTP Request
                     │ GET /measurements/{id}/files/{index}
                     ▼
┌─────────────────────────────────────────────────────────────┐
│                  Download Server                             │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  MeasurementFileController                           │  │
│  │  - Validates authorization (existing ACL logic)      │  │
│  │  - Resolves provider from registry                   │  │
│  │  - Streams file to client                            │  │
│  └──────────────────────────────────────────────────────┘  │
│                         │                                    │
│                         ▼                                    │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  Provider Registry                                   │  │
│  │  - Maps dataset IDs to storage providers             │  │
│  │  - Initially: database query (monolith)              │  │
│  │  - Future: separate service                          │  │
│  └──────────────────────────────────────────────────────┘  │
│                         │                                    │
│                         ▼                                    │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  StorageProvider Interface (lean)                    │  │
│  │  - listFiles(datasetId)                              │  │
│  │  - getFile(datasetId, index)                         │  │
│  │  - getFileSize(datasetId, index)                     │  │
│  │  - getFileMetadata(datasetId, index)                 │  │
│  └──────────────────────────────────────────────────────┘  │
│       │           │          │          │                  │
│       │    ┌──────┴──┐  ┌────┴───┐  ┌───┴────┐             │
│       │    │ByteRange│  │FilePath│  │Presigned            │
│       │    │Provider │  │Provider│  │Provider             │
│       │    │  (I/F)  │  │ (I/F)  │  │ (I/F)               │
│       │    └────┬────┘  └───┬────┘  └───┬────┘             │
│       │         │           │           │                  │
│       ▼         ▼           ▼           ▼                  │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐        │
│  │OpenBIS  │ │   NFS   │ │   S3    │ │ (future)│        │
│  │Provider │ │Provider │ │Provider │ │Provider │        │
│  │(Adapter)│ │(Direct) │ │(Direct) │ │         │        │
│  │+Range   │ │+Range   │ │+Range   │ │         │        │
│  │         │ │+Path    │ │+Presigned│ │         │        │
│  └─────────┘ └─────────┘ └─────────┘ └─────────┘        │
└─────────────────────────────────────────────────────────────┘
```

### Component Details

#### 1. StorageProvider Interface

The core interface is kept **lean** by applying the Interface Segregation Principle (ISP): only the methods every provider must implement live here. Optional capabilities are split into separate role interfaces that providers implement only when they support them.

```java
public interface StorageProvider {
    /**
     * List all files in a dataset in stable, deterministic order.
     * The order must be consistent across calls for the same dataset.
     */
    List<FileInfo> listFiles(String datasetId);

    /**
     * Get a file by its index (from the ordered list returned by listFiles).
     * Streams the whole file from the start.
     */
    DataFile getFile(String datasetId, int index);

    /**
     * Get file size without opening the file.
     */
    long getFileSize(String datasetId, int index);

    /**
     * Get file metadata (size, CRC32, timestamps, etc.).
     */
    FileInfo getFileMetadata(String datasetId, int index);
}
```

**File identity.** Files are addressed by their **index** within the stable, deterministic order returned by `listFiles()`. Because that order is guaranteed consistent for a given dataset (and cached via the `MeasurementFileIndex`), index-based addressing is reliable within the cache lifetime and keeps the interface backward-compatible with the existing client contract.

**Capability interfaces** — implemented by providers that support them, never the other way around:

```java
/**
 * Providers that can serve partial content.
 * Enables resumable downloads via byte-range requests.
 */
public interface ByteRangeProvider {
    /**
     * Get a file by its index, honoring a byte-range request.
     * The returned stream starts at the range offset.
     */
    DataFile getFile(String datasetId, int index, ByteRange range);
}

/**
 * Filesystem-backed providers (NFS, local mount).
 * Enables direct NIO operations.
 */
public interface FilePathProvider {
    Optional<Path> getFilePath(String datasetId, int index);
}

/**
 * Cloud-backed providers (S3, Azure Blob).
 * Enables direct client access via pre-signed URLs.
 */
public interface PresignedUrlProvider {
    PresignedUrl getPresignedUrl(String datasetId, int index, ByteRange range)
            throws UrlGenerationException;
}
```

Byte-range support is **removed from the core interface** entirely and moved to the `ByteRangeProvider` capability. A provider that does not implement `ByteRangeProvider` serves whole files only (no `Range`/`Accept-Ranges` handling); a provider that does supports resumable downloads. Consumers detect capabilities with pattern matching instead of default methods:

```java
if (provider instanceof ByteRangeProvider brp) {
    brp.getFile(datasetId, index, range)   // range-aware download
} else {
    provider.getFile(datasetId, index)     // whole-file download
}
```

This means a provider is never forced to depend on a capability it doesn't use, and the capability set is **open-ended** — a future provider can implement a new role interface (e.g. `MultipartUploadProvider`) without touching the core contract.

#### 2. DataFile Interface

```java
public interface DataFile {
    /**
     * Get an InputStream for reading file content.
     * For byte-range requests, the stream starts at the range offset.
     */
    InputStream inputStream() throws IOException;

    /**
     * Get file metadata.
     */
    FileInfo fileInfo();
}
```

#### 3. Exception Taxonomy

The retry strategy (section 5) depends on a **well-defined exception hierarchy**, so it is specified as part of the interface contract:

```
StorageProviderException (checked, base)
├── DatasetNotFoundException      // unknown datasetId
├── FileNotFoundException        // unknown file index
├── TransientException           // retryable: network, 502/503, connection reset
│   ├── ProviderUnavailableException
│   └── NetworkException
├── InvalidByteRangeException    // malformed or out-of-bounds range (416)
├── AuthorizationException       // caller not entitled to this dataset
└── ProviderException            // permanent, non-retryable provider failure
```

**Contract:**
- `TransientException` subclasses are the **only** retryable failures.
- `DatasetNotFoundException` / `FileNotFoundException` are **permanent** (a 404 to the client, no retry).
- `InvalidByteRangeException` is **permanent** (a 416 to the client).
- The taxonomy is what makes the retry decorator testable — it must never catch a bare `Exception`.

#### 4. ByteRange Contract

These semantics apply to providers that implement `ByteRangeProvider` (whole-file-only providers ignore `Range` and never set `Accept-Ranges`). Byte-range semantics must be explicit to avoid off-by-one corruption:

- Ranges are **inclusive on both ends** (`start` to `end`, matching HTTP `Range`), so `bytes=0-99` returns exactly 100 bytes.
- **Suffix ranges** (`bytes=-500`, last 500 bytes) are supported by all range-capable providers; non-native providers emulate them via NIO.
- **Out-of-bounds / invalid** ranges throw `InvalidByteRangeException` (mapped to HTTP 416).
- A `null` range means **the whole file**.

#### 5. Provider Implementations

**OpenBIS Provider (Adapter)**
- Wraps existing `MeasurementDataProvider` code
- Minimal changes to preserve working functionality
- Returns `InputStream` from DSS HTTP API
- Implements `StorageProvider` + `ByteRangeProvider` (resumable downloads preserved)
- Does **not** implement the other capability interfaces

**NFS Provider (Direct)**
- Direct file I/O using Java NIO
- Implements `StorageProvider` + `ByteRangeProvider` + `FilePathProvider`
- Returns both `InputStream` and file `Path`
- Byte-range via NIO positioning
- Optimal performance for mounted storage
- No HTTP overhead

**S3 Provider (Direct)**
- Uses AWS SDK for S3 operations
- Implements `StorageProvider` + `ByteRangeProvider` + `PresignedUrlProvider`
- Returns `InputStream` from S3 GetObject
- Can return pre-signed URLs for direct client access
- Native byte-range support via S3 Range header

#### 6. Provider Registry

```java
public interface ProviderRegistry {
    /**
     * Resolve which provider handles a given dataset.
     */
    StorageProvider getProvider(String datasetId);
}
```

**Initial Implementation (Monolith)**
- Database query to map dataset IDs to provider types
- Configuration in `application.yml` defines available providers
- Example:
  ```yaml
  providers:
    openbis:
      enabled: true
      type: openbis
    nfs:
      enabled: true
      type: nfs
      mount-path: /mnt/data
  ```

**Future Implementation (Service)**
- Separate microservice for provider resolution
- REST API for dataset-to-provider mapping
- Caching for performance

#### 7. Error Handling and Retry Strategy

**Server-side retry (automatic)**:
- Network timeouts between download server and provider
- Transient HTTP errors (503 Service Unavailable, 502 Bad Gateway)
- Connection resets
- Provider SDK retries (S3, NFS)

**Client-side retry (manual)**:
- Authentication failures (401, 403)
- File not found (404)
- Byte-range errors (invalid range)
- Persistent errors after server retries exhausted

**Implementation** (uses the exception taxonomy from section 3 — only `TransientException` subclasses are retried, and backoff is non-blocking). The decorator implements the same capability interfaces as its delegate, forwarding each capability's methods with retry:

```java
public class RetryableStorageProvider implements StorageProvider, ByteRangeProvider {
    private final StorageProvider delegate;
    private final int maxRetries = 3;

    @Override
    public DataFile getFile(String datasetId, int index) {
        return withRetry(() -> delegate.getFile(datasetId, index));
    }

    @Override
    public DataFile getFile(String datasetId, int index, ByteRange range) {
        return withRetry(() -> ((ByteRangeProvider) delegate).getFile(datasetId, index, range));
    }

    private DataFile withRetry(Supplier<DataFile> op) {
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                return op.get();
            } catch (TransientException e) {
                if (attempt == maxRetries - 1) throw e;
                sleepNonBlocking(backoffDelay(attempt));   // async scheduler, not Thread.sleep
            }
        }
        throw new IllegalStateException("unreachable");
    }
}
```

**Circuit breaker**: if a provider fails repeatedly, subsequent requests short-circuit to a fast-fail (`ProviderUnavailableException`) instead of each hitting the provider 3× with backoff.

#### 8. API Endpoints

**Keep existing endpoints** (backward compatible):
- `GET /measurements/{measurementId}/files` - List files (JSON manifest)
- `GET /measurements/{measurementId}/files/{index}` - Download file by index

**Internal changes**:
- Controller uses `StorageProvider` instead of `MeasurementDataProvider`
- Provider registry resolves which provider to use
- No changes to client-facing API

#### 9. Authorization

**No changes needed**:
- Existing `QbicPermissionEvaluator` checks project-level permissions
- Authorization happens before provider is accessed
- Providers use service credentials (not user credentials) to access storage

#### 10. Configuration

**Application properties** (`application.yml`):
```yaml
providers:
  openbis:
    enabled: true
    type: openbis
    session-timeout: 3600
  nfs:
    enabled: true
    type: nfs
    mount-path: /mnt/data
  s3:
    enabled: false
    type: s3
    bucket: my-bucket
    region: eu-central-1

download:
  buffer-size: 1048576  # 1MB
  max-concurrent-per-user: 5
  max-bandwidth-per-user: 1073741824  # 1Gbps
```

#### 11. Logging and Monitoring

**Initial implementation** (file-based):
- Plain text logs for file output
- Structured logs for other outputs (JSON when not writing to files)

**Log events**:
- Download start: user, dataset, file, provider
- Download progress: bytes transferred, throughput, elapsed time
- Download completion: total bytes, duration
- Provider interactions: request time, response time, errors
- Errors: provider errors, network errors, client disconnects

**Future enhancements**:
- Prometheus metrics endpoint
- OpenTelemetry distributed tracing
- Grafana dashboards

## Implementation Plan

### Phase 1: Foundation (Week 1-2)

**Goal**: Define interfaces and create provider abstraction layer

**Tasks**:
1. Define `StorageProvider` and `DataFile` interfaces
2. Create `OpenBisStorageProvider` adapter (wraps existing code)
3. Create `ProviderRegistry` interface and initial implementation
4. Add configuration properties for providers
5. Unit tests for interfaces and registry

**Deliverables**:
- `StorageProvider` interface
- `DataFile` interface
- `OpenBisStorageProvider` adapter
- `ProviderRegistry` implementation
- Configuration schema
- Unit tests

### Phase 2: Controller Refactoring (Week 3-4)

**Goal**: Update controllers to use new provider interface

**Tasks**:
1. Create new `MeasurementFileControllerV2` using `StorageProvider`
2. Keep existing `MeasurementFileController` unchanged
3. Add feature flag to switch between old and new controllers
4. Integration tests for new controller
5. Performance testing (compare old vs new)

**Deliverables**:
- `MeasurementFileControllerV2`
- Feature flag configuration
- Integration tests
- Performance comparison report

### Phase 3: NFS Provider (Week 5-6)

**Goal**: Implement NFS provider for direct file I/O

**Tasks**:
1. Implement `NfsStorageProvider` with direct file I/O
2. Support both `InputStream` and file `Path` access
3. Implement byte-range support using NIO
4. Integration tests with mounted NFS storage
5. Performance testing (compare NFS vs openBIS)

**Deliverables**:
- `NfsStorageProvider` implementation
- NFS configuration
- Integration tests
- Performance benchmarks

### Phase 4: Testing and Validation (Week 7-8)

**Goal**: Thoroughly test the new architecture

**Tasks**:
1. End-to-end testing with real datasets
2. Load testing (concurrent downloads, large files)
3. Error scenario testing (network failures, provider errors)
4. Security review (authorization, input validation)
5. Documentation updates

**Deliverables**:
- Test reports
- Performance benchmarks
- Security review document
- Updated documentation

### Phase 5: Gradual Rollout (Week 9-10)

**Goal**: Deploy new architecture to production

**Tasks**:
1. Deploy to staging environment
2. Monitor for issues (logs, metrics)
3. Gradually enable for test users
4. Collect feedback
5. Enable for all users
6. Decommission old controller (after validation period)

**Deliverables**:
- Deployment runbook
- Monitoring dashboards
- Rollback plan
- Post-deployment report

### Phase 6: S3 Provider (Future)

**Goal**: Add S3 provider for cloud storage

**Tasks**:
1. Implement `S3StorageProvider` using AWS SDK
2. Support pre-signed URLs for direct client access
3. Implement byte-range support via S3 Range header
4. Integration tests with S3 bucket
5. Performance testing

**Deliverables**:
- `S3StorageProvider` implementation
- S3 configuration
- Integration tests
- Performance benchmarks

## Migration Strategy

### Parallel Implementation

**Approach**: Build new provider-based controllers alongside existing ones

**Benefits**:
- Zero downtime during migration
- Easy rollback if issues arise
- Can test new architecture in production
- Gradual transition for users

**Implementation**:
1. Keep existing `MeasurementFileController` unchanged
2. Create new `MeasurementFileControllerV2` using `StorageProvider`
3. Feature flag to switch between implementations:
   ```yaml
   download:
     controller-version: v1  # or v2
   ```
4. Monitor both implementations in parallel
5. Switch to v2 when validated
6. Remove v1 after stabilization period

### Backward Compatibility

**API endpoints**: No changes to client-facing API
- Same URLs: `/measurements/{id}/files/{index}`
- Same response format (JSON manifest, binary file download)
- Same byte-range support
- Same authorization checks

**Configuration**: Existing configuration remains valid
- New provider configuration is additive
- No breaking changes to existing properties

## Testing Strategy

### Unit Tests

**Scope**: Test individual components in isolation

**Coverage**:
- `StorageProvider` interface implementations
- `ProviderRegistry` logic
- Controller request handling
- Error handling and retry logic

**Tools**:
- JUnit 5
- Mockito for mocking providers
- AssertJ for assertions

### Integration Tests (Future)

**Scope**: Test component interactions

**Coverage**:
- Controller → Provider → Storage backend
- Provider registry resolution
- End-to-end download flows

**Tools**:
- Testcontainers for NFS/S3
- Spring Boot Test
- REST Assured for API testing

### Performance Tests (Future)

**Scope**: Measure throughput and latency

**Metrics**:
- Download speed (MB/s)
- Time to first byte
- Concurrent download capacity
- Memory usage

**Tools**:
- JMeter or Gatling
- Prometheus + Grafana

## Risks and Mitigations

### Risk 1: Performance Regression

**Risk**: New architecture introduces overhead

**Mitigation**:
- Performance testing in Phase 4
- Compare old vs new implementation
- Optimize hot paths (file I/O, buffering)
- NFS provider should be faster (no HTTP overhead)

### Risk 2: Breaking Existing Functionality

**Risk**: New code breaks existing downloads

**Mitigation**:
- Parallel implementation (Phase 2)
- Feature flag for easy rollback
- Extensive testing before rollout
- Monitor logs and metrics closely

### Risk 3: Provider Implementation Bugs

**Risk**: New providers have bugs (NFS, S3)

**Mitigation**:
- Thorough unit and integration tests
- Start with openBIS adapter (proven code)
- Gradual rollout of new providers
- Comprehensive error handling

### Risk 4: Security Vulnerabilities

**Risk**: New code introduces security issues

**Mitigation**:
- Security review in Phase 4
- Keep existing authorization logic unchanged
- Input validation (dataset IDs, file paths)
- No changes to authentication flow

## Future Enhancements

### Phase 7: Monitoring and Observability

**Goal**: Add comprehensive monitoring

**Tasks**:
1. Prometheus metrics endpoint
2. OpenTelemetry distributed tracing
3. Grafana dashboards
4. Alerting rules

**Benefits**:
- Real-time visibility into download performance
- Quick issue detection and resolution
- Capacity planning

### Phase 8: Advanced Features

**Goal**: Add features enabled by provider abstraction

**Features**:
- Download acceleration (parallel chunk downloads)
- Compression on-the-fly (gzip, zstd)
- Format conversion (FASTQ → BAM)
- Download scheduling (queue large downloads)
- Download resumption across sessions

**Benefits**:
- Better user experience
- Reduced server load
- More flexible data access

## Conclusion

This architecture provides a clean abstraction for multiple storage backends while maintaining backward compatibility. The gradual migration approach minimizes risk and allows thorough testing. The provider interface is flexible enough to support current and future storage technologies.

Key benefits:
- **Simplified architecture**: Remove producer-consumer complexity
- **Better performance**: Direct file I/O for NFS, no HTTP overhead
- **Easier maintenance**: Clean separation of concerns
- **Future-proof**: Easy to add new storage backends
- **Backward compatible**: No changes to client-facing API

## Appendix

### A. Glossary

- **Dataset**: A container with one or more files (currently called "measurement")
- **Index**: The zero-based position of a file within the stable, ordered list returned by `listFiles()`
- **Provider**: A storage backend implementation (openBIS, NFS, S3)
- **Capability interface**: A role interface (`ByteRangeProvider`, `FilePathProvider`, `PresignedUrlProvider`) implemented only by providers that support the capability
- **Registry**: Maps dataset IDs to storage providers
- **Byte Range**: A subset of a file (e.g., bytes 1000-2000, inclusive on both ends; `bytes=-500` denotes the last 500 bytes)

### B. References

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [AWS SDK for Java](https://aws.amazon.com/sdk-for-java/)
- [Java NIO](https://docs.oracle.com/javase/8/docs/api/java/nio/package-summary.html)
- [Spring Security ACL](https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html)

### C. Decision Log

| Decision | Rationale | Alternatives Considered |
|----------|-----------|------------------------|
| Keep existing API endpoints | Backward compatibility, no client changes | New endpoints (broke clients) |
| Remove producer-consumer queue | Unnecessary complexity, didn't solve root cause | Keep queue for all providers |
| Adapter pattern for openBIS | Preserve working code, low risk | Rewrite openBIS provider |
| Parallel implementation | Zero downtime, easy rollback | Big refactor (risky) |
| Application properties config | Simple, standard Spring Boot approach | Database config (overkill) |
| Unit tests only (for now) | Fast feedback, low maintenance | Integration tests (complex setup) |
| File-based logging first | Simple, easy to implement | Full observability (overkill initially) |
| Index-based core addressing | Matches existing client contract; stable within cache lifetime | FileId-based addressing (stable across source changes) |
| Capability interfaces (ISP) | Lean core; providers implement only what they support; open-ended | Fat interface with default methods |
| Byte-range as capability | Whole-file and resumable providers share a lean core; range is opt-in | Range arg in the core getFile for all providers |
| Exception taxonomy | Makes retry strategy testable and precise | Catch-all exception handling |
