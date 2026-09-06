# Contributing

MaddPrestige uses Java 25 and the Maven wrapper. Before opening a pull request:

1. Keep changes narrowly scoped and preserve the stable API unless a breaking change is explicitly approved.
2. Add focused tests for behavior changes and retain fail-closed security and recovery semantics.
3. Run:

   ```powershell
   .\mvnw.cmd --no-transfer-progress clean verify
   git diff --check
   ```

4. Do not commit Paper runtime directories, logs, local databases, credentials, generated build output, or disposable
   qualification state.
5. Update public documentation and the changelog when user-visible behavior changes.

The repository retains a frozen V1 characterization boundary and release packaging checks. Do not alter protected V1
source/artifacts or distribution contents without explicit project approval and matching regression evidence.

No public security-reporting channel is declared in this repository yet. Avoid placing sensitive vulnerability
details in a public issue; contact the maintainer through an established private channel until a formal policy is
published.
