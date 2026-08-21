-- CHAR(3) pads with trailing spaces and maps awkwardly to a JPA String field
-- (Hibernate expects varchar). VARCHAR(3) keeps the entity mapping clean.
ALTER TABLE orders ALTER COLUMN currency TYPE VARCHAR(3);
