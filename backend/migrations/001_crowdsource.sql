CREATE TABLE IF NOT EXISTS gtfs_stops (
    stop_id         VARCHAR(80)   PRIMARY KEY,
    stop_name       VARCHAR(150)  NOT NULL,
    stop_lat        DECIMAL(10,8) NOT NULL,
    stop_lon        DECIMAL(10,8) NOT NULL,
    is_major_stand  BOOLEAN       DEFAULT FALSE,
    source          VARCHAR(20)   DEFAULT 'crowdsourced',
    confidence      INTEGER       DEFAULT 1,
    created_at      TIMESTAMP     DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS route_index (
    id              SERIAL PRIMARY KEY,
    otp_route_id    VARCHAR(100) UNIQUE,
    short_name      VARCHAR(80),
    long_name       VARCHAR(250),
    operator        VARCHAR(100),
    origin_stop_id  VARCHAR(80),
    origin_name     VARCHAR(150),
    dest_stop_id    VARCHAR(80),
    dest_name       VARCHAR(150),
    stop_ids        JSONB,
    source          VARCHAR(20)  DEFAULT 'crowdsourced',
    last_synced     TIMESTAMP    DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS route_index_fts ON route_index
USING GIN (to_tsvector('english',
  COALESCE(short_name,'') || ' ' || COALESCE(long_name,'') || ' ' ||
  COALESCE(operator,'')   || ' ' || COALESCE(origin_name,'') || ' ' ||
  COALESCE(dest_name,'')
));

CREATE TABLE IF NOT EXISTS gtfs_routes (
    route_id            VARCHAR(80)  PRIMARY KEY,
    route_short_name    VARCHAR(80),
    route_long_name     VARCHAR(250),
    route_type          INTEGER      DEFAULT 3,
    status              VARCHAR(20)  DEFAULT 'pending',
    confirmation_count  INTEGER      DEFAULT 0,
    source              VARCHAR(20)  DEFAULT 'crowdsourced'
);

CREATE TABLE IF NOT EXISTS gtfs_stop_times (
    trip_id         VARCHAR(100),
    stop_id         VARCHAR(80) REFERENCES gtfs_stops(stop_id),
    stop_sequence   INTEGER,
    arrival_time    VARCHAR(8),
    departure_time  VARCHAR(8),
    pickup_type     INTEGER DEFAULT 0,
    drop_off_type   INTEGER DEFAULT 0,
    sample_count    INTEGER DEFAULT 1,
    PRIMARY KEY (trip_id, stop_sequence)
);

CREATE TABLE IF NOT EXISTS gtfs_shapes (
    shape_id            VARCHAR(100),
    shape_pt_lat        DECIMAL(10,8),
    shape_pt_lon        DECIMAL(10,8),
    shape_pt_sequence   INTEGER,
    PRIMARY KEY (shape_id, shape_pt_sequence)
);

CREATE TABLE IF NOT EXISTS trip_submissions (
    id                       SERIAL PRIMARY KEY,
    device_id                VARCHAR(64),
    bus_name                 VARCHAR(100) NOT NULL,
    mode                     VARCHAR(20)  NOT NULL,
    otp_route_id             VARCHAR(100),
    declared_final_stop_id   VARCHAR(80),
    declared_final_stop_name VARCHAR(150),
    stops                    JSONB        NOT NULL,
    shape_points             JSONB,
    trust_score_at_submit    DECIMAL(4,2) DEFAULT 0.5,
    status                   VARCHAR(20)  DEFAULT 'pending',
    submitted_at             TIMESTAMP    DEFAULT NOW(),
    reviewed_at              TIMESTAMP,
    reviewer_note            TEXT
);

CREATE TABLE IF NOT EXISTS contributors (
    device_id   VARCHAR(64)  PRIMARY KEY,
    submissions INTEGER      DEFAULT 0,
    accepted    INTEGER      DEFAULT 0,
    flagged     INTEGER      DEFAULT 0,
    trust_score DECIMAL(4,2) GENERATED ALWAYS AS (
        CASE WHEN submissions = 0 THEN 0.5
             ELSE LEAST(1.0,
                (accepted::DECIMAL / GREATEST(submissions,1))
                * (1.0 - (flagged::DECIMAL / GREATEST(submissions,1)))
             )
        END
    ) STORED
);
