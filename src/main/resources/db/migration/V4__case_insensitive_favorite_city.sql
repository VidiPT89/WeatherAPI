-- The app's own duplicate check (existsByUserAndCityIgnoreCase) is case-insensitive, but this
-- constraint was exact-string, so concurrent adds of e.g. "Lisbon" and "LISBON" for the same
-- user could both pass the app-level check and land as two separate rows. Make the constraint
-- match the app's actual intent.
ALTER TABLE favorites
    DROP CONSTRAINT uk_favorites_user_city;

CREATE UNIQUE INDEX uk_favorites_user_city_ci ON favorites (user_id, LOWER(city));
