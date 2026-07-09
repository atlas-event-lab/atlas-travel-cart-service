-- ADR-0011: hotel stay dates on cart items. `cart_items` becomes a SINGLE_TABLE hierarchy
-- (FlightCartItem / HotelCartItem) discriminated by the existing `type` column; hotel items add
-- nullable check_in / check_out. Forward-only, additive DDL.
ALTER TABLE cart_items ADD COLUMN check_in  DATE;
ALTER TABLE cart_items ADD COLUMN check_out DATE;
