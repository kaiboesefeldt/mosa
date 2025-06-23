Mosa Identity Standard (MIS) v1 – Revised Draft Specification


# 1. Introduction

This document defines the Mosa Identity Standard (MIS) v1, a lightweight, implementation-friendly and decentralized authentication token and certificate format built on public-key cryptography. MIS lets a client present a pre-signed token to a Pod with zero user interaction, so the Pod can seamlessly admit, throttle or temporarily block identities under high load, without user interaction. Because all trust is embedded in signatures, Pods remain stateless yet keep fine-grained control over active sessions. The design scales horizontally, integrates with existing JWT toolchains and stays secure against both classical and near-term quantum threats. It streamlines machine-to-machine and human-to-service flows alike and fosters federated ecosystems where trust is conveyed entirely through verifiable signatures.

Identity in MIS is tied to an asymmetric cryptographic key pair: each user generates a key pair locally and is identified by the hash of their public key. A server (referred to as a Pod) issues a certificate for the user’s public key hash (the user’s GID, or Global ID) without storing any user credentials or state. The user then authenticates by presenting a JSON Web Token (JWT) that includes their GID, full public key, the Pod’s certificate, timestamps, version identifier and is signed by the user’s private key. The Pod’s certificate is signed with the Pod’s private key. This approach yields a stateless, cryptographically assured identity model – the Pod need not maintain user records, and all trust is established via digital signatures.

This specification is deliberately minimal and strict. It uses the key words MUST, SHALL, SHOULD, MAY, etc., in the normative sense (to be interpreted as in RFC 2119 when capitalized). It mandates only the essential fields and claims and enforces strong security practices (quantum-safe algorithms, strict time checks, secure transport). Optional or context-dependent JWT claims (e.g. issuer, subject, audience) are eliminated to reduce complexity and attack surface. The result is an implementation-ready standard for authentication tokens that is compact, deterministic, and robust against both current and future threats.


# 2. Identity Keys and GID

User Identity Key Pair: Each user MUST generate an cryptographic asymmetric key pair (see §6 for acceptable algorithms). The public key serves as the basis of the user’s identity.

Global ID (GID): The user’s GID is a hash of their public key. Specifically, GID MUST be the raw bytes of the digest of the public key (algorithms as defined in §6), encoded in base64url without padding. The GID is the unique identifier for the user in MIS. Users register with a Pod by providing only the GID (the public key itself is not stored by the Pod). The Pod does not retain the public key or any user state; it uses the GID solely to issue a certificate, then discards any user-specific data (although the pod may add more user specific info to the certificate using the `inf` field).


# 3. Pod Certificate Format

Upon registration, the Pod issues a certificate for the user’s GID. The certificate is a JSON object with exactly the following fields:

* `gid` – The user’s Global ID (hash of their public key, as described above).
* `exp` – An expiration timestamp (Unix epoch seconds) after which the certificate is no longer valid.
* `inf` - Optional additional info added by the pod, as base64url-encoded string. E.g. if the Pod has linked the `gid` to some human identity, it may add the human's name and address here to the certificate. The `inf` field may be safely omitted.
* `sig` – The Pod’s digital signature over the `gid`, `exp` and `inf` (if present) fields.

No other fields are allowed. In particular, there is no `iss` (issuer) or other metadata; the Pod’s signature itself serves as the attestation of issuance. The Pod MUST generate the certificate without storing any user-specific state: once issued, the certificate is handed to the user and the Pod forgets it. The certificate’s `exp` (expiry) should be a mid-term duration (e.g. days, weeks or month) to avoid too frequent re-approvals on the one hand, and to limit the window of validity in case of key compromise on the other hand. The Pod’s signature (`sig`) is computed with the Pod’s private crypto key as described in §5.1 and is encoded as a base64url string without padding in the certificate. If a certificate is presented, the Pod can verify the signature using the its public key to confirm the authenticity of the certificate.

Certificate Example: A sample JSON certificate issued by a Pod might appear as:

```json
{
  "gid": "X4V5Z...0Rw",        // base64url-encoded SHA-256 hash of user's public key
  "exp": 1759281600,           // certificate expiry (Unix time)
  "sig": "ACQCIQDh8m3...AX6Q"  // Pod's Ed25519 signature on (`gid` || exp)
  "inf": "KXZEeeREE55...DXGQ"  // Pod specific user info.
}
```

Above, base64 representations of binary values are truncated for display. In the actual certificate, `gid` and `sig` would be full length (e.g. 43 base64url characters for a 32-byte GID, and 86 characters for a 64-byte signature), as well as `inf`. This certificate certifies that the Pod recognizes the user identified by that GID, with more detailed info in `inf`, until the specified `exp` time.


# 4. JWT Payload and Claims

The user authenticates by presenting a JWT containing their identity and proof. The JWT payload MUST include only the following mandatory claims (no optional claims):

* `pub` – The user’s full public key (encoded in base64url). This is included so that verifiers can use it to check the user’s signature on the JWT. It MUST correspond to the provided GID.
* `cert` – The Pod’s certificate object for this GID. This is exactly the JSON object described in §3, embedded as a claim (with its own subfields `gid`, `exp`, `sig` and optionally `inf`). The `gid` in the certificate MUST match the hash of the `pub` claim (i.e. `cert.gid` must equal the hash of the raw bytes of the public key; this is verified by the Pod).
* `exp` – Expiration time (Unix epoch seconds) after which this JWT is invalid.
* `mvs` – The MIS version identifier constant. For MIS v1, this MUST be the string "1". This required claim allows forward compatibility; if future versions use a different format, they will use a different `mvs` value. Verifiers MUST check that `mvs` == "1" for tokens claiming to be MIS v1, and MUST reject tokens with unknown or missing version.

Other JWT claims MUST NOT appear in an MIS v2 token (including `iss` (issuer), `sub` (subject), `aud` (audience), `nbf` (not-before) and similar claims). These fields are unnecessary in this design and are omitted to avoid ambiguity or redundant data. (For instance, the Pod’s certificate and signature serve the role of an issuer claim, and `cert.gid` serves as the subject identifier, so separate `iss`/`sub` fields would add no value and are excluded.) Any token that includes extraneous claims SHOULD be considered non-compliant and rejected.


# 5. Deterministic Serialization and Signing

All signature operations in MIS v1 use a flat, deterministic byte serialization of the relevant fields. This ensures that all implementations sign and verify the exact same byte sequence, avoiding any ambiguity in encoding or field ordering.

## 5.1 Certificate Signature (Pod → sig)

For the Pod to sign a certificate (fields `gid`, `exp` and `inf`), the following procedure MUST be used to produce the message to sign:

* Convert the GID from its text encoding to raw bytes (The GID was defined as the raw bytes of the hash output).
* Convert the `exp` timestamp to an 8-byte big-endian binary representation (unsigned 64-bit integer). This covers dates up to beyond year 2100 and ensures a fixed width.
* Convert the `inf` field from its text encoding to raw bytes (if present).
* Concatenate the raw bytes of the GID, the 8-byte expiration and optinally the raw bytes of the `inf` content (if present). No delimiters or encoding (such as JSON or ASCII) are added – just the raw byte sequences: `signing_input = gid_bytes || exp_bytes || inf_bytes (if present)`.
* The Pod then signs this raw byte message using its private key, producing a 64-byte signature. This signature is placed into the `sig` field of the certificate as a base64url-encoded string.

This exact process MUST be followed for verifying a certificate as well: a verifier will take the `gid`, `exp` and `inf` (if present) from the certificate, convert and concatenate them in the same way, and then check the cryptographic signature (`sig`) against the Pod’s public key. The use of a raw binary format here avoids any variability (e.g. no whitespace, JSON key ordering issues, or character encoding confusion). It is a straightforward, canonical byte string that yields a unique signature for given inputs.

## 5.2 JWT Signature (User → JWT)

The user must sign the JWT to prove possession of the private key corresponding to `pub` (and thus to the GID). MIS v1 uses the standard JWT (JWS) compact serialization for the token, with an explicit specification of how to construct the signed content:

* The JWT header MUST be `{"alg":"<cryptoalgo>","typ":"JWT"}` (where <cryptoalgo> is the specification of the algorithm used to create the JWT signature, see §6 for acceptable choices). Any other fields (such as `kid`, `cty`, etc.) MUST NOT appear in the header. This header is JSON-serialized, then base64url-encoded to form the first part of the token.
* The JWT payload is the JSON object containing exactly the claims listed in §4 (`pub`, `cert`, `exp`, `mvs`). This JSON payload is then base64url-encoded to form the second part of the token.
* The user computes the signature over the standard JOSE signing input: base64url(header) || '.' || base64url(payload). In other words, the exact bytes to sign are the ASCII encoding of the two base64url segments separated by a period (`.`). The user uses their private key to sign this data. The resulting signature is then base64url-encoded to produce the third segment of the JWT.

The final JWT token is formed as `<header>.<payload>.<signature>` using the three base64url segments. Verifiers will perform the analogous process: parse the token, base64url-decode the header and payload, verify that the header correctly indicates the crypto algorithm (identifier according to RFC 7518 and RFC 8032), recompute the signing input, and use the provided `pub` (user’s public key) to verify the signature segment. Because all fields are included in the signed payload, the JWT signature covers the entire content (including the embedded certificate and all timestamps). There is no redundant or nested signature beyond these two: the Pod signs the certificate, and the user signs the JWT – no other signatures or MACs are applied. This two-tier signing ensures both the authenticity of the user (JWT signature) and the authenticity of the user’s identity attestation (Pod’s certificate signature) without any duplication. All implementations MUST abide by this signing procedure to guarantee interoperability.


# 6. Cryptographic Algorithms

All cryptography in MIS v1 MUST use algorithms deemed secure against modern attackers, with an eye towards post-quantum robustness. The minimum required algorithm set is as follows:

* Digital Signatures: Ed25519 (EdDSA over Curve25519) SHOULD be used for all signatures, weaker algorithms MUST NOT be used. The user’s key pair SHOULD BE Ed25519, and the Pod’s signing key SHOULD BE Ed25519. Ed25519 provides 128-bit security strength for classical attacks and is efficient and widely supported (In JOSE/JWT terms, the algorithm identifier for this is “EdDSA”).
* Hashing (for GID): SHA-256 (As defined in RFC 6234) SHOULD be used to hash the public key to produce the GID, weaker algorithms MUST NOT be used. The SHA-256 output (256 bits) is considered secure against classical preimage attacks and offers some resistance to quantum attacks (quantum adversaries would still need on the order of 2^128 operations due to Grover’s algorithm).
* Key Encodings: Public keys, signatures, and hashes are represented as URL-safe base64 strings (with padding omitted) when included in JWT JSON fields. This ensures that binary values are conveyed in a text-friendly format without introducing ambiguity.

Forbidden Algorithms: RSA, DSA, ECDSA (over prime curves such as P-256, secp256k1, etc.), or any other non-quantum-safe or legacy algorithm MUST NOT be used in MIS v1. Such algorithms are slower, harder to implement without errors, and are vulnerable to future quantum computing attacks. For example, both RSA and classic ECDSA rely on problems (integer factorization and elliptic curve discrete log, respectively) that can be efficiently solved by Shor’s algorithm on a sufficiently powerful quantum computer. By mandating Ed25519 and SHA-256, MIS v1 utilizes state-of-the-art cryptography; these choices are secure in today’s paradigm and simplify implementation (Ed25519 avoids many of the pitfalls of older algorithms, and it is faster and more modern than RSA). Future versions of MIS may introduce true post-quantum algorithms (such as CRYSTALS-Dilithium or others standardized by NIST) once they become practical; however, MIS v1 implementers are encouraged to design systems so that algorithm upgrades are possible when needed (hence the inclusion of the `mvs` field and not hard-coding algorithm assumptions beyond what is stated here).

Additionally, the JWT `alg` header MUST specify a strong crypto algorithm. All JWTs specifying weak crypto algorithms (especially those state above) MUST be rejected.


# 7. Transport Security

MIS tokens are bearer tokens and are not encrypted by themselves. Anyone in possession of a valid token could theoretically use it to impersonate the user until it expires. Therefore, all JWTs MUST be transmitted only over secure channels. Specifically, when a token is sent over a network, it MUST be via HTTPS (HTTP over TLS 1.2 or higher). This applies to any API calls, page loads, or service-to-service communications involving the token. Tokens must not be sent in cleartext over a network using HTTP. Using TLS is essential because JWTs are just base64-encoded data; if sent in the clear, they can be trivially intercepted and read or reused by an attacker.

Furthermore, tokens MUST be sent in the HTTP Authorization header or message body, not as part of the URL. Embedding JWTs in URLs (e.g. as query parameters) is prohibited because URLs can end up in browser history, server logs, referrer headers or intermediary caches, leaking the token. Even caching or logging of requests can inadvertently expose tokens if they appear in GET request query strings. As a best practice, never log or cache full JWTs on the server side either (see §9). In summary, treat the JWT as a secret credential: transport it only over encrypted channels and avoid any mechanism that might persist it in plaintext form (such as proxy logs, browser logs, etc.).

The only exception is purely intra-host communication, e.g. between processes on the same machine, where there is no network transmission. In such a case, one MAY pass the token via secure inter-process communication without encryption. But as soon as the token leaves a host, TLS is REQUIRED.


# 8. Time Validation

Time-based claims (`exp`) and certificate expiry MUST be strictly enforced. All tokens and certificates carry timestamps, and these are to be treated as hard bounds, not suggestions. Implementations MUST perform the following checks during verification of a JWT:

* If the current time is equal to or after the `exp` or `cert.exp` timestamp, the token is expired and MUST be considered invalid.
* If the certificate has expired, the token MUST be rejected even if the JWT’s own `exp` is still in the future (A user should not be able to authenticate with an expired certificate, even if they minted a token earlier).
* There is no grace period or clock-skew tolerance for any of these time checks. “Not-before” (`nbf`) claims are not used in MIS v1, and verifiers MUST NOT introduce any implicit leeway for clock differences. A token that is even 1 second past its expiration is invalid. A token from 1 second in the future is not yet valid. This strict approach is by design, to simplify implementation and eliminate ambiguity.

Many JWT libraries allow a default clock skew (often 2-5 minutes) to account for clock drift, but in MIS v1 this is disallowed – the validation is zero-skew. By enforcing exact times, we ensure clarity: all parties know exactly when a token becomes invalid with no ambiguity. If strict time checking is not possible or if clocks cannot be kept in sync, that environment may not be suitable for MIS without modifications (such as introducing a small buffer), but such modifications would violate the spec as written.

If clients need to deal with clock skew, they have the possibility to use tokens only until a few minutes before their expiration.

No `nbf` and no `iat`: The `nbf` claim (Not Before) and `iat` claim (Issued At) are intentionally omitted (and MUST NOT appear) because its use cases (post-dating tokens) are not needed in our design. We rely on `exp` alone. If a scenario requires a token to start being valid at a future time, the issuing party can simply wait to issue it at that time or handle that logic internally, rather than embed `nbf`. This keeps token processing straightforward.


# 9. Security Considerations

MIS v1 is designed to be minimal yet secure. Implementers should heed the following additional security requirements and best practices:

9.1 Token Confidentiality and Logging

As noted in §7, the JWT should be treated as a sensitive secret. Pods (and any services handling the token) MUST NOT log or store raw JWTs or certificate contents in any persistent store or plaintext log. This includes avoiding writing tokens to server logs, debug output, error messages, stack traces, etc. A token in a log file can be stolen by anyone with access to that file, enabling impersonation. If logging is necessary, you may log the GID or an opaque token identifier, but never the full JWT or the private key or actual signature material. Even in transit, tokens should not appear in URLs or other places that may be recorded. For example, do not transmit JWTs as query parameters in GET requests, because they could end up in web server logs or browser history. Always send tokens in an Authorization header or POST body, and only over HTTPS. Essentially, minimize exposure of the token contents to the bare minimum needed for operation.

## 9.2 Replay and Revocation

MIS v1 tokens are short-lived by design (enforced via `exp`) and bound to a certificate which is itself short-lived. There is no built-in revocation list or state; the system relies on expiration for revocation, a common approach for stateless token systems. To mitigate replay attacks, ensure that tokens have sufficiently short lifetimes (e.g. an hour or less) so that even if intercepted, they quickly become useless. If a user suspects their key is compromised, they should generate a new key pair and register a new GID (the old certificate will soon expire and any tokens with it will be rejected thereafter. Pods should not issue very long-lived certificates unless absolutely necessary, and should consider maintaining an internal blacklist of GIDs in extreme cases of key compromise (though that introduces state and is outside this specification). In normal operation, the combination of user signature + Pod certificate makes each token valid only for a specific user and timeframe; they cannot be altered or reused outside that context.

# 9.3 Implementation Robustness

All JSON parsing and token handling should be done carefully:

* If a token is malformed or fails verification, the server should respond with a generic authentication failure response. Do not leak details about which part was wrong (e.g. “signature invalid” vs “expired”) in a way that attackers can use to refine their guesses. For example, respond with HTTP 401 (Unauthorized) on authentication failures. Do not reveal failure reasons. This prevents attackers from binary searching for valid signatures or valid timestamps by observing error differences.
* The `mvs` claim allows future changes. If an implementation of MIS v1 encounters a token with a different `mvs`, it must reject it (as it indicates a newer version token that this implementation doesn’t understand). This fail-safe ensures forward compatibility – new tokens won’t be mistakenly accepted by old code that can’t validate them properly.
* Ensure to use well-vetted cryptographic libraries. Do not rewrite these algorithms yourself. Use libraries that properly handle edge cases and side-channel protections. This specification describes what to sign and hash; the underlying crypto should be left to reliable implementations.

By following this specification strictly – mandatory fields only, deterministic signing, strong algorithms, TLS transport, strict time checks, and careful coding practices – developers can achieve a high level of security for authentication in a stateless manner. MIS v1 deliberately forgoes flexibility in favor of clarity and security. Each requirement above is meant to eliminate an entire class of potential vulnerabilities.


# 10. Examples

This section provides a complete example of a certificate and a JWT, illustrating how a real authentication request would look under MIS v1. These examples use hypothetical values for demonstration. Appendix B contains python code with exactly the examples in this section.

## 10.1 Example Certificate (Pod → User):

Suppose a user registers with a Pod. The user’s public key hash (GID) is computed as RbG-smTmD69uNI9Wa4UWfUqftbPeIxUFCClDvOomwUg (a 43-character base64url string representing 32 bytes). The Pod issues a certificate valid until UNIX time 1616416635 (for example, some date in the future). The Pod signs the 40-byte sequence of the GID and expiration. The resulting certificate JSON might be:

{
  "gid": "RbG-smTmD69uNI9Wa4UWfUqftbPeIxUFCClDvOomwUg",
  "exp": 1616416635,
  "inf": "c29tZSBhZGRpdGlvbmFsIGluZm8",
  "sig": "3LZSIOAOtxAzPCWLHlUYvFmfIg7GNDEy2Fi5Xo9Mr__U_J7Ec2RT4AHH5dww7sBI5elSsIUXHzBlKz8ASDgDDg"
}

Here, `sig` is the base64url-encoded Ed25519 signature (64 bytes) produced by the Pod. A verifier (who knows the Pod’s public key) can recreate the message by concatenating the 32-byte binary hash of `gid` and the 8-byte binary of 1759203741 and verify this signature. If the signature is valid and the current time is before `exp`, the certificate is trusted. The certificate says, in effect: “Pod certifies that GID `RbG-smTmD69uNI9Wa4UWfUqftbPeIxUFCClDvOomwUg` is a valid user identity until 1616416635.”

## 10.2 Example JWT (User → Pod request):

Using the above certificate, the user now wants to authenticate to a service (which we’ll assume is the same Pod or another service that trusts the Pod’s signature). The user’s Ed25519 public key (corresponding to the GID) is, for example, `wmK6SSAu2E9V7uynkCKEaj5nZJyTvNG4x0KohsRzLpg` (32 bytes, shown here in base64url). The user creates a JWT with the required claims:

```json
{
    "pub": "wmK6SSAu2E9V7uynkCKEaj5nZJyTvNG4x0KohsRzLpg",
    "cert": {
        "gid": "RbG-smTmD69uNI9Wa4UWfUqftbPeIxUFCClDvOomwUg",
        "exp": 1616416635,
        "sig": "3LZSIOAOtxAzPCWLHlUYvFmfIg7GNDEy2Fi5Xo9Mr__U_J7Ec2RT4AHH5dww7sBI5elSsIUXHzBlKz8ASDgDDg",
        "inf": "c29tZSBhZGRpdGlvbmFsIGluZm8"
    },
    "exp": 1610614921,
    "mvs": "1"
}
```

Let’s break down these fields:

* `pub` is the user’s public key. Anyone can hash this and see that it matches the certificate's `gid`.
* `cert` is exactly the certificate the Pod issued (with the same gid, the Pod’s signature, and expiry 1616416635).
* `exp` is the token’s expiry (Unix epoch time 1610614921).
* `mvs` is 1, indicating this conforms to MIS v1.

The user then signs this JWT. The header is `{"alg":"EdDSA","typ":"JWT"}`. The signing input is:

`base64url(header) + "." + base64url(payload)`

Using the actual values (with base64url encoding), the complete JWT is this:
```
eyJhbGciOiJFZERTQSIsInR5cCI6IkpXVCJ9.
eyJwdWIiOiJ3bUs2U1NBdTJFOVY3dXlua0NLRWFqNW5aSnlUdk5HNHgwS29oc1J6THBnIiwiY2VydCI6eyJnaW
QiOiJSYkctc21UbUQ2OXVOSTlXYTRVV2ZVcWZ0YlBlSXhVRkNDbER2T29td1VnIiwiZXhwIjoxNjE2NDE2NjM1
LCJzaWciOiIzTFpTSU9BT3R4QXpQQ1dMSGxVWXZGbWZJZzdHTkRFeTJGaTVYbzlNcl9fVV9KN0VjMlJUNEFISD
Vkd3c3c0JJNWVsU3NJVVhIekJsS3o4QVNEZ0REZyIsImluZiI6ImMyOXRaU0JoWkdScGRHbHZibUZzSUdsdVpt
OCJ9LCJleHAiOjE2MTA2MTQ5MjEsIm12cyI6IjEifQ.
GxzAnPTX_6wQ10HI6nQEpyhhI7OrzeklfBjz71YOvE8ByiDtjFGq17d2RrCdLq58QlNnFDZF6QX7JvifUZ0_CQ
```
(Line breaks added for readability.)

This token consists of three dot-separated parts:

* Header: `eyJhbGciOiJFZERTQSIsInR5cCI6IkpXVCJ9` – which decodes to {"alg":"EdDSA","typ":"JWT"}.
* Payload: the long middle portion is the base64url encoding of the JSON payload shown above.
* Signature: `fR68kaJmn7Rj3Ugzy2OmXnqIWEPlSwz5Zv4R_NxwukqxY4fC6efgrYc-6fn3FpjF-ZfgdDEhZyTGG49ZjPcmBA` – a base64url encoding of the Ed25519 signature created by the user’s private key over the header and payload.

When the Pod receives this token, it will perform verification as follows:

* Check the `mvs` == 1.
* Verify the Pod’s certificate: recompute the hash of the provided `pub` to ensure it equals gid, then concatenate `gid`+`exp` (from the cert, `inf` is missing) and verify the `sig` with the Pod’s public key. If this fails or the certificate `exp` has passed, reject the token.
* Verify the user’s JWT signature: using the `pub` key and the known Ed25519 algorithm, check that the signature (third part of token) is valid for the concatenation of first two parts. If not, reject.
* Check `exp` against current time (both must be valid as per §8). In this example, ensure current time is <1610614921. No leeway is allowed – if the current time is 1610614921 or more, the token is expired and invalid.
* If all checks pass, the token is considered authentic and the user is authenticated as the owner of the key `gid` (with whatever application context that implies, e.g. access to their account or service).

This example demonstrates a full flow: a user registers (Pod issues certificate) and then authenticates (user sends signed token with certificate). The design’s advantages should be apparent: the server does not need to store user credentials or sessions – the token itself carries the proof and will be rejected if tampered with or expired. Everything in the token is protected by at least one signature: the `gid` and `pub` are bound by both the Pod’s and user’s signatures, and timestamps are covered by the user’s signature (and the certificate’s `exp` by the Pod’s). There are no superfluous claims or duplicate signatures, making validation straightforward and unambiguous.

Note on Sizes: The JWT in this example is longer than a typical OAuth JWT because it includes an embedded certificate and full public key. This is an expected trade-off for the stateless design – the token is self-contained. The sizes are still practical (on the order of a few hundred bytes, which is acceptable for HTTP headers). All fields use compact encodings (binary-to-text base64url), and no additional bloat like URL encoding is involved beyond the standard JWT encoding.


# 11. Conclusion

MIS v1 defines a strict, minimal set of rules for a cryptographic identity token. By using a dual-signature approach (user and Pod) and eliminating optional or variable components, it provides a clear blueprint for secure authentication:

* Precise fields only: If a piece of data is not absolutely required, it’s not in the token.
* No confusion in signing: Exactly what gets signed by whom is fixed and deterministic.
* Modern crypto only: Only the current best-practice algorithms (with an eye toward future-proofing) are allowed, and known weaker algorithms are banned.
* Robust validation: Every check that can be strict is strict – times, fields, etc. – to avoid undefined behavior or security gaps.

Implementors should find this specification strict but straightforward. By following it closely, one can achieve a secure, decentralized authentication mechanism without maintaining server-side state for identities. The design naturally supports horizontal scaling (any stateless Pod instance can verify tokens with just the knowledge of the Pod’s private key and current time) and can integrate with existing systems (since it is JWT-based). We have also considered forward compatibility: the version field and emphasis on algorithm agility mean that as threats evolve (e.g. quantum computers), a new version of MIS can be introduced without confusion, and old tokens can be phased out.

In summary, MIS v1 is an implementation-ready standard that prioritizes security and clarity. The above example and guidelines should enable developers to implement the standard correctly. By adhering to the MUSTs and MUST NOTs in this document, you will ensure that your MIS v1 implementation is interoperable with others and resistant to known classes of attacks. Always test thoroughly – especially the edge cases of time validation and signature verification – and keep your cryptographic libraries up to date. With these precautions and this spec in hand, you can provide users a secure and seamless way to authenticate using just a key pair and a token.


# Appendix A: Definitions

* **base64url**: All base64url encodings MUST be used without padding.


# Appendix B: Example generation of a Pod certificate and a client JWT

The following demo code shows how a certificate and a client JWT may be generated and verified in python.

```python
import base64
import hashlib
import json
from cryptography.hazmat.primitives.asymmetric import ed25519
from cryptography.hazmat.primitives import serialization
from datetime import datetime, timezone
from textwrap import indent


def b64url_decode_nopad(data):
    pad = '=' * (-len(data) % 4)
    return base64.urlsafe_b64decode(data + pad)
    
    
def base64url(data):
    return base64.urlsafe_b64encode(data).rstrip(b'=').decode('utf8')


def pretty(d):
    return json.dumps(d, indent=4, ensure_ascii=False)


def check_fields(d, mandatory_fields, optional_fields=None):
    mandatory_fields = mandatory_fields
    optional_fields = set() if optional_fields is None else optional_fields
    actual_fields = set([field_name for field_name in (d.keys() - optional_fields) if field_name != 'inf'])
    if mandatory_fields != actual_fields:
        raise ValueError(f"Invalid fields. Actual={d.keys()}")
    

def public_key_pem(key_pem):
    private_key = serialization.load_pem_private_key(key_pem, password=None)
    public_key = private_key.public_key()
    return public_key.public_bytes(
      encoding=serialization.Encoding.PEM,
      format=serialization.PublicFormat.SubjectPublicKeyInfo
    ).strip()


def public_key_raw(key_pem):
    private_key = serialization.load_pem_private_key(key_pem, password=None)
    public_key = private_key.public_key()
    return public_key.public_bytes(encoding=serialization.Encoding.Raw, format=serialization.PublicFormat.Raw)


def create_gid_from_key(key_pem):
    private_key = serialization.load_pem_private_key(key_pem, password=None)
    public_key = private_key.public_key()
    public_key_raw = public_key.public_bytes(encoding=serialization.Encoding.Raw, format=serialization.PublicFormat.Raw)
    return hashlib.sha256(public_key_raw).digest()
    
    
def create_cert(private_key_pem, gid, exp, inf):
    private_key = serialization.load_pem_private_key(private_key_pem, password=None)
    exp_bytes = exp.to_bytes(8, byteorder='big', signed=False)
    signing_input = gid + exp_bytes + (bytes() if inf is None else inf)
    signature = private_key.sign(signing_input)
    return {
        "gid": base64url(gid),
        "exp": exp,
        "sig": base64url(signature),
        **({} if inf is None else {"inf": base64url(inf)})
    }


def verify_cert(cert, public_key_pem, gid, curr_time):
    check_fields(cert, mandatory_fields={'gid', 'exp', 'sig'}, optional_fields={'inf'})
    
    if gid != cert['gid']:
        raise ValueError(f"Invalid GID: {gid} vs. {cert['gid']}")
    	
    if cert['exp'] <= curr_time:
        raise ValueError(f"Certificate expired")
    
    inf = b64url_decode_nopad(cert['inf']) if 'inf' in cert else bytes()
    signing_input = (
        b64url_decode_nopad(cert['gid']) +
        cert['exp'].to_bytes(8, byteorder='big', signed=False) +
        inf
    )
    
    public_key = serialization.load_pem_public_key(public_key_pem)
    public_key.verify(b64url_decode_nopad(cert['sig']), signing_input)


def create_jwt_header():
    return {
    	"alg": "EdDSA",
    	"typ": "JWT"
    }


def create_jwt_payload(cert, pub, exp, mvs):
    return {
        "pub": base64url(pub),
        "cert": cert,
        "exp": exp,
        "mvs": mvs
    }


def create_jwt(private_key_pem, header, payload):
    private_key = serialization.load_pem_private_key(private_key_pem, password=None)
    header_base64url = base64.urlsafe_b64encode(json.dumps(header, separators=(',', ':')).encode('utf8'))
    payload_base64url = base64.urlsafe_b64encode(json.dumps(payload, separators=(',', ':')).encode('utf8'))
    jwt_sign_input = header_base64url + b'.' + payload_base64url
    jwt_signature = base64.urlsafe_b64encode(private_key.sign(jwt_sign_input)).rstrip(b'=')
    jwt = jwt_sign_input + b'.' + jwt_signature

    return jwt


def verify_jwt(jwt, pod_public_key_pem, curr_time):
    parts = jwt.decode('utf8').split('.')
    if len(parts) != 3:
        raise ValueError("Invalid JWT format")

    header_b64, payload_b64, sig_b64 = parts

    header = json.loads(b64url_decode_nopad(header_b64))
    payload = json.loads(b64url_decode_nopad(payload_b64))
    signature = b64url_decode_nopad(sig_b64)

    check_fields(header, mandatory_fields={'alg', 'typ'})
    if header["alg"] != "EdDSA":
        raise ValueError("JWT algorithm is not EdDSA")
    if header["typ"] != "JWT":
        raise ValueError("JWT type is not JWT")

    signing_input = (header_b64 + "." + payload_b64).encode('utf8')
    public_key_bytes = b64url_decode_nopad(payload['pub'])
    public_key = ed25519.Ed25519PublicKey.from_public_bytes(public_key_bytes)
    public_key.verify(signature, signing_input)
    
    public_key_hash = base64url(hashlib.sha256(public_key_bytes).digest())
    verify_cert(payload['cert'], pod_public_key_pem, gid=public_key_hash, curr_time=curr_time)

    check_fields(payload, mandatory_fields={'pub', 'cert', 'exp', 'mvs'})

    if payload['exp'] <= curr_time:
        raise ValueError(f"JWT expired")
    	
    if payload['mvs'] != "1":
        raise ValueError(f"Invalid MIS Version")
    

pod_private_key_pem = b"""-----BEGIN PRIVATE KEY-----
MC4CAQAwBQYDK2VwBCIEILnp38hvbOBGFtnLyn3qQ/GAwL2zfeJKemrSiX/pJaIK
-----END PRIVATE KEY-----"""
print(f"Pod public key:\n{public_key_pem(pod_private_key_pem).decode('utf8')}\n")

client_private_key_pem = b"""-----BEGIN PRIVATE KEY-----
MC4CAQAwBQYDK2VwBCIEIFxEb2I7tPuKvihV4PgA55HDyMoVPHs2p0/nqJOBeuGG
-----END PRIVATE KEY-----"""
print(f"Client public key:\n{public_key_pem(client_private_key_pem).decode('utf8')}\n")

gid = create_gid_from_key(client_private_key_pem)
cert = create_cert(
    private_key_pem=pod_private_key_pem,
    gid=gid,
    exp=int(datetime(2021, 3, 22, 12, 37, 15, tzinfo=timezone.utc).timestamp()),
    inf=b"some additional info"
)

header = create_jwt_header()
print(f"header = {pretty(header)}")

payload = create_jwt_payload(
    cert=cert,
    pub=public_key_raw(client_private_key_pem),
    exp=int(datetime(2021, 1, 14, 9, 2, 1, tzinfo=timezone.utc).timestamp()),
    mvs="1"
)
print(f"payload = {pretty(payload)}")

jwt = create_jwt(client_private_key_pem, header, payload)
print(f"jwt = {jwt.decode('utf8')} ({len(jwt)} bytes)")
        
verify_jwt(jwt, public_key_pem(pod_private_key_pem), curr_time=int(datetime(2021, 1, 14, 9, 1, 31, tzinfo=timezone.utc).timestamp()))
print("JWT verification: OK")
```

References:

* Jones, M. et al. “JSON Web Token (JWT).” IETF RFC 7519, 2015.
* Josefsson, S., Liusvaara, I. “Edwards-Curve Digital Signature Algorithm (EdDSA).” IETF RFC 8032, 2017.
* Gueron, S., Langley, A. “JSON Web Algorithms (JWA).” IETF RFC 7518, 2015. (Defines “EdDSA” for use in JWT).
