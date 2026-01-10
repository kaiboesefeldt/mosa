# Security Findings Report – Defense Package

## Context

Evaluation of the server-side cyber defense mechanisms and their integration in the Ktor stack, with emphasis on load shedding, rate limiting, request guards, and transport enforcement.

## Findings

### 1) CPU load shedding is not container-robust

**Description**  
CPU shedding is based on `systemLoadAverage`. In containers or certain JVMs, this value can return `-1`, causing the system to incorrectly assume a load of 0.

**Affected location**  
`server/src/main/kotlin/straightway/mosa/defense/cpu/CpuSheddingDecider.kt:54`

**Risk**  
In environments without a valid load average, CPU shedding is completely absent; overload is not detected.

**Mitigation idea**  
Fall back to container-compatible metrics (e.g., CGroup CPU usage or `OperatingSystemMXBean` process CPU usage, if available) and allow explicit configuration for the preferred measurement method.

### 2) Memory shedding ignores off-heap and MMap usage

**Description**  
The memory decision is based solely on heap usage. Off-heap memory (Lucene `MMapDirectory`, ONNX Runtime, Direct Buffers) is not taken into account.

**Affected location**  
`server/src/main/kotlin/straightway/mosa/defense/memory/MemorySheddingDecider.kt:56`

**Risk**  
OOM risks can occur despite low heap utilization; shedding reacts too late or not at all.

**Mitigation idea**  
Include non-heap/total memory metrics (e.g., `MemoryMXBean.nonHeapMemoryUsage`, `BufferPoolMXBean`, OS-level RSS) or enforce a configurable process-memory guard that triggers shedding before OOM.

### 3) Retry-After in rate limit is static and inaccurate

**Description**  
`Retry-After` is calculated from `permitsPerMinute` and is not linked to the actual token refill of the respective bucket instance.

**Affected location**  
`server/src/main/kotlin/straightway/mosa/defense/ratelimit/RateLimiter.kt:14`

**Risk**  
Clients receive unreliable backoff notices, which can trigger unnecessary retry spikes.

**Mitigation idea**  
Calculate `Retry-After` per key based on the current token status and refill time instead of a static approximation.

### 4) TLS enforcement can be bypassed via spoofed `X-Forwarded-Proto`

**Description**  
`isSecureRequest` trusts `X-Forwarded-Proto` without validating that the request actually came from a trusted proxy. A direct client can set the header to `https` and bypass the TLS requirement when `ALLOW_HTTP` is `false`.

**Affected location**  
`server/src/main/kotlin/straightway/mosa/ServerConfig.kt:116`

**Risk**  
Unauthenticated HTTP requests can be accepted as “secure,” undermining the transport security expectation of the pod endpoints.

**Mitigation idea**  
Only honor forwarded proto headers for requests coming from `trustedProxyIps`, or install and configure Ktor’s forwarded headers plugin with strict proxy trust rules; otherwise rely solely on the local scheme.

## Summary

The server now includes request body limits, IP + authenticated rate limits, request duration shedding, and read/write timeouts. The remaining gaps are the container robustness of CPU/memory signals, static rate-limit backoff hints, and transport enforcement that can be spoofed via forwarded headers.

## Specific attack scenarios without adequate defense

1) **TLS downgrade by header spoofing**  
   A client sends HTTP requests with `X-Forwarded-Proto: https`, bypassing the TLS enforcement while still being treated as secure.

2) **Containerized CPU overload**  
   `systemLoadAverage` returns `-1`, which disables CPU-based shedding and allows sustained overload.

3) **Off-heap exhaustion**  
   MMap/ONNX/DirectBuffer usage grows while heap usage stays low, so memory shedding never triggers.

4) **Rate-limit retry storms**  
   Clients receive inaccurate `Retry-After` values and retry too aggressively after a block.
