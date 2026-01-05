# Security Findings Report – Defense Package

## Context

Evaluation of the cyber defense mechanisms in the server-side `defense` package and the integration in the server stack. Focus on load protection (shedding), rate limits, and their effectiveness in different runtime environments.

## Findings

### 1) Rate limit only after successful authentication

**Description**  
The rate limit for “authenticated” requests is only applied after successful authentication. Flooding with invalid or missing tokens remains unlimited.

**Affected location**  
`server/src/main/kotlin/straightway/mosa/ServerConfig.kt:125`

**Risk**  
Attackers can trigger authentication and parsing costs without being slowed down by rate limits, which increases the DoS attack surface.

**Mitigation idea**  
Add an upstream IP-based rate limit for all `/v1/*` requests (regardless of authentication), or a separate limit keyed by IP for failed authentications before JWT parsing.

### 2) CPU load shedding is not container-robust

**Description**  
CPU shedding is based on `systemLoadAverage`. In containers or certain JVMs, this value can return `-1`, causing the system to incorrectly assume a load of 0.

**Affected location**  
`server/src/main/kotlin/straightway/mosa/defense/cpu/CpuSheddingDecider.kt:54`

**Risk**  
In environments without a valid load average, CPU shedding is completely absent; overload is not detected.

**Mitigation idea**  
Fall back to container-compatible metrics (e.g., CGroup CPU usage or `OperatingSystemMXBean` process CPU usage, if available) and allow explicit configuration for the preferred measurement method.

### 3) Memory shedding ignores off-heap and MMap usage

**Description**  
The memory decision is based solely on heap usage. Off-heap memory (Lucene `MMapDirectory`, ONNX Runtime, Direct Buffers) is not taken into account.

**Affected location**  
`server/src/main/kotlin/straightway/mosa/defense/memory/MemorySheddingDecider.kt:56`

**Risk**  
OOM risks can occur despite low heap utilization; shedding reacts too late or not at all.

**Mitigation idea**  
Include non-heap/total memory metrics (e.g., `MemoryMXBean.nonHeapMemoryUsage`, `BufferPoolMXBean`, OS-level RSS) or enforce a configurable process-memory guard that triggers shedding before OOM.

### 4) Retry-After in rate limit is static and inaccurate

**Description**  
`Retry-After` is calculated from `permitsPerMinute` and is not linked to the actual token refill of the respective bucket instance.

**Affected location**  
`server/src/main/kotlin/straightway/mosa/defense/ratelimit/RateLimiter.kt:14`

**Risk**  
Clients receive unreliable backoff notices, which can trigger unnecessary retry spikes.

**Mitigation idea**  
Calculate `Retry-After` per key based on the current token status and refill time instead of a static approximation.

### 5) Lack of protection mechanisms beyond shedding and rate limits

**Description**  
The current defense stack covers peak loads via shedding and rate limits, but there is a lack of complementary cyber defense mechanisms for abuse and anomaly protection as well as request-side hardening.

**Affected area**  
System-wide, no specific code path. No additional protection plugins are visible in the current server pipeline.

**Risk**  
Increased attack surface for payload abuse, automated crawling, credential stuffing-like patterns, or resource-intensive requests that remain below rate limits.

**Mitigation idea**  
Targeted additions to the pipeline, e.g.:
- Request body size limits and parsing guards for large payloads.
- IP/ASN-based block and allow lists or adaptive abuse detection.
- Circuit breakers or concurrency limits per route/feature.
- Dedicated abuse signals (e.g., error rate, auth failures, query complexity) with automatic throttling.
- Strict request and idle timeouts, especially for slow uploads or long-running responses.
- Optional validation of request complexity (e.g., query length, number of filters, embedding size) with rejection thresholds.

## Summary

The existing mechanisms address load protection and auth rate limits, but have weaknesses in protection against unauthenticated floods and robustness against containerized or off-heap memory loads. Strengthening the metric sources would significantly improve effectiveness in all environments.

## Specific attack scenarios without adequate defense

1) **Auth flooding**  
   Many requests with invalid signatures/tokens cause CPU load due to JWT parsing and verification, without an upstream rate limit.

2) **Payload DoS**  
   Very large JSON bodies at content endpoints drive up parsing costs and heap/GC pressure, as no body size limits are apparent.

3) **Query amplification**  
   Many expensive search or query requests with valid tokens remain below the rate limit but can saturate Lucene/embedding resources.

4) **Slow request abuse**  
   Slow connections (Slowloris-like) tie up resources if no strict request/idle timeouts are active.

5) **Off-heap exhaustion**  
   High off-heap usage (Lucene MMap, ONNX, Direct Buffers) increases without a heap-based shedding protection layer.
