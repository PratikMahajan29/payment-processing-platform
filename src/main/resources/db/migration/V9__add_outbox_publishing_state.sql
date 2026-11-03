ALTER TABLE outbox_events
    ADD COLUMN publishing_started_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE outbox_events
    DROP CONSTRAINT chk_outbox_status;

ALTER TABLE outbox_events
    ADD CONSTRAINT chk_outbox_status
        CHECK (
            status IN (
                'NEW',
                'PUBLISHING',
                'PUBLISHED'
            )
        );

CREATE INDEX idx_outbox_events_publishing_started_at
    ON outbox_events(status, publishing_started_at);