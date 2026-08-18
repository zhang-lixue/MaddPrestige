param(
    [string]$RepositoryRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'
$sourceRoot = Join-Path $RepositoryRoot 'maddprestige-api/src/main/java'
$classes = Join-Path $RepositoryRoot 'maddprestige-api/target/classes'
$output = Join-Path $RepositoryRoot 'docs/V2_PHASE8B_API_INVENTORY.md'
$paperSourceRoot = Join-Path $RepositoryRoot 'maddprestige-platform-paper/src/main/java'
$paperClasses = Join-Path $RepositoryRoot 'maddprestige-platform-paper/target/classes'
$paperOutput = Join-Path $RepositoryRoot 'docs/V2_PHASE8B_PAPER_API_INVENTORY.md'

if (-not (Test-Path $classes) -or -not (Test-Path $paperClasses)) {
    throw 'Compile maddprestige-api and maddprestige-platform-paper before generating the inventories.'
}

$types = foreach ($file in Get-ChildItem $sourceRoot -Recurse -Filter '*.java' |
        Where-Object Name -ne 'package-info.java') {
    $source = Get-Content -LiteralPath $file.FullName -Raw
    $package = [regex]::Match($source, '(?m)^package\s+([^;]+);').Groups[1].Value
    $name = [IO.Path]::GetFileNameWithoutExtension($file.Name)
    $qualified = "$package.$name"
    $stable = $source -match '@Stable' -or $package -in @(
        'net.maddkraft.maddprestige.api.service',
        'net.maddkraft.maddprestige.api.event'
    ) -or $qualified -in @(
        'net.maddkraft.maddprestige.api.annotation.Stable',
        'net.maddkraft.maddprestige.api.annotation.Experimental'
    )
    $experimental = -not $stable -and $source -match '@Experimental'
    $internal = -not $stable -and -not $experimental -and (
        $package -in @(
            'net.maddkraft.maddprestige.api.audit',
            'net.maddkraft.maddprestige.api.operation'
        ) -or $name -in @(
            'NativeRecoverableCostProvider', 'PlannedCost', 'NativeRecoverableRewardProvider', 'PlannedReward',
            'ConfigRevisionId', 'FieldId', 'IdentifierRules', 'OperationId', 'ActivationState',
            'ProviderHealth', 'ProviderLifecycle'
        )
    )
    $classification = if ($stable) {
        'STABLE CANDIDATE'
    } elseif ($experimental) {
        'EXPERIMENTAL'
    } elseif ($internal) {
        'INTERNAL/SHOULD NOT BE PUBLIC'
    } else {
        'LEGACY/PENDING REMOVAL'
    }
    [pscustomobject]@{
        Qualified = $qualified
        Name = $name
        Package = $package
        Classification = $classification
    }
}
$types = @($types | Sort-Object Qualified)

function Stable-Purpose($type) {
    if ($type.Package.EndsWith('.annotation')) { return 'Stability classification marker.' }
    if ($type.Package.EndsWith('.service')) { return 'Public service discovery, operation receipt, or immutable read DTO.' }
    if ($type.Package.EndsWith('.event')) { return 'Immutable, Bukkit-free lifecycle event payload.' }
    if ($type.Package.EndsWith('.id')) { return 'Validated stable identity/correlation value.' }
    if ($type.Package.EndsWith('.metric')) { return 'Typed requirement-metric value or semantic enum.' }
    return 'Owner-attested provider SDK callback, metadata, request/result, or lifecycle capability.'
}

function Stable-ThreadModel($type) {
    if ($type.Package.EndsWith('.service')) {
        if ($type.Name -eq 'MaddPrestigeService') {
            return 'Potential-I/O reads/mutations are asynchronous; stages()/providers() are synchronous cached-only.'
        }
        return 'Immutable/thread-neutral once constructed.'
    }
    if ($type.Package.EndsWith('.provider')) {
        if ($type.Name -in @('ProviderDeclaration', 'RequirementProvider')) {
            return 'Invoked off registry locks on the bounded provider callback executor; deadline supplied in context.'
        }
        return 'Immutable/thread-neutral; handle unregister is asynchronous and exact-registration scoped.'
    }
    return 'Immutable/thread-neutral, or compile/runtime metadata only.'
}

function Stable-Bounds($type) {
    if ($type.Package.EndsWith('.annotation')) { return 'Finite annotation target/retention only.' }
    if ($type.Package.EndsWith('.id')) { return 'Validated conservative identifier syntax; ProviderId permits bounded owner:local form.' }
    if ($type.Package.EndsWith('.metric')) { return 'Canonical values are bounded to 4096 characters; numeric/type parsing is fail-closed.' }
    if ($type.Package.EndsWith('.provider')) { return 'Metadata <=256 metrics; read batches <=256; IDs/text/maps/filter dimensions have explicit limits.' }
    if ($type.Package.EndsWith('.event')) { return 'Immutable lists/text are explicitly bounded; no mutable implementation references.' }
    return 'DTO strings/maps/lists are copied and bounded; Optional represents absence.'
}

function Stable-Risk($type) {
    if ($type.Name -eq 'MaddPrestigeService' -or $type.Name.EndsWith('Provider') -or
            $type.Name.EndsWith('Declaration') -or $type.Name.EndsWith('Handle')) {
        return 'HIGH: adding abstract interface methods is binary/source breaking; default methods or a major version are required.'
    }
    if ($type.Name -match '(Status|Kind|Mode|Policy|Type|Operator|Monotonicity)$') {
        return 'MEDIUM: added enum constants affect exhaustive consumer switches.'
    }
    if ($type.Name.EndsWith('Snapshot') -or $type.Name.EndsWith('Result') -or
            $type.Name.EndsWith('Request') -or $type.Name.EndsWith('Definition') -or
            $type.Name.EndsWith('View') -or $type.Name.EndsWith('Metadata')) {
        return 'HIGH: record component changes are constructor and binary incompatible.'
    }
    return 'LOW/MEDIUM: validation tightening and semantic changes still require compatibility review.'
}

$counts = $types | Group-Object Classification | Sort-Object Name
$lines = [Collections.Generic.List[string]]::new()
$lines.Add('# Phase 8B Bukkit-free SDK public API inventory and classification')
$lines.Add('')
$lines.Add('Generated mechanically from the compiled `maddprestige-api` classes on 2026-08-17 by `tools/Generate-Phase8BApiInventory.ps1`. This is surface A: the Bukkit-free core/service/provider SDK. Every public member shown by `javap -public` inherits its enclosing type classification. Compiler-standard record/enum members are included, so this is the exact review surface rather than a source-file summary.')
$lines.Add('')
$lines.Add('- Phase 8A public top-level type count: **76**.')
$lines.Add("- Phase 8B public top-level type count: **$($types.Count)**.")
foreach ($count in $counts) {
    $lines.Add("- $($count.Name): **$($count.Count)** type(s).")
}
$lines.Add('- No compatibility baseline is established by this document; owner acceptance is required before a baseline is recorded.')
$lines.Add('')
$lines.Add('## Type classification')
$lines.Add('')
$lines.Add('| Public type | Classification |')
$lines.Add('|---|---|')
foreach ($type in $types) {
    $lines.Add("| ``$($type.Qualified)`` | $($type.Classification) |")
}
$lines.Add('')
$lines.Add('## Stable-candidate review details')
$lines.Add('')
$lines.Add('Nullability for every stable candidate is non-null by default at runtime; `Optional` represents absence and public constructors/callback adapters reject null. Normative service/event/provider/id/metric package and type/member Javadocs define bounds, mutability, threading/blocking, ownership/lifetime and structured failure semantics. Provider callbacks must return non-null stages/maps/results. Java nullness type-use annotations remain an owner-review decision before the final baseline.')
$lines.Add('')
$lines.Add('| Stable candidate | Purpose | Javadoc | Thread model | Nullability | Bounds | Compatibility risk |')
$lines.Add('|---|---|---|---|---|---|---|')
foreach ($type in $types | Where-Object Classification -eq 'STABLE CANDIDATE') {
    $javadoc = 'Normative source contract present; owner wording review remains.'
    $lines.Add("| ``$($type.Qualified)`` | $(Stable-Purpose $type) | $javadoc | $(Stable-ThreadModel $type) | Non-null unless ``Optional``; fail-fast validation. | $(Stable-Bounds $type) | $(Stable-Risk $type) |")
}
$lines.Add('')
$lines.Add('## Exact public members by type')
$lines.Add('')
foreach ($type in $types) {
    $lines.Add("### ``$($type.Qualified)`` - $($type.Classification)")
    $lines.Add('')
    $lines.Add('```text')
    $signature = @(& javap -classpath $classes -public $type.Qualified 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "javap failed for $($type.Qualified): $signature"
    }
    foreach ($line in $signature) {
        $lines.Add($line.ToString())
    }
    $lines.Add('```')
    $lines.Add('')
}

[IO.File]::WriteAllLines($output, $lines, [Text.UTF8Encoding]::new($false))
Write-Output $output

$paperTypes = foreach ($file in Get-ChildItem $paperSourceRoot -Recurse -Filter '*.java') {
    $source = Get-Content -LiteralPath $file.FullName -Raw
    if ($source -notmatch '@Stable' -or $source -notmatch '(?m)^public\s+(?:final\s+)?class\s+') {
        continue
    }
    $package = [regex]::Match($source, '(?m)^package\s+([^;]+);').Groups[1].Value
    $name = [IO.Path]::GetFileNameWithoutExtension($file.Name)
    $classRelative = $package.Replace('.', '/') + "/$name.class"
    [pscustomobject]@{
        Qualified = "$package.$name"
        Name = $name
        ClassFile = Join-Path $paperClasses $classRelative
    }
}
$paperTypes = @($paperTypes | Sort-Object Qualified)

$paperLines = [Collections.Generic.List[string]]::new()
$paperLines.Add('# Phase 8B Paper-specific Stable event API inventory')
$paperLines.Add('')
$paperLines.Add('Generated mechanically from public `@Stable` classes in `maddprestige-platform-paper` and their compiled class files on 2026-08-17 by `tools/Generate-Phase8BApiInventory.ps1`. This is surface B: the Paper-specific event API. It is intentionally separate from the Bukkit-free SDK inventory.')
$paperLines.Add('')
$paperLines.Add("- Public Paper-specific Stable top-level types: **$($paperTypes.Count)**.")
$paperLines.Add('- Permitted platform exposure: `org.bukkit.event.Event`, `Cancellable`, and `HandlerList` only.')
$paperLines.Add('- Permitted MaddPrestige exposure: immutable Stable SDK event snapshots only.')
$paperLines.Add('- Persistence, journal/recovery, mutable core implementation, provider registry generation/token, optional-vendor, and unrelated platform internals are forbidden by `StablePaperEventSurfaceTest`.')
$paperLines.Add('- Both the Bukkit-free SDK and this Paper event surface are candidates for the future compatibility baseline; no baseline is frozen in Phase 8B.')
$paperLines.Add('')
$paperLines.Add('## Stable Paper event types')
$paperLines.Add('')
$paperLines.Add('| Public type | Purpose | Delivery/ownership contract | Compatibility risk |')
$paperLines.Add('|---|---|---|---|')
foreach ($type in $paperTypes) {
    $purpose = if ($type.Name.StartsWith('Pre')) { 'Cancellable pre-operation gate.' }
        elseif ($type.Name.StartsWith('Post')) { 'Durable terminal operation notification.' }
        elseif ($type.Name -eq 'ConfigAppliedEvent') { 'Canonical configuration publication notification.' }
        else { 'Cached provider-health transition notification.' }
    $paperLines.Add("| ``$($type.Qualified)`` | $purpose | Synchronous Paper-server-thread event; dispatch owns the event object and immutable snapshot values remain safe after return. | HIGH: superclass, interfaces, constructors, or accessor signature changes are binary/source incompatible. |")
}
$paperLines.Add('')
$paperLines.Add('## Exact public members by type')
$paperLines.Add('')
foreach ($type in $paperTypes) {
    if (-not (Test-Path -LiteralPath $type.ClassFile)) {
        throw "Compiled Paper Stable class is missing: $($type.ClassFile)"
    }
    $paperLines.Add("### ``$($type.Qualified)`` - PAPER STABLE CANDIDATE")
    $paperLines.Add('')
    $paperLines.Add('```text')
    $signature = @(& javap -public $type.ClassFile 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "javap failed for $($type.Qualified): $signature"
    }
    foreach ($line in $signature) {
        $paperLines.Add($line.ToString())
    }
    $paperLines.Add('```')
    $paperLines.Add('')
}

[IO.File]::WriteAllLines($paperOutput, $paperLines, [Text.UTF8Encoding]::new($false))
Write-Output $paperOutput
