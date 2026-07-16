CREATE TABLE IF NOT EXISTS puzzles (
    seed INT PRIMARY KEY,
    difficulty INT DEFAULT 1,
    completions INT DEFAULT 0,
    avg_time_seconds FLOAT DEFAULT 0.0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS play_logs (
    id SERIAL PRIMARY KEY,
    seed INT REFERENCES puzzles(seed),
    completion_time FLOAT,
    device_id TEXT,
    played_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS devices (
    device_id TEXT PRIMARY KEY,
    puzzles_played INT NOT NULL DEFAULT 0,
    total_play_time_seconds FLOAT NOT NULL DEFAULT 0,
    max_puzzle_number INT NOT NULL DEFAULT 1,
    current_puzzle_number INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS players (
    email TEXT PRIMARY KEY,
    player_name VARCHAR(12) NOT NULL,
    current_puzzle_number INTEGER NOT NULL DEFAULT 1,
    max_puzzle_number INTEGER NOT NULL DEFAULT 1,
    puzzles_played INTEGER NOT NULL DEFAULT 0,
    total_play_time_seconds DOUBLE PRECISION NOT NULL DEFAULT 0,
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS signup_rate_limits (
    rl_key TEXT PRIMARY KEY,
    window_started_at TIMESTAMPTZ NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
