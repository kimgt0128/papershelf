ALTER TABLE passages DROP COLUMN keywords;
ALTER TABLE passages ADD COLUMN keywords tsvector GENERATED ALWAYS AS (
    to_tsvector('simple', text) || array_to_tsvector(
        array_remove(regexp_split_to_array(lower(text), '[^[:alnum:]_]+'), ''))
) STORED;
CREATE INDEX passages_keywords ON passages USING gin(keywords);
