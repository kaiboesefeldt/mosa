# Semantic Social Network "Mosa"

## Core Concept

A federated social network that organizes content exclusively by **semantic topics** instead of social connections. Users follow topics (defined by text descriptions), not people. Content is discovered and clustered via embedding vectors.

## Basic Architecture

### Pods (Servers)

* **Thematic Specialization**: Each pod can optionally define 1-N topic areas with its own embedding vectors. If a pod defines no topic area, it accepts all topics.
* **Content Filtering**: Accepts only posts that fit its topics (similarity > threshold).
* **Smart Routing**: Rejects thematically mismatched requests and may return hints for more suitable pods.
* **Autonomous Moderation**: Every pod moderates according to its own standards, e.g. via “blocked” embedding vectors defined locally per pod.

### Clients (preferably Browser-Only, if feasible)

* **Topic Definition**: The user describes interest groups in text.
* **Embedding Calculation**: Client-side computation of embedding vectors (e.g. with TensorFlow\.js) → technically feasible and performant enough? If not → the pod may offer embedding calculation.
* **Federated Polling**: Clients regularly query relevant pods using vector-similarity search.
* **No Login Required**: Login and identity management is done via [MIS](spec/MIS1.0.md) without user interaction

## Identity and Posting System

### Client Identity

* **Cryptographic key pair as identity**: Clients generate a cryptographic key to prove their identity. 
* **Identity approval by signing**: Pods can grant a certificate for a client identity and thus approve access to the pod.
* **Long-Lived Identities**: Client identities act like pseudonymous accounts and may be used to access multiple pods (each must approve the identity)

### Access Control

* **Automatic sign-in**: Clients automatically request a certificate from a pod for their identity.
* **[MIS-JWT](spec/MIS1.0.md)) as bearer token**: Clients must build a MIS-JWT and provide it as bearer token with every REST request to a pod.
* **Rate limiting by identity approval**: Pods may delay or temporarily refuse the approval of identities and/or revoke certificates in high load scenarios. This is a protective measure.
* **Optional Later: Fine-Grained Access Rights**, idea:
    - User authentication via OIDC (e.g. through Keycloak).
    - Group membership stored in the MIS-JWT

## Federation and Discovery

### Pod-to-Pod Communication

* **Federation**: Pods provide a special endpoint to query their “recommended” pods, which may be accessed by other pods.
* **Thematic Routing**: Pods may suggest more suitable pods when rejecting requests.
* **No Automatic Block-List Sharing**: Each pod remains sovereign in moderation decisions.

### Client Discovery

* **Bootstrap**: Start with a seed list of known pods.
* **Adaptive Learning Curve**: The client learns which pods are relevant for which topics.
* **Topic discovery**: Pods provide a special endpoint to query the topics they support.
* **Emergent Quality Signals**: Well-curated pods receive more traffic.

## Content Moderation

### Architecture-Based Moderation

* **Decentralized Moderation**: Each pod moderates its own content.
* **Thematic Barriers**: Spam and off-topic posts are automatically rejected or routed into appropriate groups.
* **Delayed Token Approval by Pods**: Makes spam attacks exponentially more expensive, because spammers would have to request approval for a big number of identities.
* **User Choice**: Users decide on moderation standards by choosing pods.

### Quality Control

* **Community Voting**: Rating of posts and tokens.
* **Pod Reputation**: Poorly moderated pods lose traffic.
* **Voting with Their Feet**: Users can simply ignore unsuitable pods.

## **Protection Against Cyber Attacks**

* **Blacklisting**:
    - Users (via identity)
    - IP addresses or subnets
* **Adaptive Measures**
    - Token approval can be delayed to counteract pod overload caused by DDoS attacks using newly created tokens.
    - Any access requires a token to counter overload or DDoS attacks. The token can be used to selectively delay or blacklist specific users.
* **Efficient Defensive Measures**: Rejecting and delaying access efficiently to avoid pod overload.

## API Specification

### General conventions

1. Every endpoint requires that a valid MIS-JWT is passed as bearer token.
2. All endpoints are below a `/v1` path, which designates the API version
3. All endpoints may answer with `429 Too Many Requests` including a `Retry-After` response header to protect themselves in high load scenarios.

### Content Endpoints

#### Create a new post: `POST /v1/publish`

The posted text is submitted via the request body.

#### Reply to a post or another reply: `POST /v1/reply`

Might be rejected if off-topic (embedding vector too far from the original post).

The posted text as well as a reference to the original post is submitted via the request body.

#### Rate a post or repl: `POST /v1/vote`

The voting or rating as well as a reference to the voted post is submitted via the request body.

#### Vector-similarity search: `POST /v1/query`

The following parameters may be transmitted via the request body:

1. Embedding version
2. Queried embedding vector
3. Similarity threshold between the queried embedding vector and the embedding vector of the queried results.
4. Result count limit

The result is a list of query results, plus a query ID which may be passed to the `/v1/queryon` endpoint to get the next result chunk.

#### Load next results for an existing search: `POST /v1/queryon`

The query ID from the original `/v1/query` request must be passed in the request body.

To allow pods efficient life cycle handling and data cleanupm, the `/v1/queryon` endpoint must be called within a very narrow time window from the last `search` or `searchon` request, e.g., 10 s. This endpoint may be called on and on and on until it yields an empty result.

The result is a list of the next query results. 

#### Info about the pod's configuration: `/v1/info`

Returns info about the pod's configuration, e.g. supported API and embedding versions.


### Discovery Endpoints

#### Supported topics: `GET /v1/topics`

Get the topics supported by the pod. Replies with a list of embedding vectors

#### Known pods: `GET /v1/pods`

Get a list of other known pods. Replies with a list of other pods' host names. 

Which pods are returned by is configured by the pod administrator.


### Token Management

#### Request identity approval: `POST /v1/approvegid`

The client's `gid` according to [MIS](spec/MIS1.0.md) is passed via request body. This is the only request not requiring a bearer token header.

The pod answers with a MIS certificate for the transmitted `gid`.


## Technical Implementation

### Client Side

* **Embedding Calculation**: TensorFlow\.js or WASM-based models
* **Caching**: Store computed embeddings locally.
* **Polling Strategy**: Intelligent prioritization of relevant pods.
* **Save storage of crypto-identities**: How is this possible in a browser?

### Server Side

* **Vector Database**: Qdrant, Weaviate, or similar for similarity search.
* **Rate Limiting**: Protection against spam and overload.
* **Content Cleanup**: Automatic deletion of old/unpopular posts.

## Extended Features

### Serendipity and Diversity

* **Opposite Groups**: Exploration of negated embedding vectors.
* **Random Discovery**: Deliberate exposure to other topic areas, e.g., by randomized insertion of (possibly specially marked) off-topic posts during queries.

### Comment System

* **Thread Structure**: Comments are stored on the original pod.
* **Always On-Topic**: Comments must have a minimum similarity (by embedding vector) to the original post.

## Advantages over Traditional Social Networks

1. **Thematic Coherence**: Content is organized by relevance, not by social connections.
2. **Natural Spam Resistance**: Architecture-based filtering.
3. **Decentralized Moderation**: No central censorship authority.
4. **Low Entry Barriers**: Access with minimal effort, automatic registration.
5. **Pseudonymous Participation**: Privacy-friendly without total anonymity.
6. **Discovery**: Users don’t have to learn which pods are relevant; pod discovery automatically guides them.

## Possible Challenges

1. **Scalability**: Efficient vector search with large datasets.
2. **Spam Resistance**: Although filtered by architecture, thematically fitting spam could appear.
3. **Quality Control**: Thematic relevance ≠ content quality.
5. **Filter-Bubble Risk**: Even though thematic diversity is possible within topics.  
