CREATE TABLE outbox_events (
    event_id UUID PRIMARY KEY,

    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id UUID NOT NULL,

    event_type VARCHAR(100) NOT NULL,

    payload TEXT NOT NULL,

    status VARCHAR(20) NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT chk_outbox_status
        CHECK (status IN ('NEW', 'PUBLISHED'))
);

CREATE INDEX idx_outbox_events_status_created_at
    ON outbox_events(status, created_at);

CREATE INDEX idx_outbox_events_aggregate
    ON outbox_events(aggregate_type, aggregate_id);