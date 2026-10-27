# FlakeTax

A friend-group accountability engine built with Java 21, Spring Boot, Spring Data JPA, PostgreSQL, and vanilla JavaScript.

## Core Features

- **Pact Creation & Host Stake:** Creating a pact locks the host's stake immediately and opens the pool.
- **Social Discovery:** Discover open pacts, browse challenges hosted by accepted friends, or look up pacts by ID.
- **Friend System:** Mutual friend requests and removals using handles.
- **Proof & Media Storage:** Submit text notes or proof images (JPG, PNG, WebP up to 2 MB) saved to a configured storage path.
- **Anti-Self-Approval Review:** Hosts review participant proofs; participant consensus or alternate members review host proofs to prevent self-approval.
- **Deterministic Settlement:** Split logic divides the pot evenly among approved participants with remainder handling; open pacts can be cancelled for full stake refunds before deadlines.
- **Archival vs. Audit:** Pacts can be hidden from active lists while preserving immutable ledger entries for balance auditing.

## Local Setup (Windows / IntelliJ)

### Prerequisites
- Java 21
- PostgreSQL 17 (local or Docker)
- IntelliJ IDEA / Maven

### Configuration
Set the following environment variables in your IntelliJ Run Configuration (`FlakeTaxApp`):

```text
DB_URL=jdbc:postgresql://localhost:5432/flaketax
DB_USER=flaketax
DB_PASSWORD=your_local_password
REVIEW_MINUTES=1
PROOF_DIR=./data/proofs