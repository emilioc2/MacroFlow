-- V1__init.sql
-- MacroFlow initial schema — all tables in dependency order (referenced tables first).
-- Managed by Flyway; never alter this file after it has been applied to any environment.

CREATE TABLE app_user (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider     TEXT NOT NULL,   -- 'apple' or 'google'
    provider_sub TEXT NOT NULL,   -- subject claim from the provider's ID token
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_sub)
);

CREATE TABLE user_profile (
    user_id        UUID PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
    sex            TEXT NOT NULL,
    date_of_birth  DATE NOT NULL,
    height_cm      NUMERIC(5,2) NOT NULL,
    weight_kg      NUMERIC(5,2) NOT NULL,
    activity       TEXT NOT NULL,  -- sedentary|light|moderate|active|very_active
    goal           TEXT NOT NULL,  -- lose|maintain|gain
    timezone       TEXT NOT NULL DEFAULT 'UTC',
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE daily_targets (
    user_id        UUID PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
    calories_kcal  NUMERIC(7,2) NOT NULL,
    protein_g      NUMERIC(6,2) NOT NULL,
    carbs_g        NUMERIC(6,2) NOT NULL,
    fat_g          NUMERIC(6,2) NOT NULL,
    -- is_override distinguishes user-set targets from auto-calculated TDEE targets
    is_override    BOOLEAN NOT NULL DEFAULT false,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- owner_id NULL means this is a global/shared food entry (not user-created)
CREATE TABLE food (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id    UUID REFERENCES app_user(id) ON DELETE CASCADE,
    name        TEXT NOT NULL,
    calories    NUMERIC(7,2) NOT NULL,
    protein_g   NUMERIC(6,2) NOT NULL,
    carbs_g     NUMERIC(6,2) NOT NULL,
    fat_g       NUMERIC(6,2) NOT NULL,
    serving_g   NUMERIC(6,2) NOT NULL,
    is_custom   BOOLEAN NOT NULL DEFAULT false,
    source      TEXT NOT NULL DEFAULT 'CUSTOM',  -- CUSTOM|USDA|OFF
    external_id TEXT,   -- ID from the originating external data source
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE meal_entry (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    food_id      UUID NOT NULL REFERENCES food(id),
    log_date     DATE NOT NULL,
    logged_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    meal_name    TEXT,
    servings     NUMERIC(6,2) NOT NULL DEFAULT 1.0,
    protein_g    NUMERIC(6,2) NOT NULL,
    carbs_g      NUMERIC(6,2) NOT NULL,
    fat_g        NUMERIC(6,2) NOT NULL,
    calories     NUMERIC(7,2) NOT NULL,
    -- client_id is a client-generated UUID used as an idempotency key for sync
    client_id    TEXT NOT NULL UNIQUE,
    client_ts    TIMESTAMPTZ NOT NULL,  -- client-side timestamp for conflict resolution
    deleted      BOOLEAN NOT NULL DEFAULT false,  -- soft delete; hard delete not supported
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE saved_meal (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    name       TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE saved_meal_item (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    saved_meal_id UUID NOT NULL REFERENCES saved_meal(id) ON DELETE CASCADE,
    food_id       UUID NOT NULL REFERENCES food(id),
    servings      NUMERIC(6,2) NOT NULL DEFAULT 1.0
);

CREATE TABLE recently_logged (
    user_id     UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    food_id     UUID NOT NULL REFERENCES food(id) ON DELETE CASCADE,
    last_logged TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, food_id)
);

CREATE TABLE reminder_config (
    user_id         UUID PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
    meal_enabled    BOOLEAN NOT NULL DEFAULT true,
    meal_time       TIME NOT NULL DEFAULT '12:00',
    eod_enabled     BOOLEAN NOT NULL DEFAULT true,
    eod_time        TIME NOT NULL DEFAULT '20:00',
    streak_enabled  BOOLEAN NOT NULL DEFAULT false,
    streak_time     TIME NOT NULL DEFAULT '21:00',
    checkin_enabled BOOLEAN NOT NULL DEFAULT false,
    checkin_day     INTEGER NOT NULL DEFAULT 0  -- 0=Sunday … 6=Saturday
);

CREATE TABLE user_preferences (
    user_id              UUID PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
    recently_logged_max  INTEGER NOT NULL DEFAULT 10,
    tutorial_shown       BOOLEAN NOT NULL DEFAULT false,
    theme                TEXT NOT NULL DEFAULT 'system'  -- light|dark|system
);

-- Composite index for the most common query: entries for a user on a given date
CREATE INDEX idx_meal_entry_user_date ON meal_entry(user_id, log_date);

-- Supports filtering custom foods by owner
CREATE INDEX idx_food_owner_id ON food(owner_id);

-- Supports deduplication and lookup by external data source ID
CREATE INDEX idx_food_external_id ON food(external_id);
