-- Tracks the last successful Kiosk Cookie authentication (ADR-02-013), so the Cookie-refresh Filter can tell
-- whether the current 30-day Cookie has 7 days or fewer remaining without a separate Session/Token table.
ALTER TABLE kiosk_device
    ADD COLUMN last_authenticated_at TIMESTAMPTZ;
