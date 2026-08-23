# Minimal external metric provider

This example compiles against only the MaddPrestige Stable API JAR and Paper API. It deliberately does not import
MaddPrestige core, persistence, platform, or recovery internals.

The plugin registers a `ProviderDeclaration` through Paper's `ServicesManager`. MaddPrestige attests the owning plugin,
derives the provider namespace, validates the metadata, and returns an opaque generation-specific registration handle.
The resulting provider ID is `example_progress_provider:visits`; its metric is `visits`.

The callback is thread-safe and non-blocking. It observes the deadline/cancellation signal and returns a structured
unavailable result while shutting down. `onDisable` unregisters the Paper service and invalidates the exact handle; both
operations are safe to repeat. A real provider would replace the constant value with a bounded read of its own
authoritative storage.

Install the public MaddPrestige API into the normal local Maven repository, then build the isolated consumer:

```text
.\mvnw.cmd --no-transfer-progress -pl maddprestige-api -am -DskipTests install
.\mvnw.cmd --no-transfer-progress -f examples/provider-sdk/pom.xml clean package
```

Copy `examples/provider-sdk/target/ExampleProgressProvider.jar` only to a disposable development server. This example
is not part of the Member/Adventurer/Veteran profile and does not grant manual-progress or recovery authority.
