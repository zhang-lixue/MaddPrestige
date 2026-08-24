[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $PerformanceFirstBoot,
    [Parameter(Mandatory = $true)]
    [string] $PerformanceRestart,
    [Parameter(Mandatory = $true)]
    [string] $FaultMatrix,
    [Parameter(Mandatory = $true)]
    [string] $OptionalAbsence,
    [Parameter(Mandatory = $true)]
    [string] $SetupAudit,
    [Parameter(Mandatory = $true)]
    [string] $OutputDirectory
)

$ErrorActionPreference = 'Stop'

function Read-QualificationLog {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Path
    )

    $resolved = (Resolve-Path -LiteralPath $Path).Path
    if ($resolved.EndsWith('.gz', [System.StringComparison]::OrdinalIgnoreCase)) {
        $file = [System.IO.File]::OpenRead($resolved)
        $gzip = [System.IO.Compression.GzipStream]::new(
            $file,
            [System.IO.Compression.CompressionMode]::Decompress)
        $reader = [System.IO.StreamReader]::new($gzip)
        try {
            $lines = [System.Collections.Generic.List[string]]::new()
            while (($line = $reader.ReadLine()) -ne $null) {
                $lines.Add($line)
            }
            return $lines
        }
        finally {
            $reader.Dispose()
        }
    }

    return [System.IO.File]::ReadAllLines($resolved)
}

function Write-SanitizedQualificationLog {
    param(
        [Parameter(Mandatory = $true)]
        [string] $InputPath,
        [Parameter(Mandatory = $true)]
        [string] $OutputPath
    )

    $retained = [System.Collections.Generic.List[string]]::new()
    foreach ($rawLine in (Read-QualificationLog -Path $InputPath)) {
        if ($rawLine -notmatch '(\[bootstrap\]|\[PluginInitializerManager\]|^ - |\[Phase8E-|\[MaddPrestige\]|Preparing level|Done preparing level|Done \(|Stopping server|Saving chunks for level|Manual progress persistence|PersistenceException|SqliteManualProgressRepository|ManualProgressProvider|org\.sqlite\.SQLiteException|controlled POST|Phase8EFaultQualification)') {
            continue
        }

        $line = $rawLine
        $line = $line -replace '(?i)\b[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\b', '<UUID>'
        $line = $line -replace '\br_[0-9a-f]{32}\b', '<REVISION>'
        $line = $line -replace '(?i)C:\\Users\\[^\\]+\\Documents\\MC Development\\MaddPrestige-Phase8', '<WORKSPACE>'
        $retained.Add($line)
    }

    $parent = Split-Path -Parent $OutputPath
    [System.IO.Directory]::CreateDirectory($parent) | Out-Null
    [System.IO.File]::WriteAllLines(
        $OutputPath,
        $retained,
        [System.Text.UTF8Encoding]::new($false))
}

$outputs = @(
    @{ Input = $PerformanceFirstBoot; Name = 'A62_PERFORMANCE_FIRST_BOOT_SANITIZED.log' },
    @{ Input = $PerformanceRestart; Name = 'A62_PERFORMANCE_RESTART_SANITIZED.log' },
    @{ Input = $FaultMatrix; Name = 'A65_A66_A67_OR8E05_FAULT_MATRIX_SANITIZED.log' },
    @{ Input = $OptionalAbsence; Name = 'A67_OPTIONAL_ABSENCE_SANITIZED.log' },
    @{ Input = $SetupAudit; Name = 'OR8E04_SETUP_PROVIDER_MAPPING_SANITIZED.log' }
)

foreach ($output in $outputs) {
    Write-SanitizedQualificationLog `
        -InputPath $output.Input `
        -OutputPath (Join-Path $OutputDirectory $output.Name)
}
