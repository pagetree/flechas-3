<?php
ini_set('display_errors', 1);
ini_set('display_startup_errors', 1);
error_reporting(E_ALL);

$method = $_SERVER['REQUEST_METHOD'];
$requestPath = parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH) ?: '/';
$action = $_GET['action'] ?? '';

// If Railway routes everything to this file, manually pass admin URLs through.
$normalizedPath = ltrim($requestPath, '/');
if (str_starts_with($normalizedPath, 'server/')) {
    $normalizedPath = substr($normalizedPath, 7);
}

// Serve privacy policy without requiring the database.
if ($normalizedPath === 'privacy-policy.html' || $normalizedPath === 'privacy-policy') {
    $privacyPath = __DIR__ . '/privacy-policy.html';
    if (is_file($privacyPath)) {
        header('Content-Type: text/html; charset=utf-8');
        header('Cache-Control: public, max-age=3600');
        readfile($privacyPath);
        exit;
    }
}

if (str_starts_with($normalizedPath, 'admin')) {
    $adminRelative = substr($normalizedPath, strlen('admin'));
    $adminRelative = ltrim($adminRelative, '/');
    $target = $adminRelative === '' ? 'index.php' : $adminRelative;

    // Prevent traversal and only allow files inside /server/admin.
    if (str_contains($target, '..')) {
        http_response_code(400);
        header('Content-Type: text/plain');
        echo 'Bad request';
        exit;
    }

    $targetPath = __DIR__ . '/admin/' . $target;
    if (!is_file($targetPath)) {
        http_response_code(404);
        header('Content-Type: text/plain');
        echo 'Not found';
        exit;
    }

    // Static assets first (no DB needed).
    $ext = strtolower(pathinfo($targetPath, PATHINFO_EXTENSION));
    $staticTypes = [
        'css' => 'text/css; charset=utf-8',
        'png' => 'image/png',
        'jpg' => 'image/jpeg',
        'jpeg' => 'image/jpeg',
        'webp' => 'image/webp',
        'svg' => 'image/svg+xml',
        'ico' => 'image/x-icon',
    ];
    if (isset($staticTypes[$ext])) {
        header('Content-Type: ' . $staticTypes[$ext]);
        header('Cache-Control: public, max-age=86400');
        readfile($targetPath);
        exit;
    }

    if ($ext !== 'php') {
        http_response_code(404);
        header('Content-Type: text/plain');
        echo 'Not found';
        exit;
    }

    require_once 'db.php';
    require $targetPath;
    exit;
}

require_once 'db.php';

if ($action !== 'download_schema_dump') {
    header('Content-Type: application/json');
}
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, Authorization');

// Handle preflight requests
if ($method === 'OPTIONS') {
    exit;
}

switch ($action) {
    case 'get_puzzles':
        handleGetPuzzles($pdo);
        break;
    case 'submit_stats':
        handleSubmitStats($pdo);
        break;
    case 'check_player_email':
        handleCheckPlayerEmail($pdo);
        break;
    case 'create_player':
        handleCreatePlayer($pdo);
        break;
    case 'get_progress':
        handleGetProgress($pdo);
        break;
    case 'save_progress':
        handleSaveProgress($pdo);
        break;
    case 'download_schema_dump':
        handleDownloadSchemaDump($pdo);
        break;
    default:
        echo json_encode([
            'status' => 'online', 
            'message' => 'Arrow Game API',
            'endpoints' => [
                'get_puzzles' => '?action=get_puzzles&count=10',
                'submit_stats' => '?action=submit_stats',
                'get_progress' => '?action=get_progress&device_id=DEVICE_ID',
                'save_progress' => '?action=save_progress',
                'download_schema_dump' => '?action=download_schema_dump'
            ]
        ]);
        break;
}

function ensureDeviceProgressColumns(PDO $pdo): void {
    $pdo->exec("ALTER TABLE devices ADD COLUMN IF NOT EXISTS max_puzzle_number INT NOT NULL DEFAULT 1");
    $pdo->exec("ALTER TABLE devices ADD COLUMN IF NOT EXISTS current_puzzle_number INT NOT NULL DEFAULT 1");
}

function quoteIdentifier(string $value): string {
    return '"' . str_replace('"', '""', $value) . '"';
}

function handleDownloadSchemaDump(PDO $pdo): void {
    try {
        $tablesStmt = $pdo->query("
            SELECT table_name
            FROM information_schema.tables
            WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
            ORDER BY table_name
        ");
        $tables = $tablesStmt->fetchAll(PDO::FETCH_COLUMN);

        $columnsStmt = $pdo->prepare("
            SELECT
                cols.column_name,
                pg_catalog.format_type(pg_attr.atttypid, pg_attr.atttypmod) AS formatted_type,
                cols.is_nullable,
                cols.column_default
            FROM information_schema.columns cols
            JOIN pg_catalog.pg_namespace pg_ns
                ON pg_ns.nspname = cols.table_schema
            JOIN pg_catalog.pg_class pg_cls
                ON pg_cls.relname = cols.table_name
                AND pg_cls.relnamespace = pg_ns.oid
            JOIN pg_catalog.pg_attribute pg_attr
                ON pg_attr.attrelid = pg_cls.oid
                AND pg_attr.attname = cols.column_name
                AND pg_attr.attnum > 0
                AND NOT pg_attr.attisdropped
            WHERE cols.table_schema = 'public' AND cols.table_name = ?
            ORDER BY cols.ordinal_position
        ");

        $pkStmt = $pdo->prepare("
            SELECT kcu.column_name
            FROM information_schema.table_constraints tc
            JOIN information_schema.key_column_usage kcu
                ON tc.constraint_name = kcu.constraint_name
                AND tc.table_schema = kcu.table_schema
            WHERE tc.table_schema = 'public'
                AND tc.table_name = ?
                AND tc.constraint_type = 'PRIMARY KEY'
            ORDER BY kcu.ordinal_position
        ");

        $lines = [];
        $lines[] = '-- Arrow Game schema-only dump';
        $lines[] = '-- Generated at: ' . gmdate('Y-m-d H:i:s') . ' UTC';
        $lines[] = '-- Contains table/column definitions only (no rows)';
        $lines[] = '';

        foreach ($tables as $tableName) {
            $columnsStmt->execute([$tableName]);
            $columns = $columnsStmt->fetchAll();

            $pkStmt->execute([$tableName]);
            $primaryKeyColumns = $pkStmt->fetchAll(PDO::FETCH_COLUMN);

            $lines[] = 'CREATE TABLE IF NOT EXISTS '
                . quoteIdentifier('public')
                . '.'
                . quoteIdentifier((string)$tableName)
                . ' (';

            $columnLines = [];
            foreach ($columns as $column) {
                $columnSql = '    '
                    . quoteIdentifier((string)$column['column_name'])
                    . ' '
                    . (string)$column['formatted_type'];

                if (($column['is_nullable'] ?? 'YES') === 'NO') {
                    $columnSql .= ' NOT NULL';
                }

                $defaultValue = $column['column_default'] ?? null;
                if ($defaultValue !== null) {
                    $columnSql .= ' DEFAULT ' . $defaultValue;
                }

                $columnLines[] = $columnSql;
            }

            if (!empty($primaryKeyColumns)) {
                $quotedPk = array_map(
                    static fn($col): string => quoteIdentifier((string)$col),
                    $primaryKeyColumns
                );
                $columnLines[] = '    PRIMARY KEY (' . implode(', ', $quotedPk) . ')';
            }

            $lines[] = implode(",\n", $columnLines);
            $lines[] = ');';
            $lines[] = '';
        }

        $filename = 'arrow_schema_' . gmdate('Ymd_His') . '.sql';
        header('Content-Type: application/sql; charset=utf-8');
        header('Content-Disposition: attachment; filename="' . $filename . '"');
        echo implode("\n", $lines);
    } catch (Exception $e) {
        http_response_code(500);
        header('Content-Type: application/json');
        echo json_encode(['error' => 'Failed to export schema', 'details' => $e->getMessage()]);
    }
}

function normalizeDeviceId(array $data, string $queryKey = 'device_id'): ?string {
    $deviceId = null;

    if (isset($data[$queryKey])) {
        $deviceId = trim((string)$data[$queryKey]);
    } elseif (isset($data['deviceId'])) {
        $deviceId = trim((string)$data['deviceId']);
    }

    if ($deviceId === '') {
        return null;
    }

    return $deviceId;
}

function handleGetPuzzles($pdo) {
    $count = isset($_GET['count']) ? (int)$_GET['count'] : 10;
    $count = min(max($count, 1), 50); // Limit between 1 and 50
    
    $seeds = [];
    for ($i = 0; $i < $count; $i++) {
        // Generate a random 32-bit signed integer
        $seed = mt_rand(-2147483648, 2147483647);
        $seeds[] = $seed;
        
        // Ensure seed exists in DB so we can track stats later
        // Note: In production, you might want to pre-populate this or do it asynchronously
        $stmt = $pdo->prepare("INSERT INTO puzzles (seed) VALUES (?) ON CONFLICT (seed) DO NOTHING");
        $stmt->execute([$seed]);
    }
    
    echo json_encode(['seeds' => $seeds]);
}

function handleSubmitStats($pdo) {
    $data = json_decode(file_get_contents('php://input'), true);
    $seed = isset($data['seed']) ? (int)$data['seed'] : null;
    $time = isset($data['time']) ? (float)$data['time'] : null;
    $deviceId = normalizeDeviceId($data);
    $playerEmail = resolvePlayerEmailForProgress($pdo, $data, $deviceId);

    if ($seed === null || $time === null) {
        http_response_code(400);
        echo json_encode(['error' => 'Invalid data. Required: seed (int), time (float)']);
        return;
    }

    try {
        // Start transaction
        $pdo->beginTransaction();

        // 1. Ensure the puzzle exists (if it wasn't generated by get_puzzles)
        $stmt = $pdo->prepare("INSERT INTO puzzles (seed) VALUES (?) ON CONFLICT (seed) DO NOTHING");
        $stmt->execute([$seed]);

        // 2. Update puzzle averages
        $stmt = $pdo->prepare("
            UPDATE puzzles 
            SET 
                avg_time_seconds = (avg_time_seconds * completions + ?) / (completions + 1),
                completions = completions + 1 
            WHERE seed = ?
        ");
        $stmt->execute([$time, $seed]);

        // 3. Log each play for raw analytics/history.
        $stmt = $pdo->prepare("INSERT INTO play_logs (seed, completion_time, device_id) VALUES (?, ?, ?)");
        $stmt->execute([$seed, $time, $deviceId]);

        // 4. Maintain per-device aggregated stats for fast reads.
        if ($deviceId !== null) {
            ensureDeviceProgressColumns($pdo);
            $stmt = $pdo->prepare("
                INSERT INTO devices (
                    device_id,
                    puzzles_played,
                    total_play_time_seconds,
                    max_puzzle_number,
                    current_puzzle_number,
                    last_seen_at,
                    updated_at
                )
                VALUES (?, 1, ?, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (device_id)
                DO UPDATE SET
                    puzzles_played = devices.puzzles_played + 1,
                    total_play_time_seconds = devices.total_play_time_seconds + EXCLUDED.total_play_time_seconds,
                    last_seen_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
            ");
            $stmt->execute([$deviceId, $time]);
        }

        if ($playerEmail !== null) {
            ensurePlayersTable($pdo);
            if ($deviceId !== null) {
                // Backfill/link device to player so future gameplay updates can resolve by device_id only.
                $linkStmt = $pdo->prepare("
                    UPDATE players
                    SET device_id = COALESCE(?, players.device_id),
                        updated_at = CURRENT_TIMESTAMP
                    WHERE LOWER(email) = LOWER(?)
                ");
                $linkStmt->execute([$deviceId, $playerEmail]);
            }
            upsertPlayerStatsByEmail($pdo, $playerEmail, $deviceId, $time);
        } elseif ($deviceId !== null) {
            ensurePlayersTable($pdo);
            $stmt = $pdo->prepare("
                UPDATE players
                SET
                    puzzles_played = players.puzzles_played + 1,
                    total_play_time_seconds = players.total_play_time_seconds + ?,
                    last_seen_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE device_id = ?
            ");
            $stmt->execute([$time, $deviceId]);
        }

        $pdo->commit();
        echo json_encode(['success' => true]);
    } catch (Exception $e) {
        if ($pdo->inTransaction()) {
            $pdo->rollBack();
        }
        http_response_code(500);
        echo json_encode(['error' => 'Failed to save stats', 'details' => $e->getMessage()]);
    }
}

function handleGetProgress(PDO $pdo): void {
    $deviceId = normalizeDeviceId($_GET);
    if ($deviceId === null) {
        http_response_code(400);
        echo json_encode(['error' => 'Invalid data. Required: device_id (string)']);
        return;
    }

    try {
        $playerEmail = resolvePlayerEmailForProgress($pdo, $_GET, $deviceId);

        if ($playerEmail !== null) {
            ensurePlayersTable($pdo);
            $stmt = $pdo->prepare("
                SELECT current_puzzle_number, max_puzzle_number
                FROM players
                WHERE email = ?
                LIMIT 1
            ");
            $stmt->execute([$playerEmail]);
            $row = $stmt->fetch();

            if ($row) {
                echo json_encode([
                    'device_id' => $deviceId,
                    'current_puzzle_number' => max(1, (int)$row['current_puzzle_number']),
                    'max_puzzle_number' => max(1, (int)$row['max_puzzle_number']),
                    'found' => true
                ]);
                return;
            }
        }

        ensureDeviceProgressColumns($pdo);
        $stmt = $pdo->prepare("
            SELECT current_puzzle_number, max_puzzle_number
            FROM devices
            WHERE device_id = ?
            LIMIT 1
        ");
        $stmt->execute([$deviceId]);
        $row = $stmt->fetch();

        if (!$row) {
            echo json_encode([
                'device_id' => $deviceId,
                'current_puzzle_number' => 1,
                'max_puzzle_number' => 1,
                'found' => false
            ]);
            return;
        }

        echo json_encode([
            'device_id' => $deviceId,
            'current_puzzle_number' => max(1, (int)$row['current_puzzle_number']),
            'max_puzzle_number' => max(1, (int)$row['max_puzzle_number']),
            'found' => true
        ]);
    } catch (Exception $e) {
        http_response_code(500);
        echo json_encode(['error' => 'Failed to load progress', 'details' => $e->getMessage()]);
    }
}

function handleSaveProgress(PDO $pdo): void {
    $data = json_decode(file_get_contents('php://input'), true) ?? [];
    $deviceId = normalizeDeviceId($data);
    $puzzleNumber = isset($data['puzzle_number']) ? (int)$data['puzzle_number'] : null;
    $playerEmail = resolvePlayerEmailForProgress($pdo, $data, $deviceId);

    if ($deviceId === null || $puzzleNumber === null || $puzzleNumber < 1) {
        http_response_code(400);
        echo json_encode(['error' => 'Invalid data. Required: device_id (string), puzzle_number (int >= 1)']);
        return;
    }

    try {
        ensureDeviceProgressColumns($pdo);
        $stmt = $pdo->prepare("
            INSERT INTO devices (
                device_id,
                puzzles_played,
                total_play_time_seconds,
                max_puzzle_number,
                current_puzzle_number,
                last_seen_at,
                updated_at
            )
            VALUES (?, 0, 0, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (device_id)
            DO UPDATE SET
                max_puzzle_number = GREATEST(devices.max_puzzle_number, EXCLUDED.max_puzzle_number),
                -- current_puzzle_number should always reflect the latest saved level
                current_puzzle_number = EXCLUDED.current_puzzle_number,
                last_seen_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP
        ");
        $stmt->execute([$deviceId, $puzzleNumber, $puzzleNumber]);

        if ($playerEmail !== null) {
            ensurePlayersTable($pdo);
            if ($deviceId !== null) {
                // Backfill/link device to player so future gameplay updates can resolve by device_id only.
                $linkStmt = $pdo->prepare("
                    UPDATE players
                    SET device_id = COALESCE(?, players.device_id),
                        updated_at = CURRENT_TIMESTAMP
                    WHERE LOWER(email) = LOWER(?)
                ");
                $linkStmt->execute([$deviceId, $playerEmail]);
            }
            upsertPlayerProgressByEmail($pdo, $playerEmail, $deviceId, $puzzleNumber);
        } elseif ($deviceId !== null) {
            ensurePlayersTable($pdo);
            $stmt = $pdo->prepare("
                UPDATE players
                SET
                    max_puzzle_number = GREATEST(players.max_puzzle_number, ?),
                    current_puzzle_number = ?,
                    last_seen_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE device_id = ?
            ");
            $stmt->execute([$puzzleNumber, $puzzleNumber, $deviceId]);
        }

        echo json_encode([
            'success' => true,
            'device_id' => $deviceId,
            'puzzle_number' => $puzzleNumber
        ]);
    } catch (Exception $e) {
        http_response_code(500);
        echo json_encode(['error' => 'Failed to save progress', 'details' => $e->getMessage()]);
    }
}

function ensurePlayersTable(PDO $pdo): void {
    // Players are separate from "devices" (progress tracking).
    // Signup is keyed by unique email.
    $pdo->exec("
        CREATE TABLE IF NOT EXISTS players (
            email TEXT PRIMARY KEY,
            player_name VARCHAR(12) NOT NULL,
            device_id TEXT,
            current_puzzle_number INTEGER NOT NULL DEFAULT 1,
            max_puzzle_number INTEGER NOT NULL DEFAULT 1,
            puzzles_played INTEGER NOT NULL DEFAULT 0,
            total_play_time_seconds DOUBLE PRECISION NOT NULL DEFAULT 0,
            last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
            created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
            updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
        )
    ");
    // Migrate older installations where players table existed with fewer columns.
    $pdo->exec("ALTER TABLE players ADD COLUMN IF NOT EXISTS device_id TEXT");
    $pdo->exec("ALTER TABLE players ADD COLUMN IF NOT EXISTS current_puzzle_number INTEGER NOT NULL DEFAULT 1");
    $pdo->exec("ALTER TABLE players ADD COLUMN IF NOT EXISTS max_puzzle_number INTEGER NOT NULL DEFAULT 1");
    $pdo->exec("ALTER TABLE players ADD COLUMN IF NOT EXISTS puzzles_played INTEGER NOT NULL DEFAULT 0");
    $pdo->exec("ALTER TABLE players ADD COLUMN IF NOT EXISTS total_play_time_seconds DOUBLE PRECISION NOT NULL DEFAULT 0");
    $pdo->exec("ALTER TABLE players ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now()");
    $pdo->exec("CREATE INDEX IF NOT EXISTS idx_players_device_id ON players(device_id)");
}

function ensureSignupRateLimitTable(PDO $pdo): void {
    $pdo->exec("
        CREATE TABLE IF NOT EXISTS signup_rate_limits (
            rl_key TEXT PRIMARY KEY,
            window_started_at TIMESTAMPTZ NOT NULL,
            attempt_count INTEGER NOT NULL DEFAULT 0,
            updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
        )
    ");
}

function getClientIp(): string {
    $forwarded = $_SERVER['HTTP_X_FORWARDED_FOR'] ?? '';
    if ($forwarded !== '') {
        $parts = explode(',', $forwarded);
        $candidate = trim($parts[0]);
        if ($candidate !== '') {
            return $candidate;
        }
    }
    return trim((string)($_SERVER['REMOTE_ADDR'] ?? 'unknown'));
}

function isEmailValidFormat(string $email): bool {
    if (strlen($email) > 254) {
        return false;
    }
    return filter_var($email, FILTER_VALIDATE_EMAIL) !== false;
}

function rejectIfHoneypotTripped(array $data): bool {
    // Expected to stay empty. Bots that autofill hidden fields will fail here.
    $honeypot = trim((string)($data['website'] ?? ''));
    if ($honeypot !== '') {
        http_response_code(400);
        echo json_encode(['error' => 'Invalid request']);
        return true;
    }
    return false;
}

function enforceSignupRateLimit(PDO $pdo, string $key, int $maxAttempts, int $windowSeconds): bool {
    ensureSignupRateLimitTable($pdo);
    $now = time();
    $windowStart = date('Y-m-d H:i:sP', $now);

    $stmt = $pdo->prepare("
        INSERT INTO signup_rate_limits (rl_key, window_started_at, attempt_count, updated_at)
        VALUES (?, ?, 1, CURRENT_TIMESTAMP)
        ON CONFLICT (rl_key)
        DO UPDATE SET
            attempt_count = CASE
                WHEN EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - signup_rate_limits.window_started_at)) > ?
                    THEN 1
                ELSE signup_rate_limits.attempt_count + 1
            END,
            window_started_at = CASE
                WHEN EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - signup_rate_limits.window_started_at)) > ?
                    THEN CURRENT_TIMESTAMP
                ELSE signup_rate_limits.window_started_at
            END,
            updated_at = CURRENT_TIMESTAMP
        RETURNING attempt_count
    ");
    $stmt->execute([$key, $windowStart, $windowSeconds, $windowSeconds]);
    $row = $stmt->fetch();
    $count = isset($row['attempt_count']) ? (int)$row['attempt_count'] : 0;

    if ($count > $maxAttempts) {
        http_response_code(429);
        echo json_encode(['error' => 'Too many attempts. Please try again later.']);
        return false;
    }

    return true;
}

function base64UrlDecode(string $input): string|false {
    $remainder = strlen($input) % 4;
    if ($remainder > 0) {
        $input .= str_repeat('=', 4 - $remainder);
    }
    $input = strtr($input, '-_', '+/');
    return base64_decode($input, true);
}

function verifyHs256Jwt(string $jwt, string $secret): ?array {
    $parts = explode('.', $jwt);
    if (count($parts) !== 3) {
        return null;
    }
    [$encodedHeader, $encodedPayload, $encodedSig] = $parts;

    $headerJson = base64UrlDecode($encodedHeader);
    $payloadJson = base64UrlDecode($encodedPayload);
    $sigBin = base64UrlDecode($encodedSig);
    if ($headerJson === false || $payloadJson === false || $sigBin === false) {
        return null;
    }

    $header = json_decode($headerJson, true);
    $payload = json_decode($payloadJson, true);
    if (!is_array($header) || !is_array($payload)) {
        return null;
    }
    if (($header['alg'] ?? '') !== 'HS256') {
        return null;
    }

    $expected = hash_hmac('sha256', $encodedHeader . '.' . $encodedPayload, $secret, true);
    if (!hash_equals($expected, $sigBin)) {
        return null;
    }

    if (isset($payload['exp']) && is_numeric($payload['exp']) && (int)$payload['exp'] < time()) {
        return null;
    }

    return $payload;
}

function requireSignupJwtIfConfigured(): bool {
    $secret = trim((string)(getenv('SIGNUP_JWT_SECRET') ?: ''));
    if ($secret === '') {
        // Backward-compatible: disabled unless configured.
        return true;
    }

    $auth = (string)($_SERVER['HTTP_AUTHORIZATION'] ?? '');
    if (!preg_match('/^Bearer\s+(.+)$/i', $auth, $matches)) {
        http_response_code(401);
        echo json_encode(['error' => 'Missing bearer token']);
        return false;
    }

    $payload = verifyHs256Jwt(trim($matches[1]), $secret);
    if ($payload === null) {
        http_response_code(401);
        echo json_encode(['error' => 'Invalid token']);
        return false;
    }

    return true;
}

function normalizeEmail(array $data): ?string {
    $email = null;
    if (isset($data['email'])) {
        $email = trim((string)$data['email']);
    } elseif (isset($data['player_email'])) {
        $email = trim((string)$data['player_email']);
    } elseif (isset($data['Email'])) {
        $email = trim((string)$data['Email']);
    }
    if ($email === '' || $email === null) return null;
    return strtolower($email);
}

function fallbackPlayerNameFromEmail(string $email): string {
    $localPart = explode('@', $email, 2)[0] ?? '';
    $sanitized = preg_replace('/[^A-Za-z0-9]/', '', $localPart);
    $sanitized = substr((string)$sanitized, 0, 12);
    if ($sanitized === '') {
        return 'Player';
    }
    return $sanitized;
}

function upsertPlayerStatsByEmail(PDO $pdo, string $email, ?string $deviceId, float $time): void {
    $fallbackName = fallbackPlayerNameFromEmail($email);
    $stmt = $pdo->prepare("
        INSERT INTO players (
            email,
            player_name,
            device_id,
            current_puzzle_number,
            max_puzzle_number,
            puzzles_played,
            total_play_time_seconds,
            last_seen_at,
            created_at,
            updated_at
        )
        VALUES (
            LOWER(?),
            ?,
            ?,
            1,
            1,
            1,
            ?,
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP
        )
        ON CONFLICT (email) DO UPDATE SET
            device_id = COALESCE(EXCLUDED.device_id, players.device_id),
            puzzles_played = players.puzzles_played + 1,
            total_play_time_seconds = players.total_play_time_seconds + EXCLUDED.total_play_time_seconds,
            last_seen_at = CURRENT_TIMESTAMP,
            updated_at = CURRENT_TIMESTAMP
    ");
    $stmt->execute([$email, $fallbackName, $deviceId, $time]);
}

function upsertPlayerProgressByEmail(PDO $pdo, string $email, ?string $deviceId, int $puzzleNumber): void {
    $fallbackName = fallbackPlayerNameFromEmail($email);
    $stmt = $pdo->prepare("
        INSERT INTO players (
            email,
            player_name,
            device_id,
            current_puzzle_number,
            max_puzzle_number,
            puzzles_played,
            total_play_time_seconds,
            last_seen_at,
            created_at,
            updated_at
        )
        VALUES (
            LOWER(?),
            ?,
            ?,
            ?,
            ?,
            0,
            0,
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP
        )
        ON CONFLICT (email) DO UPDATE SET
            device_id = COALESCE(EXCLUDED.device_id, players.device_id),
            max_puzzle_number = GREATEST(players.max_puzzle_number, EXCLUDED.max_puzzle_number),
            current_puzzle_number = EXCLUDED.current_puzzle_number,
            last_seen_at = CURRENT_TIMESTAMP,
            updated_at = CURRENT_TIMESTAMP
    ");
    $stmt->execute([$email, $fallbackName, $deviceId, $puzzleNumber, $puzzleNumber]);
}

function resolvePlayerEmailForProgress(PDO $pdo, array $data, ?string $deviceId): ?string {
    $email = normalizeEmail($data);
    if ($email !== null && isEmailValidFormat($email)) {
        return $email;
    }
    if ($deviceId === null) {
        return null;
    }
    ensurePlayersTable($pdo);
    $stmt = $pdo->prepare("SELECT email FROM players WHERE device_id = ? LIMIT 1");
    $stmt->execute([$deviceId]);
    $row = $stmt->fetch();
    if (!$row || !isset($row['email'])) {
        return null;
    }
    return strtolower(trim((string)$row['email']));
}

function handleCheckPlayerEmail(PDO $pdo): void {
    if (!requireSignupJwtIfConfigured()) {
        return;
    }
    $data = json_decode(file_get_contents('php://input'), true) ?? [];
    if (rejectIfHoneypotTripped($data)) {
        return;
    }

    $email = normalizeEmail($data);

    if ($email === null || !isEmailValidFormat($email)) {
        http_response_code(400);
        echo json_encode(['error' => 'Invalid data. Required: valid email (string)']);
        return;
    }

    $ip = getClientIp();
    if (!enforceSignupRateLimit($pdo, 'check_email:ip:' . $ip, 30, 600)) {
        return;
    }
    if (!enforceSignupRateLimit($pdo, 'check_email:email:' . hash('sha256', $email), 12, 600)) {
        return;
    }

    ensurePlayersTable($pdo);

    $stmt = $pdo->prepare("SELECT 1 FROM players WHERE email = ? LIMIT 1");
    $stmt->execute([$email]);
    $row = $stmt->fetch();

    echo json_encode(['exists' => $row !== false]);
}

function handleCreatePlayer(PDO $pdo): void {
    if (!requireSignupJwtIfConfigured()) {
        return;
    }
    $data = json_decode(file_get_contents('php://input'), true) ?? [];
    if (rejectIfHoneypotTripped($data)) {
        return;
    }

    $email = normalizeEmail($data);
    $playerName = isset($data['player_name']) ? trim((string)$data['player_name']) : null;
    $deviceId = normalizeDeviceId($data);

    if ($email === null || !isEmailValidFormat($email) || $playerName === null || $playerName === '') {
        http_response_code(400);
        echo json_encode(['error' => 'Invalid data. Required: valid email (string), player_name (string)']);
        return;
    }

    // UI validation is expected, but keep the DB safe too.
    if (!preg_match('/^[A-Za-z0-9]{1,12}$/', $playerName)) {
        http_response_code(400);
        echo json_encode(['error' => 'Invalid player_name. Use letters and numbers only (max 12 chars).']);
        return;
    }

    $ip = getClientIp();
    if (!enforceSignupRateLimit($pdo, 'create_player:ip:' . $ip, 8, 600)) {
        return;
    }
    if (!enforceSignupRateLimit($pdo, 'create_player:email:' . hash('sha256', $email), 4, 900)) {
        return;
    }

    ensurePlayersTable($pdo);

    $stmt = $pdo->prepare("SELECT 1 FROM players WHERE email = ? LIMIT 1");
    $stmt->execute([$email]);
    if ($stmt->fetch() !== false) {
        http_response_code(409);
        echo json_encode([
            'success' => false,
            'exists' => true,
            'error' => 'Email already registered'
        ]);
        return;
    }

    $currentPuzzle = 1;
    $maxPuzzle = 1;
    $puzzlesPlayed = 0;
    $playTime = 0.0;

    if ($deviceId !== null) {
        ensureDeviceProgressColumns($pdo);
        $devStmt = $pdo->prepare("SELECT current_puzzle_number, max_puzzle_number, puzzles_played, total_play_time_seconds FROM devices WHERE device_id = ? LIMIT 1");
        $devStmt->execute([$deviceId]);
        $devRow = $devStmt->fetch();
        if ($devRow) {
            $currentPuzzle = (int)$devRow['current_puzzle_number'];
            $maxPuzzle = (int)$devRow['max_puzzle_number'];
            $puzzlesPlayed = (int)$devRow['puzzles_played'];
            $playTime = (float)$devRow['total_play_time_seconds'];
        }
    }

    try {
        $stmt = $pdo->prepare("
            INSERT INTO players (
                email,
                player_name,
                device_id,
                current_puzzle_number,
                max_puzzle_number,
                puzzles_played,
                total_play_time_seconds,
                last_seen_at,
                created_at,
                updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        ");
        $stmt->execute([$email, $playerName, $deviceId, $currentPuzzle, $maxPuzzle, $puzzlesPlayed, $playTime]);
    } catch (PDOException $e) {
        // Race: another signup landed the same email between our check and insert.
        if (($e->errorInfo[0] ?? '') === '23505') {
            http_response_code(409);
            echo json_encode([
                'success' => false,
                'exists' => true,
                'error' => 'Email already registered'
            ]);
            return;
        }
        throw $e;
    }

    echo json_encode([
        'success' => true,
        'exists' => false
    ]);
}
