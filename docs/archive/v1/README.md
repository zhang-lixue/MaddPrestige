# Archived V1 documentation

These documents describe the retired 1.x product and are retained for historical and rollback reference only:

- [Developer API](API.md)
- [Compatibility](COMPATIBILITY.md)
- [Installation](INSTALLATION.md)
- [Plugin relationships](RELATIONSHIPS.md)
- [Tebex setup](TEBEX.md)

They are not installation, configuration, compatibility, or API instructions for 2.0. The frozen V1 binaries,
configuration, database, and characterization evidence remain under [`baseline/v1`](../../../baseline/v1/README.md).

## Retired distribution artifacts

The V1.0.0 and V1.1.0 distribution JARs were retired from the current working tree after V2 release qualification
because no build or test consumes them. Their release provenance remains available in Git history and the
`v1.2.0-baseline` tag:

- `MaddPrestige-1.0.0.jar` — SHA-256
  `747A01F109A6DABE03C91F14583D900ACD33E46DFA842FAEDFDA9BC237121FCF`;
- `MaddPrestige-1.1.0.jar` — SHA-256
  `37CECC3F18EFB9E209C2F039BBAAC7016499F91B4DE5596A5E9AF99D1DA60981`.

The V1.2.0 distribution JAR remains tracked because it is an input to the executable V1 characterization suite.
