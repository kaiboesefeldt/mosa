# Security Findings Report – Defense Package

## Context

Evaluation of the server-side cyber defense mechanisms and their integration in the Ktor stack, with emphasis on load shedding, rate limiting, request guards, and transport enforcement.

## Findings

### 1) Memory shedding ignores off-heap and MMap usage

**Description**  
The memory decision is based solely on heap usage. Off-heap memory (Lucene `MMapDirectory`, ONNX Runtime, Direct Buffers) is not taken into account.

**Affected location**  
`server/src/main/kotlin/straightway/mosa/defense/memory/MemorySheddingDecider.kt:56`

**Risk**  
OOM risks can occur despite low heap utilization; shedding reacts too late or not at all.

**Mitigation idea**  
Include non-heap/total memory metrics (e.g., `MemoryMXBean.nonHeapMemoryUsage`, `BufferPoolMXBean`, OS-level RSS) or enforce a configurable process-memory guard that triggers shedding before OOM.
**Status**  
Implemented: memory shedding now includes non-heap usage plus JVM buffer pool usage.


## Summary

The server now includes request body limits, IP + authenticated rate limits, request duration shedding, and read/write timeouts. The remaining gaps are the container robustness of CPU/memory signals.

## Specific attack scenarios without adequate defense

1) **Containerized CPU overload**  
   `systemLoadAverage` returns `-1`, which disables CPU-based shedding and allows sustained overload.

2) **Off-heap exhaustion**  
   MMap/ONNX/DirectBuffer usage grows while heap usage stays low, so memory shedding never triggers.
