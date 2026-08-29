[CmdletBinding()]
param(
    [switch] $Check
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$manifestRelativePath = 'docs/V2_PHASE8F_FILE_MANIFEST.md'
$manifestPath = Join-Path $repositoryRoot $manifestRelativePath
$placeholderPattern = '(?i)\$(?:base|head|branch)\b|\$\{[A-Za-z_][A-Za-z0-9_.-]*\}|%[A-Za-z_][A-Za-z0-9_]*%|\b(?:TBD|TO_BE_FILLED|PLACEHOLDER_VALUE)\b|<PLACEHOLDER>'

function Invoke-Git {
    param([Parameter(Mandatory)][string[]] $Arguments)

    $output = & git @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "git $($Arguments -join ' ') failed with exit code $LASTEXITCODE"
    }
    return @($output)
}

function Assert-PlaceholderGuard {
    $unresolvedSamples = @('$base', '$head', '$branch', '${GIT_SHA}', '%GIT_SHA%', 'TBD',
        'TO_BE_FILLED', 'PLACEHOLDER_VALUE', '<PLACEHOLDER>')
    foreach ($sample in $unresolvedSamples) {
        if ($sample -notmatch $placeholderPattern) {
            throw "Placeholder guard does not reject representative sample: $sample"
        }
    }
    if ('993599dc47cacc76b6372302d338d1850bf2896e' -match $placeholderPattern) {
        throw 'Placeholder guard rejects a resolved Git SHA.'
    }
}

function Get-ReviewPaths {
    param([Parameter(Mandatory)][string] $Baseline)

    $tracked = Invoke-Git -Arguments @('diff', '--name-only', $Baseline, '--')
    $untracked = Invoke-Git -Arguments @('ls-files', '--others', '--exclude-standard')
    return @($tracked + $untracked + $manifestRelativePath |
        Where-Object { $_ -and $_.Trim() } |
        ForEach-Object { $_.Replace('\', '/') } |
        Sort-Object -Unique)
}

function Assert-SafeReviewPaths {
    param([Parameter(Mandatory)][string[]] $Paths)

    $forbidden = $Paths | Where-Object {
        $_ -match '(?i)(^|/)(target|world|world_nether|world_the_end|cache|caches)(/|$)' -or
        $_ -match '(?i)\.(jar|zip|db|sqlite|sqlite3|jfr)$' -or
        ($_ -match '(?i)\.log$' -and $_ -notmatch '^docs/evidence/phase8f/')
    }
    if ($forbidden) {
        throw "Review manifest contains forbidden build/runtime paths: $($forbidden -join ', ')"
    }
}

function Assert-NoEvidencePlaceholders {
    param([Parameter(Mandatory)][string] $ManifestText)

    $evidenceFiles = @(Get-ChildItem -LiteralPath (Join-Path $repositoryRoot 'docs') -File |
        Where-Object { $_.Name -like 'V2_PHASE8F_*.md' -and $_.FullName -ne $manifestPath })
    $summary = Join-Path $repositoryRoot 'PHASE8F_OWNER_REVIEW_SUMMARY.txt'
    if (Test-Path -LiteralPath $summary) {
        $evidenceFiles += Get-Item -LiteralPath $summary
    }
    $evidenceDirectory = Join-Path $repositoryRoot 'docs/evidence/phase8f'
    if (Test-Path -LiteralPath $evidenceDirectory) {
        $evidenceFiles += Get-ChildItem -LiteralPath $evidenceDirectory -File
    }

    $failures = [System.Collections.Generic.List[string]]::new()
    foreach ($file in $evidenceFiles) {
        $match = [regex]::Match((Get-Content -LiteralPath $file.FullName -Raw), $placeholderPattern)
        if ($match.Success) {
            $failures.Add("$($file.FullName): $($match.Value)")
        }
    }
    $manifestMatch = [regex]::Match($ManifestText, $placeholderPattern)
    if ($manifestMatch.Success) {
        $failures.Add("${manifestPath}: $($manifestMatch.Value)")
    }
    if ($failures.Count -ne 0) {
        throw "Unresolved Phase 8F evidence placeholders:`n$($failures -join [Environment]::NewLine)"
    }
}

Push-Location $repositoryRoot
try {
    Assert-PlaceholderGuard
    $existing = $null
    if ($Check) {
        if (-not (Test-Path -LiteralPath $manifestPath)) {
            throw "Manifest is missing: $manifestPath"
        }
        $existing = (Get-Content -LiteralPath $manifestPath -Raw).Replace("`r`n", "`n")
        $baselineMatch = [regex]::Match($existing, '(?m)^Baseline: `([0-9a-f]{40})`<br>$')
        if (-not $baselineMatch.Success) {
            throw 'Manifest does not contain one exact recorded baseline SHA.'
        }
        $baseline = $baselineMatch.Groups[1].Value
        & git merge-base --is-ancestor $baseline HEAD
        if ($LASTEXITCODE -ne 0) {
            throw "Recorded baseline is not an ancestor of HEAD: $baseline"
        }
    }
    else {
        $baseline = (Invoke-Git -Arguments @('rev-parse', 'HEAD') | Select-Object -First 1).Trim()
    }
    if ($baseline -notmatch '^[0-9a-f]{40}$') {
        throw "Baseline is not an exact Git SHA: $baseline"
    }
    $branch = (Invoke-Git -Arguments @('branch', '--show-current') | Select-Object -First 1).Trim()
    $paths = @(Get-ReviewPaths -Baseline $baseline)
    Assert-SafeReviewPaths -Paths $paths

    $lines = [System.Collections.Generic.List[string]]::new()
    $lines.Add('# Phase 8F file manifest')
    $lines.Add('')
    $lines.Add("Date: 2026-08-28<br>")
    $lines.Add("Baseline: ``$baseline``<br>")
    $lines.Add("Branch: ``$branch``<br>")
    $lines.Add("Review paths: $($paths.Count)")
    $lines.Add('')
    $lines.Add('## Exact review paths')
    $lines.Add('')
    foreach ($path in $paths) {
        $lines.Add("- ``$path``")
    }
    $lines.Add('')
    $lines.Add('## Excluded build and runtime material')
    $lines.Add('')
    $lines.Add('The review set excludes Maven targets and caches, distribution/SBOM/review archives, third-party JARs,')
    $lines.Add('Paper libraries, worlds, runtime databases, logs, JFR data, credentials, secrets and disposable server state.')
    $lines.Add('The A76 setup-correction owner-review ZIP carries the distribution and aggregate SBOM only as sealed')
    $lines.Add('candidate artifacts in a dedicated artifact directory; neither is a Git review path.')
    $manifestText = ($lines -join "`n") + "`n"
    Assert-NoEvidencePlaceholders -ManifestText $manifestText

    if ($Check) {
        if ($existing -ne $manifestText) {
            throw 'Phase 8F file manifest is stale. Run tools/Generate-Phase8FFileManifest.ps1.'
        }
        Write-Output "PASS: resolved baseline $baseline; $($paths.Count) exact review paths; no evidence placeholders."
    }
    else {
        [System.IO.File]::WriteAllText($manifestPath, $manifestText,
            [System.Text.UTF8Encoding]::new($false))
        Write-Output "Wrote $manifestRelativePath with resolved baseline $baseline and $($paths.Count) paths."
    }
}
finally {
    Pop-Location
}
