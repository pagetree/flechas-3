<?php
declare(strict_types=1);

require_once __DIR__ . '/env.php';
loadServerEnv();

/**
 * Railway: DATABASE_URL is enough.
 * Local: server/.env (DATABASE_URL) and optional server/db.local.php override.
 */
function isLocalRequest(): bool
{
    $host = strtolower((string)($_SERVER['HTTP_HOST'] ?? ''));
    $remote = (string)($_SERVER['REMOTE_ADDR'] ?? '');
    return str_contains($host, 'localhost')
        || str_contains($host, '127.0.0.1')
        || $remote === '127.0.0.1'
        || $remote === '::1';
}

function envValue(string $key, string $default = ''): string
{
    $value = getenv($key);
    if ($value === false || $value === '') {
        return $default;
    }
    return (string)$value;
}

$cfg = [
    'host' => 'localhost',
    'port' => '5432',
    'dbname' => 'railway',
    'user' => 'postgres',
    'pass' => '',
    'sslmode' => envValue('PGSSLMODE', 'require'),
];

$databaseUrl = envValue('DATABASE_URL');
if ($databaseUrl !== '') {
    $fromUrl = parseDatabaseUrl($databaseUrl);
    if ($fromUrl !== null) {
        $cfg = array_merge($cfg, $fromUrl);
    }
} else {
    // Fallback if someone uses split PG* vars instead of DATABASE_URL
    $cfg['host'] = envValue('PGHOST', $cfg['host']);
    $cfg['port'] = envValue('PGPORT', $cfg['port']);
    $cfg['dbname'] = envValue('PGDATABASE', $cfg['dbname']);
    $cfg['user'] = envValue('PGUSER', $cfg['user']);
    $cfg['pass'] = envValue('PGPASSWORD', $cfg['pass']);
}

if (isLocalRequest()) {
    $localCfgFile = __DIR__ . '/db.local.php';
    if (is_file($localCfgFile)) {
        $localCfg = require $localCfgFile;
        if (is_array($localCfg)) {
            $cfg = array_merge($cfg, $localCfg);
        }
    }
}

try {
    $dsn = sprintf(
        'pgsql:host=%s;port=%s;dbname=%s;sslmode=%s',
        $cfg['host'],
        $cfg['port'],
        $cfg['dbname'],
        $cfg['sslmode']
    );
    $pdo = new PDO($dsn, (string)$cfg['user'], (string)$cfg['pass'], [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
    ]);
} catch (PDOException $e) {
    header('Content-Type: application/json', true, 500);
    echo json_encode(['error' => 'Database connection failed: ' . $e->getMessage()]);
    exit;
}
