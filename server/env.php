<?php
declare(strict_types=1);

/**
 * Load server/.env into the process environment.
 * Existing env vars (e.g. Railway injected PG*) are never overwritten.
 */
function loadServerEnv(?string $envPath = null): void
{
    static $loaded = false;
    if ($loaded) {
        return;
    }
    $loaded = true;

    $path = $envPath ?? (__DIR__ . '/.env');
    if (!is_file($path) || !is_readable($path)) {
        return;
    }

    $lines = file($path, FILE_IGNORE_NEW_LINES);
    if ($lines === false) {
        return;
    }

    foreach ($lines as $line) {
        $line = trim($line);
        if ($line === '' || str_starts_with($line, '#')) {
            continue;
        }
        if (!str_contains($line, '=')) {
            continue;
        }

        [$name, $value] = explode('=', $line, 2);
        $name = trim($name);
        $value = trim($value);
        if ($name === '') {
            continue;
        }

        if (
            (str_starts_with($value, '"') && str_ends_with($value, '"'))
            || (str_starts_with($value, "'") && str_ends_with($value, "'"))
        ) {
            $value = substr($value, 1, -1);
        }

        $existing = getenv($name);
        if ($existing !== false && $existing !== '') {
            continue;
        }

        putenv($name . '=' . $value);
        $_ENV[$name] = $value;
        $_SERVER[$name] = $value;
    }
}

/**
 * Parse postgres:// or postgresql:// DATABASE_URL into connection parts.
 *
 * @return array{host:string,port:string,dbname:string,user:string,pass:string,sslmode:string}|null
 */
function parseDatabaseUrl(string $url): ?array
{
    $parts = parse_url($url);
    if ($parts === false || empty($parts['host'])) {
        return null;
    }

    $dbName = isset($parts['path']) ? ltrim($parts['path'], '/') : '';
    if ($dbName === '') {
        return null;
    }

    $query = [];
    if (!empty($parts['query'])) {
        parse_str($parts['query'], $query);
    }

    return [
        'host' => (string)$parts['host'],
        'port' => (string)($parts['port'] ?? '5432'),
        'dbname' => $dbName,
        'user' => (string)($parts['user'] ?? 'postgres'),
        'pass' => (string)($parts['pass'] ?? ''),
        'sslmode' => (string)($query['sslmode'] ?? 'require'),
    ];
}
