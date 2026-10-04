# research-tool-integrity Specification

## Purpose
Provides prospective component-consistent and resource-bounded offline census parsing, while preserving immutable historical tools and evidence for recorded-version replay.

## Requirements

### Requirement: Consistent signature transaction and instruction evidence
The new versioned census parser SHALL require each observed transaction signature to resolve consistently to one slot and transaction index, and each signature plus numeric instruction path to have consistent immutable contents. Multiple different instruction paths within one transaction SHALL remain valid. Conflicting pages SHALL fail before publishing accepted creations or partially applying page counters.

#### Scenario: A signature appears under conflicting indices
- **WHEN** saved or synthetic input presents one signature under two transaction indices or conflicting immutable instruction contents
- **THEN** admission SHALL report an explicit conflict without accepting partial page state
- **AND** distinct instruction paths under the same consistent transaction SHALL remain allowed.

### Requirement: Layout bounded decoding and single page provenance
Before synchronous instruction decoding, the new parser SHALL reject instruction data larger than its declared supported layout bound in encoded and decoded units. Every creation from one input page SHALL resolve the same exact page digest without recomputing the complete page digest separately for each creation. Unsupported layouts SHALL retain an explicit unsupported status rather than become admitted facts.

#### Scenario: Oversized instruction data
- **WHEN** instruction data exceeds the versioned layout-derived bound
- **THEN** parsing SHALL reject or explicitly classify that input before expensive decoding
- **AND** accepted creations SHALL not be published from a conflicting rejected page.

### Requirement: Prospective helpers and read-only historical audit
New tools SHALL depend on a separately versioned parsing, canonicalization and bounded transport helper contract rather than mutable behavior in entire historical collectors. Historical tools, pins, manifests, receipts and terminal results SHALL remain byte-for-byte unchanged. A historical relationship audit SHALL read source creations without rewriting them and report checked inputs, hashes, conflicts and incomplete coverage honestly; it SHALL NOT admit exploratory inputs into D1 or rerun census.

#### Scenario: Audit a historical transaction with several creations
- **WHEN** a read-only audit observes several distinct creation paths for one consistent historical signature
- **THEN** it SHALL not report duplication solely because the signature repeats
- **AND** it SHALL report contradictory slot, index or same-path contents separately with source references and unchanged source hashes.
