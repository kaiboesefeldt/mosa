# Semantic Social Network "Mosa"

## Core Concept

A federated social network that organizes content exclusively by **semantic topics** instead of social connections. Users follow topics (defined by text descriptions), not people. Content is discovered and clustered via embedding vectors.

## Basic Architecture

### Pods (Servers)

* **Thematic Specialization**: Each pod can optionally define 1-N topic areas with its own embedding vectors. If a pod defines no topic area, it accepts all topics.
* **Content Filtering**: Accepts only posts that fit its topics (similarity > threshold).
* **Smart Routing**: Rejects thematically mismatched requests and returns hints for more suitable pods.
* **Autonomous Moderation**: Every pod moderates according to its own standards, e.g. via “blocked” embedding vectors defined locally per pod.

### Clients (preferably Browser-Only)

* **Topic Definition**: The user describes interest groups in text.
* **Embedding Calculation**: Client-side computation of embedding vectors (e.g. with TensorFlow\.js) → technically feasible and performant enough? If not → the pod offers embedding calculation.
* **Federated Polling**: Regularly queries relevant pods using vector-similarity search.
* **No Login Required**: Fully read-only use without registration, but optionally with crypto tokens to enable “closed” pods.

## Identity and Posting System

### Cryptographic Tokens

* **Token Approval**: Pods can manually approve new tokens (anti-flooding).
* **Long-Lived Identities**: Approved tokens act like pseudonymous accounts.

### Read Access

* **Default: Free Access**: Reading is possible without sign-in and without a token.
* **Optional: Restrict via Token**: Requires only a valid token, no traditional registration.
* **Optional Later: Fine-Grained Access Rights**, idea:
    - User authentication via OIDC (e.g. through Keycloak).
    - Group membership stored in the JWT, either as embedding vectors or as group names.

### Write Access

* **Minimal Barrier**: Only a valid token required, no traditional registration.
* **Decentralized Moderation**: Each pod moderates its own content.
* **Community Voting**: Tokens can be rated by voting.

## Federation and Discovery

### Pod-to-Pod Communication

* **Federation**: Pods share lists of known pods with each other.
    - Pods provide a special endpoint to query their “recommended” pods.
* **Thematic Routing**: Pods suggest more suitable pods when rejecting requests.
    - Pods provide a special endpoint to query the topics they support.
* **No Automatic Block-List Sharing**: Each pod remains sovereign in moderation decisions.

### Client Discovery

* **Bootstrap**: Start with a seed list of known pods.
* **Adaptive Learning Curve**: The client learns which pods are relevant for which topics.
* **Emergent Quality Signals**: Well-curated pods receive more traffic.

## Content Moderation

### Architecture-Based Moderation

* **Thematic Barriers**: Spam and off-topic posts are automatically rejected or routed into appropriate groups.
* **Delayed Token Approval by Pods**: Makes spam attacks exponentially more expensive.
* **User Choice**: Users decide on moderation standards by choosing pods.

### Quality Control

* **Community Voting**: Rating of posts and tokens.
* **Pod Reputation**: Poorly moderated pods lose traffic.
* **Voting with Their Feet**: Users can simply ignore unsuitable pods.

### **Protection Against Cyber Attacks**

* **Blacklisting**:
    - Users (via token)
    - IP addresses or subnets
* **Adaptive Measures**
    - Token approval can be delayed to counteract pod overload caused by DDoS attacks using newly created tokens.
    - Any access can temporarily require a token to counter overload or DDoS attacks. The token can be used to selectively delay or blacklist specific users.
* **Efficient Defensive Measures**: Rejecting and delaying accesses efficiently to avoid pod overload.

## API Specification

Note: Every endpoint supports an HTTP header field through which a crypto token can be passed. Whether this token is required depends on the concrete situation. For example, a pod under heavy load can demand a crypto token for every endpoint to slow down and block malicious accesses.

### Content Endpoints

```
POST /post # Create a new post (with token auth)
POST /reply # Reply to a post or another reply; may be rejected if off-topic (embedding vector too far from the original post)
POST /vote # Rate a post or reply
GET /posts&embver=1&vector=[...]&threshold=0.8&limit=50 # Vector-similarity search. Returns initial hits plus postid to fetch further hits; embver defines the embedding version
GET /nextposts&postid=....&limit=50 # Load next posts for an existing search; time window is very short, e.g. 10 s
GET /meta # Pod information
```

### Discovery Endpoints

```
GET /topics # Topics supported by the pod
GET /pods # List of known pods
```

### Token Management

POST /token # Submit a new token for approval


## Technical Implementation

### Client Side

* **Embedding Calculation**: TensorFlow\.js or WASM-based models
* **Caching**: Store computed embeddings locally.
* **Polling Strategy**: Intelligent prioritization of relevant pods.

### Server Side

* **Vector Database**: Qdrant, Weaviate, or similar for similarity search.
* **Rate Limiting**: Protection against spam and overload.
* **Content Cleanup**: Automatic deletion of old/unpopular posts.

## Extended Features

### Serendipity and Diversity

* **Opposite Groups**: Exploration of negated embedding vectors.
* **Random Discovery**: Deliberate exposure to other topic areas, e.g. by
  randomized insertion of (possibly specially marked) off-topic posts during queries.

### Comment System

* **Thread Structure**: Comments are stored on the original pod.
* **Always On-Topic**: Comments must have a minimum similarity (by embedding vector) to the original post.

## Advantages over Traditional Social Networks

1. **Thematic Coherence**: Content is organized by relevance, not by social connections.
2. **Natural Spam Resistance**: Architecture-based filtering.
3. **Decentralized Moderation**: No central censorship authority.
4. **Low Entry Barriers**: Reading without registration, writing with minimal effort.
5. **Pseudonymous Participation**: Privacy-friendly without total anonymity.
6. **Emergent Quality**: Self-organizing quality control through “market mechanisms”.
7. **Discovery**: Users don’t have to learn which pods are relevant; pod discovery automatically guides them.

## Possible Challenges

1. **Scalability**: Efficient vector search with large datasets.
2. **Spam Resistance**: Although filtered by architecture, thematically fitting spam could appear.
3. **Quality Control**: Thematic relevance ≠ content quality.
5. **Filter-Bubble Risk**: Even though thematic diversity is possible within topics.  
