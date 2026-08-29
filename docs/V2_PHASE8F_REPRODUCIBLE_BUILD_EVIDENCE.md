# Phase 8F reproducible release-build evidence

Date: 2026-08-28

Authoritative command: `mvnw.cmd --no-transfer-progress clean verify`

## Corrected Owner Review 3 candidate result

| Output | Sealed build bytes | Sealed build SHA-256 | Comparison build bytes | Comparison build SHA-256 | Equal |
|---|---:|---|---:|---|---|
| `MaddPrestige-2.0.0-rc.1.jar` | 16,507,914 | `0D961DB73D6C44CF18986CA42B7950914CEA5A1598BA7D41D0E919BFB76C6513` | 16,507,914 | `0D961DB73D6C44CF18986CA42B7950914CEA5A1598BA7D41D0E919BFB76C6513` | yes |
| aggregate `bom.json` | 190,831 | `3797F16C617B3207229EFD8A846BE2EC31FB9CD56BE681A2069447A859D5568E` | 190,831 | `3797F16C617B3207229EFD8A846BE2EC31FB9CD56BE681A2069447A859D5568E` | yes |

Two consecutive authoritative clean verifies each passed 553 tests / 103 suites / zero failures / zero errors / zero
skips and zero Checkstyle violations. The sealed first build and immediately following comparison build produced
byte-identical distribution and aggregate SBOM files; no timestamp, ordering, metadata or SBOM variation was normalized
away.

The first A76 setup correction added two tests over the accepted Phase 8F baseline. OR8F-A76-01 added two more in
existing suites for the real command-to-semantic boundary and final Paper catalog rendering. OR8F-A76-02 adds two
publisher tests in one new suite, producing 553 tests in 103 suites. The 89-test / six-suite focused gate and isolated
changed Paper harness build also pass.

The previous 16,497,928-byte distribution with SHA-256
`112A0534F6166D8842F470A6C898A452284362761BF5350578453AE198961111` remains historical blocker evidence. Owner Review 1's
16,504,823-byte correction candidate with SHA-256
`6C4CD6B9349CF7AB7EBD0E0330920C2DC34A024E27C81577CF6C93F05C8277E2` remains rejected OR8F-A76-01 evidence. The
16,505,971-byte Owner Review 2 artifact with SHA-256
`C5646F927789CFF95C8F8F8663ADB264C1498D8D8EA927F4C027369085A656D3` remains OR8F-A76-02 blocker evidence. None is
the corrected Owner Review 3 candidate and none was silently substituted. The aggregate SBOM remains byte-identical
because this correction adds no dependency.

## Supporting evidence

- `docs/evidence/phase8f/PHASE8F_A76_CORRECTION_OR3_FOCUSED_VERIFY.log`
- `docs/evidence/phase8f/PHASE8F_A76_CORRECTION_OR3_CLEAN_VERIFY_1.log`
- `docs/evidence/phase8f/PHASE8F_A76_CORRECTION_OR3_CLEAN_VERIFY_2.log`
- `docs/evidence/phase8f/PHASE8F_A76_CORRECTION_OR3_ARTIFACT_COMPARE.log`
- `docs/evidence/phase8f/PHASE8F_A76_CORRECTION_OR3_ISOLATED_PAPER_HARNESS.log`
- `docs/evidence/phase8f/PHASE8F_A76_CORRECTION_OR3_PAPER_FIRST_BOOT_SANITIZED.log`
- `docs/evidence/phase8f/PHASE8F_A76_CORRECTION_OR3_PAPER_RESTART_SANITIZED.log`
- historical Phase 8F and Owner Review 1/2 correction logs remain alongside these files and are not rewritten as corrected evidence.

## Final acceptance reconciliation verification

After recording targeted Owner Review 3 and the real-player owner-operated A76 PASS, one authoritative clean verify
reruns the complete 553-test / 103-suite gate with zero failures, errors or skips and zero Checkstyle violations. The
generated distribution remains 16,507,914 bytes with SHA-256
`0D961DB73D6C44CF18986CA42B7950914CEA5A1598BA7D41D0E919BFB76C6513`; the aggregate SBOM remains 190,831 bytes with
SHA-256 `3797F16C617B3207229EFD8A846BE2EC31FB9CD56BE681A2069447A859D5568E`. The reconciliation changes only
documentation/evidence and does not create a new runtime candidate.

- `docs/evidence/phase8f/PHASE8F_A76_FINAL_ACCEPTANCE_RECONCILIATION_VERIFY.log`
