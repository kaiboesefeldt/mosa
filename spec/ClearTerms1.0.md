# ClearTerms: Transparent Legal Agreements by Explicit Profile Chains

## Draft Specification v1.0

### Abstract

The **ClearTerms** is a compositional model for representing legal and normative texts (e.g., privacy policies, terms of service, user agreements, and codes of conduct) in a form that is transparent to end users, technically verifiable, and suitable for federated and international environments.

ClearTerms separates a shared, fair reference text (**Baseline**) from one or more explicitly chained **Profiles**. Trust is earned by making every change explicit and machine-diffable, not by producing longer documents.

The key words **MUST**, **MUST NOT**, **SHOULD**, **MAY**, and **RECOMMENDED** are to be interpreted as described in RFC 2119.

---

## 1. Design Goals

ClearTerms is designed to:

* Maximize transparency of policy texts
* Minimize cognitive overload for non-expert users
* Enable deterministic rendering and strict validation
* Support client-side trust evaluation (including loading references from independent sources)
* Support internationalization through language-specific texts and stable semantic diffs
* Work well in federated and client-driven ecosystems

ClearTerms does not attempt to replace legal interpretation, encode jurisdictional law directly, or provide a general-purpose policy programming language.

---

## 2. Model Overview

A ClearTerms document is produced by composing one Baseline with one or more Profiles:

1. **Baseline**: normative text blocks with placeholders, but no concrete values.
2. **Profile**: references a parent artifact via `parent` (either a Baseline or another Profile), overrides or adds values, and, exceptionally, may add or override text blocks.

**Trust model**: Clients compare an operator profile against an upstream profile anchored in a Baseline. Smaller deltas indicate stronger alignment with the shared baseline.

---

## 3. Artifact Types

### 3.1 Baseline

A Baseline defines the normative and ethical reference text.

A Baseline:

* MUST provide one or more text blocks per language
* MAY set individual text blocks to `null` to mark them as intentionally undefined in the Baseline and required in a Profile
* MAY contain placeholders (see §6)
* MUST be versioned
* SHOULD be published by a trusted third party (e.g., NGO, foundation, community)

A Baseline SHOULD NOT contain operator-specific values.

All placeholder keys used in a Baseline MUST have a single, consistent type and meaning across all language variants.

### 3.2 Profile

A Profile contributes values and optional text overrides or additions for a specific Baseline lineage.

A Profile:

* MUST reference exactly one parent artifact via `parent`
* MUST be versioned
* MAY provide values for any placeholders used by the final text
* MUST NOT introduce new placeholder keys not referenced in text blocks
* MAY override placeholder values

As an exceptional measure, that SHOULD be a method of last resort, a profile MAY modify text blocks as follows:

* MAY replace existing Baseline text blocks
* MAY append additional text blocks to the end of the document
* MAY introduce new placeholder keys in new or replaced text blocks.

Profiles MUST NOT insert text blocks between existing blocks (i.e. no arbitrary insertion).

**Baseline + resolved Profile chain** MUST render to a complete policy text with no unresolved placeholders.

### 3.3 Profile Chain

Profile chains define how a target Profile is resolved.

* A Profile whose `parent` references a Baseline is the first Profile in a chain.
* A Profile whose `parent` references another Profile extends that chain.
* Resolution MUST follow `parent` references.
* Resolution stops when the current Profile references an artifact with kind `baseline`.
* Every referenced `parent` artifact kind MUST be either `baseline` or `profile`.
* Chains MUST be acyclic.

In practice, resolving a valid chain always leads to one baseline lineage because each profile has exactly one parent edge and chain resolution terminates at a baseline.

### 3.4 Text Resolution Terms

To avoid ambiguity, ClearTerms distinguishes between text states along a profile chain:

* `intermediate text`: the text state at a specific chain step (Baseline plus all text overrides up to and including a specific Profile)
* `final text`: the `intermediate text` of the resolved target Profile that is rendered to the user

---

## 4. Referencing, Integrity, and Trust Decoupling

### 4.1 Content Addressing

Artifacts are referenced by cryptographic hash over the exact byte sequence of their canonical UTF-8 encoded representation (see §9.0).

* References MUST include a `hash`.
* References MUST also include `id` and `version` for human readability.
* `hash` is authoritative; `id` and `version` are binding assertions that MUST match the resolved artifact (see §4.2).

### 4.2 Reference Consistency Requirements

When an artifact references another artifact:

* The referenced artifact, when resolved by `hash`, MUST expose the same `id` and `version` as stated in the reference.
* Any mismatch of `id`, `version`, or `hash` MUST be treated as a validation error.

This prevents “hash points to one thing, metadata claims another” situations.

### 4.3 Trust Decoupling

Clients:

* SHOULD be able to load Baselines and Profiles from sources other than the operator
* MUST verify reference chains
* SHOULD treat any mismatch or missing referenced artifact as fatal

---

## 5. Rendering Model

### 5.1 Text Format

* Text blocks MAY be written in any text format. ClearTerms does not prescribe a markup language.
* A text block with `null` content is unresolved and MUST be replaced by a Profile before rendering.
* Placeholder substitution MUST occur before any client-specific rendering.
* After placeholder substitution, the resulting text MUST NOT contain any `{{` sequences that match placeholder syntax.

### 5.2 Block Identity and Order

* Text blocks are concatenated in their defined order.
* Each text block MUST have a stable block identifier (`block id`).
* Block ids MUST be unique within a Baseline across all languages (i.e. a block id identifies exactly one logical block globally).
* Within a given language, block ids MUST be unique.
* For multi-language Baselines, corresponding blocks across languages MUST use the same block id.
* For multi-language Baselines, the set of block ids and their order MUST be identical across all languages.
* These constraints also apply to blocks appended via Profile overrides: appended blocks MUST use identical block ids across all Baseline languages, positionally aligned.

### 5.3 Placeholder Discovery

* Placeholders are discovered by scanning text for placeholder syntax.
* No separate placeholder declaration exists.

---

## 6. Placeholders and Types

### 6.1 Placeholder Syntax

Placeholders in text blocks MUST use one of the following forms:

* `{{type:key}}`
* `{{type:key?options}}`
* `{{type:key??fallback}}`
* `{{type:key?options??fallback}}`

`options` is a restricted key-value list (`key=value&key=value`). Options are type-specific and MUST be rejected if unknown or invalid.

`fallback` is a language-specific literal string fragment that is rendered verbatim if (and only if) the resolved value of `key` is `null`. `fallback` is never formatted by `options`.

`options` parsing rules:

* The `?options` section ends at the first occurrence of `??`.
* `options` values MUST match the restricted option grammar defined by the placeholder type.
* `fallback` MUST NOT contain the sequence `??`.

#### Escaping

To allow literal occurrences of `{{` and `}}` in text blocks, ClearTerms defines an explicit escaping mechanism:

* `\{{` represents a literal `{{`
* `\}}` represents a literal `}}`

Escaping rules:

1. Escaped sequences (`\{{`, `\}}`) MUST be processed before placeholder detection.
2. Escaped sequences MUST NOT be interpreted as placeholder delimiters.
3. After placeholder substitution and unescaping, the resulting text MUST NOT contain any unescaped `{{` sequences that match placeholder syntax.

### 6.2 Type System

Supported types:

* `string`
* `number`
* `duration`
* `time`
* `weekday`

No additional types are defined in this version.

### 6.3 Null Semantics and Fallbacks

* All types (`string`, `number`, `duration`, `time`, `weekday`) MAY be `null`, meaning “not specified”.
* If a value is `null`, rendering MUST use the final text’s inline fallback (`??fallback`), if present.
* `fallback` is rendered verbatim and MUST NOT be processed by any formatting option.
* Fallbacks are defined inline in text blocks and are not part of profile values.
* If a placeholder evaluates to `null` and no fallback exists in the final text for the relevant language, rendering MUST fail.

### 6.4 `number` Formatting

`number` placeholders MAY define rendering options via `?options`.

Supported options:

* `format`
  * `integer`: round to nearest integer, no fractional digits
  * `decimal(n)`: render with exactly `n` fractional digits, rounded to nearest with ties away from zero
  * `auto`: if the value is an integer, render without fractional digits; otherwise render without trailing zeros
* `grouping`
  * `on`: use locale-specific thousands separators
  * `off`: no grouping

Defaults:

* `format=auto`
* `grouping=on`

Formatting uses the Baseline language for locale selection.

### 6.5 `duration`

Allowed values:

* `<number><unit>` where unit ∈ {`s`, `m`, `h`, `d`, `w`, `y`}
* `infinite`
* `null`

Months are explicitly excluded.

Semantics:

* `infinite` means explicitly unbounded
* `null` means unspecified (and MUST NOT be treated as `infinite`)

### 6.6 `time`

Time values are ISO-like strings with wildcards.

Accepted forms:

* `YYYY-MM-DD`
* `YYYY-MM-DDThh:mm[:ss](Z|[+-]hh:mm)`
* Wildcards: `*` MAY replace any of `YYYY`, `MM`, `DD` in the forms above.

Wildcards MUST NOT be used in the time-of-day components (`hh`, `mm`, `ss`).

Rules:

* If a time-of-day component (`T…`) is present, a timezone MUST be present.
* The timezone MUST be expressed either as `Z` or as an explicit offset from UTC (e.g. `+02:00`, `-05:00`).
* Named time zones (e.g. `Europe/Berlin`) MUST NOT be used.
* Numeric components MUST use zero-padded fixed width: `YYYY` (4 digits), `MM` (2 digits), `DD` (2 digits), `hh` (2 digits), `mm` (2 digits), `ss` (2 digits).
* If `DD` is a number larger than the maximum day of the resolved month, it MUST be interpreted as the last day of that month.
* The overflow rule only applies to `DD`. Values outside the allowed range for `YYYY` or `MM` are invalid.
* `YYYY-*-DD`, `*-MM-DD`, and `*-*-*` are valid forms.

English rendering for wildcard combinations:

| Pattern                             | Rendering (English) | Example |
|-------------------------------------| --- | --- |
| `YYYY-MM-DD`                        | `on <Month> <DD>, <YYYY>` | `2026-02-08 -> on February 8, 2026` |
| `YYYY-MM-*`                         | `in <Month> <YYYY>` | `2026-02-* -> in February 2026` |
| `YYYY-*-DD`                         | `on day <DD> of every month in <YYYY>` | `2026-*-15 -> on day 15 of every month in 2026` |
| `YYYY-*-*`                          | `in <YYYY>` | `2026-*-* -> in 2026` |
| `*-MM-DD`                           | `on <Month> <DD> every year` | `*-02-15 -> on February 15 every year` |
| `*-MM-*`                            | `every day in <Month>` | `*-02-* -> every day in February` |
| `*-*-DD`                            | `on day <DD> of every month` | `*-*-15 -> on day 15 of every month` |
| `*-*-*`                             | `every day` | `*-*-* -> every day` |
| `YYYY-MM-DD` (overflow)             | `on the last day of <Month> <YYYY>` | `2026-02-30 -> on the last day of February 2026` |
| `*-MM-DD` (overflow)                | `on the last day of <Month> every year` | `*-02-30 -> on the last day of February every year` |
| `YYYY-*-DD` (overflow)              | `on the last day of every month in <YYYY>` | `2026-*-31 -> on the last day of every month in 2026` |
| `*-*-DD` (overflow)                 | `on the last day of every month` | `*-*-31 -> on the last day of every month` |
| `YYYY-MM-DDThh:mm[:ss](Z\|[+-]hh:mm)` | `on <Month> <DD>, <YYYY> at <time zone time>` | `2026-02-08T09:00Z -> on February 8, 2026 at 09:00Z` |
| `YYYY-MM-*Thh:mm[:ss](Z\|[+-]hh:mm)` | `in <Month> <YYYY> at <time zone time>` | `2026-02-*T09:00+01:00 -> in February 2026 at 09:00+01:00` |
| `YYYY-*-DDThh:mm[:ss](Z\|[+-]hh:mm)` | `on day <DD> of every month in <YYYY> at <time zone time>` | `2026-*-15T09:00Z -> on day 15 of every month in 2026 at 09:00Z` |
| `YYYY-*-*Thh:mm[:ss](Z\|[+-]hh:mm)` | `in <YYYY> at <time zone time>` | `2026-*-*T09:00Z -> in 2026 at 09:00Z` |
| `*-MM-DDThh:mm[:ss](Z\|[+-]hh:mm)` | `on <Month> <DD> every year at <time zone time>` | `*-02-15T09:00Z -> on February 15 every year at 09:00Z` |
| `*-MM-*Thh:mm[:ss](Z\|[+-]hh:mm)` | `every day in <Month> at <time zone time>` | `*-02-*T09:00Z -> every day in February at 09:00Z` |
| `*-*-DDThh:mm[:ss](Z\|[+-]hh:mm)` | `on day <DD> of every month at <time zone time>` | `*-*-15T12:00:00+01:00 -> on day 15 of every month at 12:00:00+01:00` |
| `*-*-*Thh:mm[:ss](Z\|[+-]hh:mm)` | `every day at <time zone time>` | `*-*-*T09:00Z -> every day at 09:00Z` |

Semantics:

* Absolute timestamps with timezone offsets or `Z` represent a fixed instant in time.
* Wildcard components represent recurring or relative calendar events.
* `null` is allowed and triggers inline fallback.

### 6.7 `weekday`

Allowed values (lowercase, abbreviated):

* `mon`, `tue`, `wed`, `thu`, `fri`, `sat`, `sun`

The abbreviations are fixed and locale-independent.

`null` is allowed and triggers inline fallback.

---

## 7. Internationalization

* Baselines MUST contain text blocks for at least one language.
* Text blocks MUST exist per language.
* Placeholder values are language-independent, but their rendering is language-specific.
* Fallbacks are language-specific because they appear inline in text blocks of intermediate and final text states.
* Formatting options use the Baseline language for locale selection.
* Language tags follow BCP 47. Locale selection uses the primary language subtag (e.g. `de-CH` -> `de`). Unsupported languages MUST fall back to `en`.

---

## 8. Validation Rules

Validation MUST be strict. There is no best-effort interpretation.

Implementations MUST validate:

* Reference chain integrity (hash) and reference consistency (`id`/`version` match; §4.2)
* Profile chains MUST be acyclic
* Type correctness of all placeholder values
* Completeness: every discovered placeholder key MUST have a value in the effective profile
* Completeness is evaluated on the effective profile (merged across the resolved Profile chain), not on a single Profile alone
* Profiles MUST NOT contain unused placeholder keys
* No unresolved placeholders after substitution (i.e. no remaining unescaped `{{` sequences that match placeholder syntax)
* For every `null` value, an applicable inline fallback in the final text MUST exist
* Final text blocks MUST NOT be `null`; every `null` text block from the Baseline MUST be replaced through Profile `replace` overrides

Invalid ClearTerms configurations MUST be treated as configuration errors.

---

## 9. JSON Artifact Format (Normative)

JSON is the canonical interchange format.

All JSON artifacts:

* MUST be UTF-8 encoded
* MUST use JSON as defined in RFC 8259
* MUST be a single object at the root
* MUST reject unknown keys at any nesting level
* MUST reject duplicate keys at any level

### 9.0 Canonicalization and Hashing (Normative)

Hashing MUST be performed over a canonical UTF-8 representation of the JSON artifact to avoid hash instability caused by formatting differences.

Canonicalization rules:

* The JSON document MUST be serialized as a single object with keys sorted lexicographically at every level.
* Arrays MUST preserve order.
* Scalars MUST be rendered in their explicit JSON type:
  * strings MUST use double-quoted style with escaped characters as needed
  * numbers MUST be rendered in decimal form without underscores
  * `null` MUST be rendered as `null`
* No extra whitespace is allowed in the canonical form.

The hash MUST be computed over the exact UTF-8 bytes of this canonical representation.

### 9.1 Baseline JSON

A Baseline JSON document MUST contain exactly:

```json
{
  "kind": "baseline",
  "id": "<string>",
  "version": "<string>",
  "texts": {
    "<language-tag>": [
      { "id": "<block-id>", "text": "<text block>" },
      { "id": "<block-id>", "text": null }
    ]
  }
}
```

Rules:

* Each block MUST be an object with keys `id` and `text`.
* `id` MUST be a string and MUST be stable within the Baseline family.
* `text` MUST be either a string or `null`.
* For multi-language Baselines, corresponding blocks across languages MUST use the same block id.
* For multi-language Baselines, the set and order of block ids MUST be identical across languages.
* For multi-language Baselines, if a block id has `text: null` in one language, it MUST have `text: null` in all Baseline languages.

### 9.2 Profile JSON

A Profile JSON document MUST contain exactly:

```json
{
  "kind": "profile",
  "id": "<string>",
  "version": "<string>",
  "parent": {
    "id": "<string>",
    "version": "<string>",
    "hash": "<hash>"
  },
  "overrides": {
    "values": {
      "<key>": "<typed value>"
    },
    "texts": {
      "<language-tag>": {
        "replace": {
          "<block-id>": "<text block>"
        },
        "append": [
          { "id": "<block-id>", "text": "<text block>" },
          { "id": "<block-id>", "text": "<text block>" }
        ]
      }
    }
  }
}
```

Rules:

* Top-level keys MUST be exactly: `kind`, `id`, `version`, `parent`, `overrides`.
* `parent` MUST be present.
* `parent` MUST be a mapping with exactly the keys `id`, `version`, and `hash`.
* `overrides.values` MUST be present.
* `overrides.texts` MAY be omitted.
* Keys inside `overrides` MUST be exactly: `values` and optionally `texts`.
* `overrides.values` MUST be a mapping from placeholder key to typed scalar JSON value (see §9.3).
* `overrides.texts` MUST only target languages present in the referenced Baseline and MUST be present for all Baseline languages if present at all.
* If `overrides.texts` is present, each language entry MUST be a mapping with exactly the keys `replace` and `append`.
* `replace` MUST be a mapping from existing block id to text block string.
* `append` MUST be a list of text blocks, each with exactly the keys `id` and `text`.
* `replace` replaces existing blocks by block id.
* Implementations MUST reject attempts to replace a non-existent block id.
* `replace` values MUST be non-null strings.
* `append` adds additional blocks to the end of the language’s block list, preserving order.
* Appended blocks MUST specify `id` and `text`, and appended block ids MUST be unique and MUST NOT collide with existing Baseline block ids.
* For multi-language Baselines:
  * `replace` MUST define the same set of block ids in every language.
  * `append` MUST append the same number of blocks in every language, and the appended block ids MUST be identical and positionally aligned across languages.
* Implementations MUST reject any other form of insertion.

* The parent reference MUST satisfy §4.2.
* The resolved parent artifact kind MUST be either `baseline` or `profile`.

Additional constraints for multi-language Baselines:

* `replace` operations MUST be defined for the same set of block ids in every language.
* `append` operations MUST append the same number of blocks in every language (one appended block position corresponds across languages), preserving order.
* Implementations MUST reject profiles that modify texts for only a subset of the Baseline’s languages.

### 9.3 Typed Values in JSON

* `string`: JSON string, or null
* `number`: JSON number, or null
* `duration`: JSON string (`<number><unit>` or `infinite`) or null
* `time`: JSON string (per §6.6) or null
* `weekday`: JSON string (per §6.7) or null

---

## 10. Change Management

* Baseline versions MUST be explicitly visible.
* Profile versions are authoritative.
* Changes MUST be textually inspectable, machine-diffable, and UI-explainable.
* There are no implicit upgrades.

---

## 11. Security and Trust Considerations

ClearTerms’ primary security property is **inspectability**.

* Treat mismatches and missing references as fatal.
* Prefer minimal profile deltas; large deltas are a user-facing risk signal.

---

## 12. Complete Minimal Example (Non-Normative)

This example uses all placeholder types and demonstrates `null` triggering inline fallbacks.

### 12.1 Baseline

```json
{
  "kind": "baseline",
  "id": "example-policy",
  "version": "1.0",
  "texts": {
    "en": [
      {
        "id": "retention",
        "text": "We retain personal data for {{duration:retention_period??as long as necessary}}."
      },
      {
        "id": "start",
        "text": "Data processing starts at {{time:start_time??a reasonable time}}."
      },
      {
        "id": "support",
        "text": "Our support is available on {{weekday:support_day??business days}}."
      },
      {
        "id": "limit",
        "text": "The maximum number of stored records is {{number:max_records??a limited number}}."
      },
      {
        "id": "operator",
        "text": "The responsible organization is {{string:operator_name}}."
      }
    ],
    "de": [
      {
        "id": "retention",
        "text": "Wir speichern personenbezogene Daten für {{duration:retention_period??eine angemessene Zeit}}."
      },
      {
        "id": "start",
        "text": "Die Datenverarbeitung beginnt um {{time:start_time??einen angemessenen Zeitpunkt}}."
      },
      {
        "id": "support",
        "text": "Unser Support ist erreichbar an {{weekday:support_day??Werktagen}}."
      },
      {
        "id": "limit",
        "text": "Die maximale Anzahl gespeicherter Einträge beträgt {{number:max_records??eine begrenzte Anzahl}}."
      },
      {
        "id": "operator",
        "text": "Verantwortliche Organisation ist {{string:operator_name}}."
      }
    ]
  }
}
```

### 12.2 Profile A

```json
{
  "kind": "profile",
  "id": "example-policy-profile-a",
  "version": "1.0",
  "parent": {
    "id": "example-policy",
    "version": "1.0",
    "hash": "sha256:BASELINE_HASH"
  },
  "overrides": {
    "values": {
      "retention_period": "30d",
      "start_time": "2025-03-23T13:33:43Z",
      "support_day": "mon",
      "max_records": 1000,
      "operator_name": "Example Organization"
    }
  }
}
```

### 12.3 Profile B

```json
{
  "kind": "profile",
  "id": "example-policy-operator",
  "version": "1.0",
  "parent": {
    "id": "example-policy-profile-a",
    "version": "1.0",
    "hash": "sha256:PROFILE_A_HASH"
  },
  "overrides": {
    "values": {
      "retention_period": "infinite",
      "max_records": null
    },
    "texts": {
      "en": {
        "replace": {
          "support": "Our support is available on {{weekday:support_day??business days}}.\n(Operator note: support response times may vary.)"
        },
        "append": [
          {
            "id": "extra",
            "text": "Additional terms: This operator applies stricter rate limits under high load."
          }
        ]
      },
      "de": {
        "replace": {
          "support": "Unser Support ist erreichbar an {{weekday:support_day??Werktagen}}.\n(Hinweis des Betreibers: Reaktionszeiten können variieren.)"
        },
        "append": [
          {
            "id": "extra",
            "text": "Zusätzliche Bestimmungen: Dieser Betreiber wendet unter hoher Last strengere Ratenbegrenzungen an."
          }
        ]
      }
    }
  }
}
```

Notes:

* `max_records: null` triggers the inline fallback `??a limited number`.
* Profile B references Profile A, forming a two-step chain.
* Text blocks are overridden for `support` and an additional `extra` block is appended.
