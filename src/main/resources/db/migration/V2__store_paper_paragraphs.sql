CREATE TABLE paper_versions (
    paper_id text NOT NULL,
    version integer NOT NULL CHECK (version > 0),
    html_sha256 text NOT NULL,
    PRIMARY KEY (paper_id, version)
);

CREATE TABLE paper_paragraphs (
    paper_id text NOT NULL,
    version integer NOT NULL,
    paragraph_id integer NOT NULL,
    section text NOT NULL,
    text text NOT NULL,
    source_uri text NOT NULL,
    PRIMARY KEY (paper_id, version, paragraph_id),
    FOREIGN KEY (paper_id, version) REFERENCES paper_versions
);
