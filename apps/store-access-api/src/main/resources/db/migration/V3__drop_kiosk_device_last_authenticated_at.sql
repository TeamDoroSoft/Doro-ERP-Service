-- ADR-02-013 was revised: the DORO_KIOSK_DEVICE Cookie's Max-Age is now fixed for 30 days from issuance and
-- is never refreshed by a successful Kiosk business request (no Sliding Renewal). The column this Forward-fix
-- removes existed only to support that now-removed refresh threshold check.
ALTER TABLE kiosk_device
    DROP COLUMN last_authenticated_at;
