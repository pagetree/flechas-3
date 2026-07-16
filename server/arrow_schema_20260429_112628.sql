-- Arrow Game schema-only dump
-- Generated at: 2026-04-29 11:26:28 UTC
-- Contains table/column definitions only (no rows)

CREATE TABLE IF NOT EXISTS "public"."admin_login_attempts" (
    "id" integer NOT NULL DEFAULT nextval('admin_login_attempts_id_seq'::regclass),
    "email" text,
    "ip" text,
    "user_agent" text,
    "attempted_at" timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "success" boolean NOT NULL DEFAULT false,
    "honeypot_hit" boolean NOT NULL DEFAULT false,
    PRIMARY KEY ("id")
);

CREATE TABLE IF NOT EXISTS "public"."admin_users" (
    "id" integer NOT NULL DEFAULT nextval('admin_users_id_seq'::regclass),
    "email" text NOT NULL,
    "password_hash" text NOT NULL,
    "is_active" boolean NOT NULL DEFAULT true,
    "created_at" timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY ("id")
);

CREATE TABLE IF NOT EXISTS "public"."devices" (
    "device_id" text NOT NULL,
    "puzzles_played" integer NOT NULL DEFAULT 0,
    "total_play_time_seconds" double precision NOT NULL DEFAULT 0,
    "created_at" timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "last_seen_at" timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "max_puzzle_number" integer NOT NULL DEFAULT 1,
    "current_puzzle_number" integer NOT NULL DEFAULT 1,
    PRIMARY KEY ("device_id")
);

CREATE TABLE IF NOT EXISTS "public"."play_logs" (
    "id" integer NOT NULL DEFAULT nextval('play_logs_id_seq'::regclass),
    "seed" integer,
    "completion_time" double precision,
    "device_id" text,
    "played_at" timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY ("id")
);

CREATE TABLE IF NOT EXISTS "public"."players" (
    "email" text NOT NULL,
    "player_name" character varying(12) NOT NULL,
    "current_puzzle_number" integer NOT NULL DEFAULT 1,
    "max_puzzle_number" integer NOT NULL DEFAULT 1,
    "puzzles_played" integer NOT NULL DEFAULT 0,
    "total_play_time_seconds" double precision NOT NULL DEFAULT 0,
    "last_seen_at" timestamp with time zone NOT NULL DEFAULT now(),
    "created_at" timestamp with time zone NOT NULL DEFAULT now(),
    "updated_at" timestamp with time zone NOT NULL DEFAULT now(),
    "device_id" text,
    PRIMARY KEY ("email")
);

CREATE TABLE IF NOT EXISTS "public"."puzzles" (
    "seed" integer NOT NULL,
    "difficulty" integer DEFAULT 1,
    "completions" integer DEFAULT 0,
    "avg_time_seconds" double precision DEFAULT 0.0,
    "created_at" timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY ("seed")
);

CREATE TABLE IF NOT EXISTS "public"."signup_rate_limits" (
    "rl_key" text NOT NULL,
    "window_started_at" timestamp with time zone NOT NULL,
    "attempt_count" integer NOT NULL DEFAULT 0,
    "updated_at" timestamp with time zone NOT NULL DEFAULT now(),
    PRIMARY KEY ("rl_key")
);
