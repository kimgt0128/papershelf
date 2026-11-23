"""Compare retrieval modes on version-pinned questions and save a local report."""
import argparse
import csv
import html
import json
import math
from pathlib import Path
from statistics import mean
from urllib.request import Request, urlopen


def search(base, item, mode):
    body = {k: item[k] for k in ("question", "paperId", "version")}
    request = Request(base.rstrip("/") + "/search", json.dumps(dict(body, mode=mode)).encode(),
                      {"Content-Type": "application/json"})
    with urlopen(request, timeout=90) as response:
        return json.load(response)


def matches(hit, group):
    return any(hit["paragraphId"] == ref["paragraphId"] and ref["quote"] in hit["text"]
               for ref in group)


def score(hits, groups):
    first = [next((rank for rank, hit in enumerate(hits, 1) if matches(hit, group)), 0)
             for group in groups]
    top = sorted(set(rank for rank in first if 0 < rank <= 5))
    return (sum(0 < rank <= 5 for rank in first) / len(groups),
            1 / top[0] if top else 0,
            mean(i / rank for i, rank in enumerate(top, 1)) if top else 0,
            first)


def evaluate(args):
    questions = json.loads(args.gold.read_text())
    if not questions or args.repeat < 1 or any(not q["evidence"] for q in questions):
        raise ValueError("Provide answerable questions and at least one repetition")
    args.out.mkdir(parents=True, exist_ok=False)
    rows = []
    with (args.out / "queries.jsonl").open("w") as log:
        for item in questions:
            for mode in ("VECTOR", "HYBRID"):
                search(args.base, item, mode)
            for run in range(args.repeat):
                modes = ("VECTOR", "HYBRID") if run % 2 == 0 else ("HYBRID", "VECTOR")
                for mode in modes:
                    result = search(args.base, item, mode)
                    recall, mrr, precision, ranks = score(result["candidates"], item["evidence"])
                    rows.append((mode, recall, mrr, precision, result["elapsedMs"]))
                    log.write(json.dumps(dict(question=item, mode=mode, run=run + 1,
                                              evidenceRanks=ranks, result=result)) + "\n")
                    log.flush()
    header = ["방식", "질문 수", "요청 수", "Recall@5", "MRR@5", "Context Precision@5", "검색 p95 (ms)"]
    summary = []
    for mode in ("VECTOR", "HYBRID"):
        values = [row for row in rows if row[0] == mode]
        latencies = sorted(row[4] for row in values)
        summary.append([mode, len(questions), len(values),
                        *(round(mean(row[i] for row in values), 3) for i in (1, 2, 3)),
                        round(latencies[math.ceil(0.95 * len(latencies)) - 1], 2)])
    with (args.out / "summary.csv").open("w", newline="") as csvfile:
        csv.writer(csvfile).writerows([header, *summary])
    table = "".join("<tr>" + "".join(f"<td>{html.escape(str(v))}</td>" for v in row) + "</tr>"
                    for row in [header, *summary])
    (args.out / "report.html").write_text(
        '<!doctype html><html lang="ko"><meta charset="utf-8"><title>검색 측정 결과</title>'
        '<style>body{font:16px Gulim,sans-serif;margin:3rem}td{padding:.6em;text-align:right}'
        'tr:first-child{font-weight:bold}</style><h1>검색 측정 결과</h1><table>' + table + '</table></html>')


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", default="http://localhost:8080")
    parser.add_argument("--gold", type=Path, default=Path("evaluation/demo.json"))
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--repeat", type=int, default=5)
    evaluate(parser.parse_args())
