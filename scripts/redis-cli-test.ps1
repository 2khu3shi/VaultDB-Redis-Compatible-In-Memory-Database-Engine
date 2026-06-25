# VaultDB redis-cli compatibility smoke test
# Usage: ./scripts/redis-cli-test.ps1 [port]

param([int]$Port = 6379)

$redisCli = Get-Command redis-cli -ErrorAction SilentlyContinue
if (-not $redisCli) {
    Write-Error "redis-cli not found. Install Redis CLI tools first."
}

function Assert-Resp($actual, $expected, $label) {
    if ($actual -ne $expected) {
        throw "$label failed: expected '$expected', got '$actual'"
    }
    Write-Host "[PASS] $label"
}

$cli = { param($cmd) & redis-cli -p $Port $cmd.Split(' ') }

Assert-Resp (& redis-cli -p $Port ping) "PONG" "PING"
Assert-Resp (& redis-cli -p $Port set name vault) "OK" "SET"
Assert-Resp (& redis-cli -p $Port get name) "vault" "GET"
Assert-Resp (& redis-cli -p $Port incr counter) "1" "INCR"
Assert-Resp (& redis-cli -p $Port lpush items a b c) "3" "LPUSH"
Assert-Resp (& redis-cli -p $Port lrange items 0 -1) "c`nb`na" "LRANGE"
Assert-Resp (& redis-cli -p $Port hset user name ankit) "1" "HSET"
Assert-Resp (& redis-cli -p $Port hget user name) "ankit" "HGET"
Assert-Resp (& redis-cli -p $Port sadd tags java redis) "2" "SADD"
Assert-Resp (& redis-cli -p $Port sismember tags java) "1" "SISMEMBER"
Assert-Resp (& redis-cli -p $Port expire name 60) "1" "EXPIRE"
Assert-Resp (& redis-cli -p $Port type name) "string" "TYPE"
Assert-Resp (& redis-cli -p $Port del name counter items user tags) "5" "DEL"

Write-Host "`nAll redis-cli compatibility checks passed."
