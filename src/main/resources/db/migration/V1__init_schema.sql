CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    preferred_units VARCHAR(20)  NOT NULL DEFAULT 'METRIC',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE search_history (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    city         VARCHAR(255) NOT NULL,
    units        VARCHAR(20)  NOT NULL,
    searched_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_search_history_user_id ON search_history (user_id, searched_at DESC);

CREATE TABLE favorites (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    city        VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_favorites_user_city UNIQUE (user_id, city)
);
