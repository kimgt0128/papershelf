WITH versions AS (
    SELECT paper_id, max(version) AS version FROM passages
    WHERE (CAST(:paper AS text) IS NULL OR (paper_id=:paper AND version=:version))
    GROUP BY paper_id
), scoped AS (
    SELECT p.* FROM passages p JOIN versions USING (paper_id, version)
), dense AS (
    SELECT paper_id, version, paragraph_id, start_offset,
           row_number() OVER (ORDER BY embedding <=> CAST(:embedding AS vector),
               paper_id, paragraph_id, start_offset) AS rank
    FROM scoped ORDER BY rank LIMIT 20
), lexical AS (
    SELECT paper_id, version, paragraph_id, start_offset,
           row_number() OVER (ORDER BY ts_rank(keywords, CAST(:terms AS tsquery)) DESC,
               paper_id, paragraph_id, start_offset) AS rank
    FROM scoped WHERE :hybrid AND keywords @@ CAST(:terms AS tsquery)
    ORDER BY rank LIMIT 20
), combined AS (
    SELECT paper_id, version, paragraph_id, start_offset, sum(1.0 / (60 + rank)) AS score
    FROM (SELECT * FROM dense UNION ALL SELECT * FROM lexical) ranks
    GROUP BY paper_id, version, paragraph_id, start_offset
    ORDER BY score DESC, paper_id, paragraph_id, start_offset LIMIT 20
)
SELECT c.paper_id, c.version, c.paragraph_id, c.start_offset, chunk.text,
       parent.text AS paragraph, parent.source_uri, c.score
FROM combined c JOIN passages chunk USING (paper_id, version, paragraph_id, start_offset)
JOIN paper_paragraphs parent USING (paper_id, version, paragraph_id)
ORDER BY score DESC, paper_id, paragraph_id, start_offset
