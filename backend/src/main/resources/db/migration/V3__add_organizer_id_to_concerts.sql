-- TicketBox Database Migration V3: Add organizer_id to concerts table
-- Enables multi-tenant ownership enforcement for Organizers

ALTER TABLE concerts ADD COLUMN organizer_id UUID;

-- Backfill existing concerts from V2 seed data to belong to default ORGANIZER user (organizer@ticketbox.com)
UPDATE concerts 
SET organizer_id = '22222222-2222-2222-2222-222222222222' 
WHERE organizer_id IS NULL;

-- Enforce NOT NULL and Foreign Key constraint referencing users table
ALTER TABLE concerts ALTER COLUMN organizer_id SET NOT NULL;

ALTER TABLE concerts 
    ADD CONSTRAINT fk_concerts_organizer 
    FOREIGN KEY (organizer_id) REFERENCES users(id);

CREATE INDEX idx_concerts_organizer_id ON concerts(organizer_id);
