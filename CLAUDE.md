# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Mosa is a federated semantic social network written in Kotlin. It organizes content by semantic topics using embedding vectors rather than social connections. Pods support both semantic queries (by topic similarity) and thread queries (replies to root posts). Content filtering ensures posts fit pod topics, with replies requiring topic alignment with root posts.

## Build System & Commands

This is a Gradle-based Kotlin project using JVM target 19.

### Core Commands
- `./gradlew run` - Start the main server application (port 8080)
- `./gradlew build` - Build all modules
- `./gradlew test` - Run all tests (uses JUnit 5 Platform)
- `./gradlew check` - Run tests and all verification tasks
- `./gradlew clean` - Clean build directories

### Code Quality
- `./gradlew formatKotlin` - Format Kotlin code (runs automatically before compilation)
- `./gradlew lintKotlin` - Run Kotlinter linting
- Formatting is enforced and runs automatically with compilation

### Single Test Execution
- `./gradlew test --tests "ClassName"` - Run specific test class
- `./gradlew test --tests "ClassName.methodName"` - Run specific test method

## Architecture

### Multi-Module Structure
- **Root module**: Main server application with REST API

### Core Components

**Dependency Injection**: Uses Koin for DI, configured in `AppModule.kt`

**Web Framework**: Ktor server with:
- Netty engine on port 8080
- JWT bearer token authentication (MIS-JWT format)
- Jackson JSON serialization
- Controller-based routing via `BaseController`

**Key Services**:
- `EmbeddingService`: ONNX-based local embedding computation using DJL
- `VectorStore`: Lucene-based vector similarity search with KNN for semantic and thread queries
- `ContentService`: Manages posts, replies, and semantic queries with on-topic filtering
- `PodCertificateService`: Handles identity approval and JWT certificates
- `Crypto`: Ed25519 cryptographic operations using BouncyCastle

**Authentication Flow**:
1. Clients generate Ed25519 keypairs as identity
2. POST `/v1/approvegid` to request pod certificate (no auth required)
3. All other endpoints require MIS-JWT bearer token

### API Structure
All endpoints under `/v1/` prefix (unless explicitly stated, all require MIS-JWT bearer token):
- `/publish` - Create posts
- `/reply` - Reply to posts (may be rejected if off-topic)
- `/query` - On-topic search with text description, result limit, optional timestamp filter, optional root post filter
- `/queryon` - Paginated query continuation using query ID
- `/approvegid` - Identity approval (unauthenticated)
- `/pods` - Get list of known pods
- `/vote` (postponed) - Rate posts or replies
- `/info` (postponed) - Pod configuration info  
- `/topics` (postponed) - Get supported topics

### Data Storage
- **Vector Search**: Lucene with MMapDirectory at `mosa_lucene_index/`
- **Embeddings**: Local ONNX models in `models/` directory
- **Configuration**: Uses typesafe configuration patterns

### Test Structure
- **Unit Tests**: Per-component testing with MockK
- **Integration Tests**: HTTP endpoint testing with Ktor test host
- **Test Data**: JSON content files in `src/test/resources/testContent/`
- **Test Utilities**: Clock mocking and test environments

## Development Notes

### Model Dependencies
The project includes local ONNX models for embeddings and text generation. These are loaded from the `models/` directory and managed by the DJL (Deep Java Library) framework.

### Security Implementation
Uses Ed25519 signatures and MIS (Mosa Identity System) JWT tokens for authentication. All cryptographic operations go through the `Crypto` interface with BouncyCastle backend.
