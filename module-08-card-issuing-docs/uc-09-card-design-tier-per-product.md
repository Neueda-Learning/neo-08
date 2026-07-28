# Module 8 · Card Issuing — UC 09 · Card design / tier per product (CANDIDATE)

> AI implementation brief, generated from the v5 spec (`spec/.../use-cases/`). Source of truth is the spec; regenerate, don't hand-edit.

## Context

- Module: 8 · Card Issuing · category Integration · domain `card` · command `issue-card` · outcomes: ISSUED, FAILED
- Use case: 09 · Card design / tier per product · track C · prerequisite: after 01–08 · build shape: config field + flow branch · primary screen: Card Detail + Configuration
- Data effect: config field + record column
- Platform rules (non-negotiable): the orchestrator is the only caller; the whole application arrives in the envelope (plus the v5 `outputs` block, Option A); steps are independent and re-orderable; the payload is NEVER stored — only `applicationId`; every module ships `GET /cases/{id}/applicant` proxying the orchestrator; big lists are empty by default and capped at 10 rows (≤10 hydration calls); ALL APIs are idempotent — same request twice, same result once; every endpoint appears in the service's OpenAPI 3.0 (Swagger) spec.

## Story

A REWARDS card should not look like a STUDENT card. Real issuers map product → card design (artwork, tier, sometimes material) and send the design code to the bureau in the personalisation instruction — the factory prints what the bank's config says.

## What it adds

- IssuingConfig gains `designMap`: productCode → { designCode, tier } (e.g. STANDARD → CLASSIC/blue, REWARDS → GOLD/gold, STUDENT → CAMPUS/teal).
- The issue flow resolves the design from the pinned config version and includes designCode in the bureau instruction; CardRecord gains designCode + tier columns.
- The mock bureau echoes designCode on GET /bureau/cards/{id}, so the timeline can show what is being printed.
- The Card Detail shows a design badge; the Configuration screen edits the map — versioned like everything else, so "why did this card print CLASSIC?" pins to a version.
- No new reason codes, no callback change — design is data on the record.

## Acceptance criteria

1. Maria's REWARDS card resolves designCode GOLD, tier GOLD — stored on the record and sent in the bureau instruction.
2. A product missing from the designMap → the config validator rejects the version (400); the flow can always resolve, because an unresolvable version can never become current.
3. A new config version changing REWARDS → PLATINUM affects the next issue only; Maria's card still shows GOLD via its pinned version.
4. The Card Detail renders the design badge; the bureau's GET echoes the same code — factory and bank agree.
5. All UC 01–08 ACs still pass unchanged with the designMap present.

## Diagrams

> Rendered images first (for PDF/preview); the mermaid source is collapsed underneath for machine consumption.

### Entity model (suggested — the shape to beat)

![Entity model](diagrams/er-suggested.jpg)

**CardRecord — one card per applicationId (unique), masked from birth; no column can hold a full PAN**

| field | type | key | meaning |
|---|---|---|---|
| applicationId | string | PK | the journey key from the envelope — unique, the ONLY applicant-related column, AND the bureau instruction's reference |
| outcome | enum |  | ISSUED or FAILED — both FAILED reasons (bad address, bureau down) are fixable by a person, never a refusal |
| reference | string |  | human-facing case reference shown on screens and in the callback, e.g. crd-000064 — becomes outputs.cardId |
| panLast4 | char(4) |  | the last four digits, e.g. 4242 — the human-readable half of the masked pair; the column physically cannot hold more |
| panHash | char(64) |  | salted SHA-256 of the full PAN — answers "is this the same card?" for machines; NEVER the PAN itself |
| bureauCardId | string, nullable |  | the bureau's own id for the card, e.g. bur-77103; null on FAILED |
| bureauStatus | enum |  | the factory's lifecycle as last observed by polling: REQUESTED, PERSONALISED or DISPATCHED — moved by the bureau, never by command |
| dispatchRef | string, nullable |  | the postal tracking reference, e.g. RM-2214-9915 — null until the bureau reports DISPATCHED |
| accountId | string, nullable |  | module 7's account from outputs, e.g. acc-000123 — the account this card spends against; anchored-and-flagged if absent (re-ordered saga) |
| productCode | string |  | the product issued, e.g. CREDIT_CARD_REWARDS — drives card artwork/tier in the candidate design rule |
| manualAddress | boolean |  | true when an operator typed a corrected delivery address in the queue — the address itself is never persisted, only this trace |
| issuingConfigVersion | int | FK | the IssuingConfig version whose PAN range and delivery rules issued this card — pinned forever |
| issuedAt | timestamp, nullable |  | when the card was issued (PAN generated, bureau instructed); null on FAILED |

**IssuingConfig — insert-only, versioned PAN range and delivery rules; current = MAX(version)**

| field | type | key | meaning |
|---|---|---|---|
| version | int | PK | one new row per change — rows are inserted, never updated; current = MAX(version) |
| panPrefix | string |  | the reserved TEST range every PAN starts with (seeded 999900) — no real network owns it, so no generated number can collide with a live card |
| panLength | int |  | total PAN length including the Luhn check digit (seeded 16) |
| deliveryCountries | JSON |  | countries the bureau will post to (seeded [GB, IE]) — rule 2 rejects addresses outside them |
| requiredAddressFields | JSON |  | the fields an alternative delivery address must have (seeded [line1, city, postcode, country]) |
| bureauBaseUrl | string |  | where the mock bureau lives — the base URL for instructions and status polls |
| effectiveFrom | timestamp |  | when this version became the current one |

**CardStatusHistory — the observed bureau lifecycle; one append-only row per transition, written by the poller**

| field | type | key | meaning |
|---|---|---|---|
| applicationId | string | FK | the card whose observed transition this row records |
| status | enum |  | the bureau status observed: REQUESTED, PERSONALISED or DISPATCHED — the timeline screen reads straight off this |
| source | enum |  | how the observation happened: ISSUE (the initial instruction) or POLL (the scheduled status check) |
| observedAt | timestamp |  | when THIS module saw the transition — the bureau moves on its own clock; we find out by asking |

**OverrideLog — audit trail; one row per manual override, none ever deleted**

| field | type | key | meaning |
|---|---|---|---|
| applicationId | string | FK | the card case that was overridden |
| oldOutcome | enum |  | the outcome before the override |
| newOutcome | enum |  | the outcome after — a human-known fact asserted without touching the bureau |
| reason | string |  | the mandatory justification typed by the operator |
| operator | string |  | who performed the override |
| overriddenAt | timestamp |  | when it happened |

Relationships: CardRecord 1:N CardStatusHistory — every observed bureau transition writes one row · CardRecord N:1 IssuingConfig — each card pins the config version that issued it · CardRecord 1:N OverrideLog — every restricted override is audited against its case

<details><summary>mermaid source (generated from the spec tables)</summary>

```mermaid
flowchart LR
    CardRecord["<b>CardRecord</b><br/>————————<br/>applicationId (PK)<br/>outcome<br/>reference<br/>panLast4<br/>panHash<br/>bureauCardId<br/>bureauStatus<br/>dispatchRef<br/>accountId<br/>productCode<br/>manualAddress<br/>issuingConfigVersion (FK)<br/>issuedAt"]
    IssuingConfig["<b>IssuingConfig</b><br/>————————<br/>version (PK)<br/>panPrefix<br/>panLength<br/>deliveryCountries<br/>requiredAddressFields<br/>bureauBaseUrl<br/>effectiveFrom"]
    CardStatusHistory["<b>CardStatusHistory</b><br/>————————<br/>applicationId (FK)<br/>status<br/>source<br/>observedAt"]
    OverrideLog["<b>OverrideLog</b><br/>————————<br/>applicationId (FK)<br/>oldOutcome<br/>newOutcome<br/>reason<br/>operator<br/>overriddenAt"]
    CardRecord -->|"every observed bureau transition writes one row (1:N)"| CardStatusHistory
    CardRecord -->|"each card pins the config version that issued it (N:1)"| IssuingConfig
    CardRecord -->|"every restricted override is audited against its case (1:N)"| OverrideLog
    classDef ent fill:#ffffff,stroke:#2EA98D,color:#22302B
    class CardRecord ent
    class IssuingConfig ent
    class CardStatusHistory ent
    class OverrideLog ent
```

</details>

### State transitions — the case record

![State transitions — the case record](diagrams/case-states.jpg)

<details><summary>mermaid source</summary>

```mermaid
stateDiagram-v2
    direction LR
    [*] --> IN_PROGRESS : /execute accepted (202)
    IN_PROGRESS --> ISSUED : PAN masked + bureau instructed
    IN_PROGRESS --> FAILED : bad address · bureau down
    FAILED --> ISSUED : queue — fix address / retry · FRESH PAN · local-manual
    ISSUED --> FAILED : override (operator + reason)
    FAILED --> ISSUED : override — fact known out of band
    note right of FAILED
        both reasons are FIXABLE —
        that is why the status is
        application-manual, never rejected
    end note
    note left of ISSUED
        bureauStatus moves separately:
        REQUESTED→PERSONALISED→DISPATCHED
        (own diagram — observed by polling)
    end note
    classDef ok fill:#ffffff,stroke:#1F8A5D,color:#1F8A5D,font-weight:bold
    classDef bad fill:#ffffff,stroke:#B3403A,color:#B3403A,font-weight:bold
    classDef trans fill:#ECF6F1,stroke:#4A635B,color:#22302B
    class ISSUED ok
    class FAILED bad
    class IN_PROGRESS trans
```

</details>

### State transitions — the versioned configuration

![State transitions — the versioned configuration](diagrams/config-states.jpg)

<details><summary>mermaid source</summary>

```mermaid
stateDiagram-v2
    direction LR
    [*] --> CURRENT : new version inserted (POST /config)
    CURRENT --> SUPERSEDED : a newer version is inserted
    note right of CURRENT
        current = MAX(version)
        panPrefix (guarded inside 9999xx) ·
        delivery rules · next issue uses it
    end note
    note right of SUPERSEDED
        never edited, never deleted
        still pinned by the cards it issued —
        "which numbers COULD exist?" stays answerable
    end note
    classDef cur fill:#ffffff,stroke:#2EA98D,color:#2EA98D,font-weight:bold
    classDef sup fill:#ECF6F1,stroke:#5E736B,color:#5E736B,font-weight:bold
    class CURRENT cur
    class SUPERSEDED sup
```

</details>

## Why this one

Cheapest candidate on the module's list: one config field, one record column, one instruction field — no new entity, no new endpoint, no contract change. And it completes the product story: modules 1–5 decided WHETHER and HOW MUCH; this decides what lands on the doormat looking like.

## Definition of done

All acceptance criteria verified against the running app · `./mvnw test` green · teammate-reviewed PR merged to main · fresh clone builds green · endpoint idempotent + present in the OpenAPI 3.0 (Swagger) spec · demoable from the UI.
