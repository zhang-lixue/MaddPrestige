param(
    [string]$RepositoryRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'
Push-Location $RepositoryRoot
try {
    $branch = git branch --show-current
    $head = git rev-parse HEAD
    $tracked = @(git diff --name-status | Sort-Object)
    $untracked = @(git ls-files --others --exclude-standard | Sort-Object)
    $staged = @(git diff --cached --name-only)
    $lines = [Collections.Generic.List[string]]::new()
    $lines.Add('# Phase 8B exact changed/new file manifest')
    $lines.Add('')
    $lines.Add("- Branch: ``$branch``")
    $lines.Add("- Baseline/current HEAD: ``$head``")
    $lines.Add("- Tracked changed paths: **$($tracked.Count)**")
    $lines.Add("- Untracked paths: **$($untracked.Count)**")
    $lines.Add("- Total changed/new paths: **$($tracked.Count + $untracked.Count)**")
    $lines.Add("- Staged paths: **$($staged.Count)**")
    $lines.Add('')
    $lines.Add('## Tracked changes')
    $lines.Add('')
    $lines.Add('```text')
    foreach ($entry in $tracked) { $lines.Add($entry) }
    $lines.Add('```')
    $lines.Add('')
    $lines.Add('## Untracked additions')
    $lines.Add('')
    $lines.Add('```text')
    foreach ($entry in $untracked) { $lines.Add($entry) }
    $lines.Add('```')
    $lines.Add('')
    $lines.Add('Generated mechanically by `tools/Generate-Phase8BManifest.ps1`; run after all review evidence is created.')
    [IO.File]::WriteAllLines((Join-Path $RepositoryRoot 'docs/V2_PHASE8B_FILE_MANIFEST.md'), $lines,
            [Text.UTF8Encoding]::new($false))
} finally {
    Pop-Location
}
