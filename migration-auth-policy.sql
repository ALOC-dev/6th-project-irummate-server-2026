-- Run this once before deploying the application version that removes
-- GUEST, UserStatus.PENDING, and CertificationStatus.REQUESTED.
BEGIN;

UPDATE users
SET role = 'USER'
WHERE role = 'GUEST';

UPDATE users
SET status = 'ACTIVE'
WHERE status = 'PENDING';

UPDATE certifications
SET certification_status = 'PENDING'
WHERE certification_status = 'REQUESTED';

COMMIT;
