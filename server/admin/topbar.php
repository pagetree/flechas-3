<?php
declare(strict_types=1);

if (!function_exists('renderAdminTopbar')) {
    function renderAdminTopbar(bool $showLogout = false, bool $showEmail = false): void
    {
        $adminEmail = (string)($_SESSION['admin_email'] ?? '');
        ?>
        <div class="admin-topbar">
            <div>
                <img
                    class="brand-logo"
                    src="<?= htmlspecialchars(adminUrl('arrows_logo.png'), ENT_QUOTES, 'UTF-8') ?>"
                    alt="Arrow Game"
                >
                <?php if ($showEmail && $adminEmail !== ''): ?>
                    <div class="small">Signed in as <?= htmlspecialchars($adminEmail, ENT_QUOTES, 'UTF-8') ?></div>
                <?php endif; ?>
            </div>
            <?php if ($showLogout): ?>
                <form method="post" action="<?= htmlspecialchars(adminUrl('logout.php'), ENT_QUOTES, 'UTF-8') ?>">
                    <input type="hidden" name="csrf_token" value="<?= htmlspecialchars(csrfToken(), ENT_QUOTES, 'UTF-8') ?>">
                    <button class="btn" type="submit">Logout</button>
                </form>
            <?php endif; ?>
        </div>
        <?php
    }
}
