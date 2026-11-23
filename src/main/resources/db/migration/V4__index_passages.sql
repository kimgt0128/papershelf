CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE passages (
    paper_id text NOT NULL,
    version integer NOT NULL,
    paragraph_id integer NOT NULL,
    start_offset integer NOT NULL,
    text text NOT NULL,
    embedding vector(384) NOT NULL,
    keywords tsvector GENERATED ALWAYS AS (to_tsvector('simple', text)) STORED,
    PRIMARY KEY (paper_id, version, paragraph_id, start_offset),
    FOREIGN KEY (paper_id, version, paragraph_id) REFERENCES paper_paragraphs
);
CREATE INDEX passages_keywords ON passages USING gin(keywords);
