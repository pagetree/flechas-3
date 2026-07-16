<?php
declare(strict_types=1);

require_once __DIR__ . '/bootstrap.php';
require_once __DIR__ . '/topbar.php';

if (!empty($_SESSION['admin_auth'])) {
    header('Location: ' . adminUrl('dashboard.php'));
    exit;
}

$error = '';
$email = '';

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $email = mb_strtolower(trim((string)($_POST['email'] ?? '')));
    $password = (string)($_POST['password'] ?? '');
    $honeypot = trim((string)($_POST['website'] ?? ''));
    $csrf = $_POST['csrf_token'] ?? null;

    if (!validateCsrf($csrf)) {
        http_response_code(400);
        $error = 'Invalid request token. Please refresh and try again.';
    } elseif ($honeypot !== '') {
        recordAttempt($pdo, $email, false, true);
        http_response_code(400);
        $error = 'Login failed.';
        usleep(random_int(250000, 500000));
    } elseif (isRateLimited($pdo, $email)) {
        http_response_code(429);
        $error = 'Too many login attempts. Try again later.';
    } else {
        $stmt = $pdo->prepare("SELECT email, password_hash, is_active FROM admin_users WHERE email = ? LIMIT 1");
        $stmt->execute([$email]);
        $user = $stmt->fetch();
        $valid = false;

        if ($user && (bool)$user['is_active']) {
            $valid = password_verify($password, (string)$user['password_hash']);
        }

        recordAttempt($pdo, $email, $valid, false);

        if ($valid) {
            session_regenerate_id(true);
            $_SESSION['admin_auth'] = true;
            $_SESSION['admin_email'] = $email;
            $_SESSION['admin_ua_hash'] = hash('sha256', (string)($_SERVER['HTTP_USER_AGENT'] ?? ''));
            $_SESSION['csrf_token'] = bin2hex(random_bytes(32));
            header('Location: ' . adminUrl('dashboard.php'));
            exit;
        }

        $error = 'Invalid email or password.';
        usleep(random_int(250000, 500000));
    }
}
?>
<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Arrow Game Admin Login</title>
    <link rel="stylesheet" href="<?= htmlspecialchars(adminUrl('admin.css'), ENT_QUOTES, 'UTF-8') ?>">
</head>
<body>
<div class="wrap">
    <?php renderAdminTopbar(false, false); ?>
    <?php if ($error !== ''): ?>
        <div class="error"><?= htmlspecialchars($error, ENT_QUOTES, 'UTF-8') ?></div>
    <?php endif; ?>
    <form method="post" action="<?= htmlspecialchars(adminUrl('login.php'), ENT_QUOTES, 'UTF-8') ?>" autocomplete="off">
        <input type="hidden" name="csrf_token" value="<?= htmlspecialchars(csrfToken(), ENT_QUOTES, 'UTF-8') ?>">
        <div class="hp">
            <label for="website">Website</label>
            <input id="website" name="website" type="text" tabindex="-1" autocomplete="off">
        </div>
        <label for="email">Email</label>
        <input id="email" type="email" name="email" required value="<?= htmlspecialchars($email, ENT_QUOTES, 'UTF-8') ?>">
        <label for="password">Password</label>
        <input id="password" type="password" name="password" required>
        <button type="submit">Sign in</button>
    </form>
</div>
</body>
</html>
