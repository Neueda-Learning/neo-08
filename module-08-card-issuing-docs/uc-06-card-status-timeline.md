# Module 8 · Card Issuing — UC 06 · Card Status Timeline

> AI implementation brief, generated from the v5 spec (`spec/.../use-cases/`). Source of truth is the spec; regenerate, don't hand-edit.

## Context

- Module: 8 · Card Issuing · category Integration · domain `card` · command `issue-card` · outcomes: ISSUED, FAILED
- Use case: 06 · Card Status Timeline · track D · prerequisite: after 05 (bureau moving) + a card from 02 · build shape: poller→DB→API→FE · primary screen: Card Status Timeline
- Data effect: poller appends history rows
- Platform rules (non-negotiable): the orchestrator is the only caller; the whole application arrives in the envelope (plus the v5 `outputs` block, Option A); steps are independent and re-orderable; the payload is NEVER stored — only `applicationId`; every module ships `GET /cases/{id}/applicant` proxying the orchestrator; big lists are empty by default and capped at 10 rows (≤10 hydration calls); ALL APIs are idempotent — same request twice, same result once; every endpoint appears in the service's OpenAPI 3.0 (Swagger) spec.

## Story

As a bank employee I want each card's lifecycle as this module observed it — when it was requested, personalised, dispatched, and with what reference — so "where is my card?" has an answer without calling the factory.

## Contract

```
GET /cases/{id}/timeline →
[{"status":"REQUESTED",
  "observedAt":"…T09:21:04Z","source":"ISSUE"},
 {"status":"PERSONALISED",
  "observedAt":"…T09:21:09Z","source":"POLL"},
 {"status":"DISPATCHED",
  "observedAt":"…T09:21:14Z","source":"POLL",
  "dispatchRef":"RM-2214-9915"}]
```

## Acceptance criteria

1. GET /cases/{id}/timeline → 200 + every observed transition in order: status, observedAt, source (ISSUE | POLL), dispatchRef on the final entry.
2. The poller appends a row ONLY on change — polling an unchanged card adds nothing (dedupe against the latest row).
3. At demo speed (secondsPerStage=5) Maria's timeline shows exactly 3 rows — REQUESTED (ISSUE), PERSONALISED (POLL), DISPATCHED (POLL) with dispatchRef RM-2214-9915 — within 15 seconds.  ⟵ **checkpoint — exact value**
4. card_record.bureauStatus always equals the latest history row's status; the board's chip (UC 01) follows it.
5. If the poller was down while the bureau moved two stages, the next poll records the jump as observed — one row, REQUESTED→DISPATCHED, never an invented PERSONALISED row.
6. DISPATCHED cards are no longer polled; FAILED cards never are.
7. Timeline of a FAILED-on-address card → 200 + [] — no lifecycle ever started, and the screen says why.

## Expected data changes

- **INSERT card_status_history** — append-only, one row per observed change; nothing ever edits or deletes one.
- **UPDATE card_record.bureauStatus** — the mirror of the latest row, for cheap board chips.
- The outcome column never changes on this path — ISSUED was decided at issue time; the lifecycle is a separate machine.

## Diagrams

> Rendered images first (for PDF/preview); the mermaid source is collapsed underneath for machine consumption.

### Sequence — this use case

![Sequence — this use case](diagrams/uc-06-sequence.jpg)

<details><summary>mermaid source</summary>

```mermaid
sequenceDiagram
    autonumber
    participant Poller
    participant Bureau
    participant MySQL
    participant UI
    participant Controller
    Poller->>MySQL: cards not yet DISPATCHED
    MySQL-->>Poller: bur-77103, …
    Poller->>Bureau: GET /bureau/cards/bur-77103
    Bureau-->>Poller: PERSONALISED
    Poller->>MySQL: INSERT history + UPDATE bureauStatus
    UI->>Controller: GET /cases/{id}/timeline
    Controller-->>UI: 200 — observed rows, in order
    Note over Poller,Controller: The factory knows before the bank does — the poller closes that gap on a schedule, and the timeline is the record of what was SEEN, not what merely happened.
```

</details>

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

## Out of scope

Commanding the bureau (the module observes, never drives the lifecycle); backfilling hops the poller missed (record what was SEEN — a jump REQUESTED→DISPATCHED is honest history).

## Build notes

A scheduled poller (every few seconds; interval configurable) asks GET /bureau/cards/{id} for every non-DISPATCHED card and appends a CardStatusHistory row on every observed CHANGE — dedupe by comparing to the latest row. The first row (REQUESTED, source ISSUE) is written by the issue flow itself. bureauStatus on card_record mirrors the latest row. DISPATCHED cards leave the polling set — the poll bill is bounded.

## Tests

Poller service test with a scripted mock: change → one row, no change → no row, missed hop → two-row jump recorded as seen; slice test for the GET's ordering.

## Sequence caption

The factory knows before the bank does — the poller closes that gap on a schedule, and the timeline is the record of what was SEEN, not what merely happened.

## Definition of done

All acceptance criteria verified against the running app · `./mvnw test` green · teammate-reviewed PR merged to main · fresh clone builds green · endpoint idempotent + present in the OpenAPI 3.0 (Swagger) spec · demoable from the UI.
