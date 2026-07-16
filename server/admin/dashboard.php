<?php
declare(strict_types=1);

require_once __DIR__ . '/bootstrap.php';
require_once __DIR__ . '/topbar.php';
requireAdmin();

function trendMeta(float $current, float $previous): array
{
    $delta = $current - $previous;
    if (abs($delta) < 0.00001) {
        return [
            'class' => 'trend-neutral',
            'icon' => 'trending_flat',
            'value' => '0.0%',
        ];
    }

    $positive = $delta > 0;
    $pct = $previous > 0 ? (abs($delta) / $previous) * 100 : 100;

    return [
        'class' => $positive ? 'trend-positive' : 'trend-negative',
        'icon' => $positive ? 'trending_up' : 'trending_down',
        'value' => sprintf('%s%s', $positive ? '+' : '-', number_format($pct, 1) . '%'),
    ];
}

$range = (string)($_GET['range'] ?? '24h');
$allowedRanges = [
    '24h' => "24 hours",
    '7d' => "7 days",
    '30d' => "30 days",
];
if (!isset($allowedRanges[$range])) {
    $range = '24h';
}
$interval = $allowedRanges[$range];

$metricsStmt = $pdo->prepare("
    WITH current_period AS (
        SELECT completion_time, device_id
        FROM play_logs
        WHERE played_at >= (CURRENT_TIMESTAMP - CAST(:range_interval AS INTERVAL))
    ),
    previous_period AS (
        SELECT completion_time, device_id
        FROM play_logs
        WHERE played_at >= (CURRENT_TIMESTAMP - (CAST(:range_interval AS INTERVAL) * 2))
          AND played_at < (CURRENT_TIMESTAMP - CAST(:range_interval AS INTERVAL))
    )
    SELECT
        (SELECT COUNT(*)::INT FROM current_period) AS total_plays,
        (SELECT COALESCE(AVG(completion_time), 0)::FLOAT FROM current_period) AS avg_time,
        (SELECT COALESCE(SUM(completion_time), 0)::FLOAT FROM current_period) AS total_time,
        (SELECT COUNT(DISTINCT device_id)::INT FROM current_period) AS total_devices,
        (SELECT COUNT(*)::INT FROM previous_period) AS prev_total_plays,
        (SELECT COALESCE(AVG(completion_time), 0)::FLOAT FROM previous_period) AS prev_avg_time,
        (SELECT COALESCE(SUM(completion_time), 0)::FLOAT FROM previous_period) AS prev_total_time,
        (SELECT COUNT(DISTINCT device_id)::INT FROM previous_period) AS prev_total_devices
");
$metricsStmt->bindValue(':range_interval', $interval, PDO::PARAM_STR);
$metricsStmt->execute();
$metrics = $metricsStmt->fetch() ?: [];
$puzzleCountRow = $pdo->query("SELECT COUNT(*)::INT AS total_puzzles FROM puzzles")->fetch() ?: ['total_puzzles' => 0];

$totalPuzzles = (int)($puzzleCountRow['total_puzzles'] ?? 0);
$totalPlays = (int)($metrics['total_plays'] ?? 0);
$avgTime = (float)($metrics['avg_time'] ?? 0);
$totalTime = (float)($metrics['total_time'] ?? 0);
$totalDevices = (int)($metrics['total_devices'] ?? 0);

$playsTrend = trendMeta((float)$totalPlays, (float)($metrics['prev_total_plays'] ?? 0));
$avgTimeTrend = trendMeta($avgTime, (float)($metrics['prev_avg_time'] ?? 0));
$devicesTrend = trendMeta((float)$totalDevices, (float)($metrics['prev_total_devices'] ?? 0));

$registeredPlayersCount = 0;
$guestPlayersCount = 0;
try {
    $registeredPlayersCount = (int)($pdo->query("SELECT COUNT(*)::INT AS c FROM players")->fetch()['c'] ?? 0);
    $guestPlayersCount = (int)($pdo->query("
        SELECT COUNT(*)::INT AS c
        FROM devices d
        WHERE NOT EXISTS (
            SELECT 1 FROM players p
            WHERE p.device_id IS NOT NULL AND p.device_id = d.device_id
        )
    ")->fetch()['c'] ?? 0);
} catch (Throwable $e) {
    $registeredPlayersCount = 0;
    $guestPlayersCount = 0;
}
$allPlayersCount = $registeredPlayersCount + $guestPlayersCount;

$allPlayers = [];
try {
    $allPlayersStmt = $pdo->query("
        SELECT
            'player' AS kind,
            email AS player_id,
            player_name,
            COALESCE(device_id, '') AS device_id,
            current_puzzle_number,
            max_puzzle_number,
            puzzles_played,
            total_play_time_seconds,
            last_seen_at
        FROM players

        UNION ALL

        SELECT
            'guest' AS kind,
            d.device_id AS player_id,
            'Guest' AS player_name,
            d.device_id AS device_id,
            d.current_puzzle_number,
            d.max_puzzle_number,
            d.puzzles_played,
            d.total_play_time_seconds,
            d.last_seen_at
        FROM devices d
        WHERE NOT EXISTS (
            SELECT 1 FROM players p
            WHERE p.device_id IS NOT NULL AND p.device_id = d.device_id
        )

        ORDER BY last_seen_at DESC NULLS LAST
        LIMIT 200
    ");
    $allPlayers = $allPlayersStmt ? $allPlayersStmt->fetchAll() : [];
} catch (Throwable $e) {
    $allPlayers = [];
}
?>
<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Arrow Game Admin Dashboard</title>
    <link rel="stylesheet" href="<?= htmlspecialchars(adminUrl('admin.css'), ENT_QUOTES, 'UTF-8') ?>">
</head>
<body>
<div class="container">
    <?php renderAdminTopbar(true, true); ?>

    <div class="filters-row">
        <div class="time-filter" role="group" aria-label="Time filter">
            <a class="filter-btn <?= $range === '24h' ? 'is-active' : '' ?>" href="<?= htmlspecialchars(adminUrl('dashboard.php?range=24h'), ENT_QUOTES, 'UTF-8') ?>">24 hours</a>
            <a class="filter-btn <?= $range === '7d' ? 'is-active' : '' ?>" href="<?= htmlspecialchars(adminUrl('dashboard.php?range=7d'), ENT_QUOTES, 'UTF-8') ?>">7 days</a>
            <a class="filter-btn <?= $range === '30d' ? 'is-active' : '' ?>" href="<?= htmlspecialchars(adminUrl('dashboard.php?range=30d'), ENT_QUOTES, 'UTF-8') ?>">30 days</a>
        </div>
    </div>

    <div class="cards cards-primary">
        <div class="card card-plays">
            <div class="metric-head">
                <span class="material-symbols-outlined metric-icon" aria-hidden="true">sports_esports</span>
                <div class="label">Total Plays</div>
            </div>
            <div class="value"><?= $totalPlays ?></div>
            <div class="trend-row">
                <span class="trend-value <?= htmlspecialchars($playsTrend['class'], ENT_QUOTES, 'UTF-8') ?>">
                    <span class="material-symbols-outlined trend-icon" aria-hidden="true"><?= htmlspecialchars($playsTrend['icon'], ENT_QUOTES, 'UTF-8') ?></span>
                    <span><?= htmlspecialchars($playsTrend['value'], ENT_QUOTES, 'UTF-8') ?></span>
                </span>
                <span class="trend-label">vs previous period</span>
            </div>
        </div>
        <div class="card card-devices">
            <div class="metric-head">
                <span class="material-symbols-outlined metric-icon" aria-hidden="true">devices</span>
                <div class="label">Total Devices</div>
            </div>
            <div class="value"><?= $totalDevices ?></div>
            <div class="trend-row">
                <span class="trend-value <?= htmlspecialchars($devicesTrend['class'], ENT_QUOTES, 'UTF-8') ?>">
                    <span class="material-symbols-outlined trend-icon" aria-hidden="true"><?= htmlspecialchars($devicesTrend['icon'], ENT_QUOTES, 'UTF-8') ?></span>
                    <span><?= htmlspecialchars($devicesTrend['value'], ENT_QUOTES, 'UTF-8') ?></span>
                </span>
                <span class="trend-label">vs previous period</span>
            </div>
        </div>
        <div class="card card-all-players">
            <div class="metric-head">
                <span class="material-symbols-outlined metric-icon" aria-hidden="true">group</span>
                <div class="label">All Players</div>
            </div>
            <div class="value"><?= $allPlayersCount ?></div>
            <div class="trend-row">
                <span class="trend-label"><?= $registeredPlayersCount ?> registered · <?= $guestPlayersCount ?> guests</span>
            </div>
        </div>
    </div>

    <div class="cards cards-secondary">
        <div class="card card-puzzles">
            <div class="metric-head">
                <span class="material-symbols-outlined metric-icon" aria-hidden="true">extension</span>
                <div class="label">Nr. Puzzles</div>
            </div>
            <div class="value"><?= $totalPuzzles ?></div>
        </div>
        <div class="card card-total-time">
            <div class="metric-head">
                <span class="material-symbols-outlined metric-icon" aria-hidden="true">schedule</span>
                <div class="label">Total Play Time (s)</div>
            </div>
            <div class="value"><?= number_format($totalTime, 0) ?></div>
        </div>
    </div>

    <div class="cards cards-tertiary">
        <div class="card card-avg-time">
            <div class="metric-head">
                <span class="material-symbols-outlined metric-icon" aria-hidden="true">timer</span>
                <div class="label">Average Completion Time (s)</div>
            </div>
            <div class="value"><?= number_format($avgTime, 2) ?></div>
            <div class="trend-row">
                <span class="trend-value <?= htmlspecialchars($avgTimeTrend['class'], ENT_QUOTES, 'UTF-8') ?>">
                    <span class="material-symbols-outlined trend-icon" aria-hidden="true"><?= htmlspecialchars($avgTimeTrend['icon'], ENT_QUOTES, 'UTF-8') ?></span>
                    <span><?= htmlspecialchars($avgTimeTrend['value'], ENT_QUOTES, 'UTF-8') ?></span>
                </span>
                <span class="trend-label">vs previous period</span>
            </div>
        </div>
    </div>

    <div class="panel">
        <div class="panel-title">Players & Guests</div>
        <table>
            <thead>
            <tr>
                <th>Type</th>
                <th>Player ID</th>
                <th>Name</th>
                <th>Device ID</th>
                <th>Current</th>
                <th>Max</th>
                <th>Puzzles Played</th>
                <th>Play Time (s)</th>
                <th>Last Seen</th>
            </tr>
            </thead>
            <tbody>
            <?php if (empty($allPlayers)): ?>
                <tr><td colspan="9">No players or guests yet.</td></tr>
            <?php else: ?>
                <?php foreach ($allPlayers as $row): ?>
                    <?php $kind = (string)($row['kind'] ?? 'guest'); ?>
                    <tr>
                        <td>
                            <span class="type-badge type-<?= $kind === 'player' ? 'player' : 'guest' ?>">
                                <?= $kind === 'player' ? 'Player' : 'Guest' ?>
                            </span>
                        </td>
                        <td><?= htmlspecialchars((string)$row['player_id'], ENT_QUOTES, 'UTF-8') ?></td>
                        <td><?= htmlspecialchars((string)$row['player_name'], ENT_QUOTES, 'UTF-8') ?></td>
                        <td><?= htmlspecialchars((string)$row['device_id'], ENT_QUOTES, 'UTF-8') ?></td>
                        <td><?= (int)$row['current_puzzle_number'] ?></td>
                        <td><?= (int)$row['max_puzzle_number'] ?></td>
                        <td><?= (int)$row['puzzles_played'] ?></td>
                        <td><?= number_format((float)$row['total_play_time_seconds'], 2) ?></td>
                        <td><?= htmlspecialchars((string)$row['last_seen_at'], ENT_QUOTES, 'UTF-8') ?></td>
                    </tr>
                <?php endforeach; ?>
            <?php endif; ?>
            </tbody>
        </table>
    </div>
</div>
</body>
</html>
