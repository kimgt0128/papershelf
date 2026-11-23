# PaperShelf 맥북 청킹·검색 실험 실행계획

**목표**는 고정된 HTML 5편에서 청크 길이를 선택하고, 누적 20편에서 처음 보는 질문 10개의 근거 검색을 검증하는 것이다. 20편 실측 후 50편 또는 100편으로 확대한다.

**구성**은 macOS에서 Java 21·Spring Boot·MiniLM ONNX를 실행하고 Docker에서 PostgreSQL 17·pgvector를 실행하는 방식이다. 검색 단위는 청크, 사람이 채점하고 인용하는 단위는 버전이 고정된 원문 문단이다. 경태는 실행·계측·코드를, 팀원은 논문 목록·근거 검토를 맡는다. 질문은 둘이 나눠 작성한다.

기준 코드는 `4a6e1c0`이다. 전체 연구 범위는 [기존 설계](hpc-rag-spring-implementation-plan.md), 기본 설치는 [README](../README.md), 브랜치·커밋은 [협업 규칙](github.md)을 따른다. 아래 체크박스는 앞으로 수행할 작업이다. 자동화 작업자는 단계별로 `superpowers:executing-plans`를 적용한다.

## 먼저 고정할 값

| 항목 | 기준 |
|---|---|
| 장비 관측값 · 2026-11-23 | M5 Pro, CPU 18코어, 메모리 24 GiB. Docker 메모리 약 7.65 GiB. 당시 macOS 디스크 여유 약 469 GiB, 스왑 사용 약 3.75 GiB. 실행 직전 다시 기록한다. |
| 예제 실측 | `2508.13397v1` HTML 160,851바이트 → 문단 63개 → 청크 63개. 저장된 청크 본문 합계 24,608바이트, 벡터 값 합계 97,060바이트. 한 번의 색인 로그 945 ms에 청킹 21 ms가 포함된다. |
| 입력 예산 | HTML 파일 1개 ≤ 2,097,152바이트(2 MiB), 고유 논문 20편 합계 ≤ 41,943,040바이트(40 MiB). 모델·DB·로그는 따로 집계한다. |
| 실행 조건 | 앱 1개, 처리 중인 논문 1편, 모델 호출당 최대 16청크, 384차원 벡터. 16개씩 추론하더라도 현재 저장기는 논문 한 편의 벡터 전체를 모아 JDBC로 보낸다. |
| 청킹 비교 | 문단 사이를 합치지 않는다. A는 최대 256토큰, B는 최대 128토큰. 둘 다 특수 토큰을 포함하고 겹침은 0. 영어 본문·영어 질문, 같은 모델·토크나이저·분할 위치 선택 알고리즘을 사용한다. |

**검토할 실패 사례 다섯 가지**는 큰 HTML인데 실제 본문이 짧은 경우(①), 토큰 한도 초과나 문자 손실(②), DB 재사용으로 재색인이 생략되는 경우(③), 대체 근거·경계 분할을 오답으로 잘못 세는 경우(④~⑤), 다른 버전 인용과 평가 질문 재사용(⑥)이다.

## ① 팀원 · 재현 가능한 HTML 5편 선정

- [ ] `cs.DC`·`cs.PF`에서 영어 HTML을 열 수 있는 논문을 선정한다. 예제 1편을 포함해 ≤256 KiB 2편, >256 KiB~1 MiB 2편, >1~2 MiB 1편을 목표로 한다. 부족한 구간은 선정 결과표에 표시한다.
- [ ] `corpus/pilot-05/manifest.txt`에 버전을 포함한 ID를 한 줄에 하나씩 적는다. 같은 폴더에 `{ID}.html`을 저장한다. `corpus/pilot-05/inventory.csv`의 열은 `paper_id,version,category,source_url,html_bytes,sha256`으로 고정한다. 같은 ID의 v1·v2는 고유 논문 한 편으로 센다.
- [ ] 프로젝트 루트에서 아래 명령으로 파일을 대조한다. manifest와 inventory를 커밋하고 HTML은 로컬에 보관한다. 두 사람의 파일 해시가 같아야 한다.

```bash
wc -c corpus/pilot-05/*.html
shasum -a 256 corpus/pilot-05/*.html
```

**통과** — 서로 다른 ID 5개, manifest 한 줄당 HTML 한 개, 크기 초과 0개, 해시 불일치 0개. ②에서 256토큰 초과 문단이 있는 논문이 2편 미만이면 같은 크기 구간에서 교체하고 manifest와 해시를 다시 고정한다.

## ② 경태 · 청크 상한 설정과 문단 길이 계측 구현

현재 [SectionAwareChunker](../src/main/java/io/github/kimgt0128/papershelf/index/SectionAwareChunker.java)는 문단이 256토큰을 초과하면 가운데 부근 공백에서 나눈다. [PassageVectorWriter](../src/main/java/io/github/kimgt0128/papershelf/index/PassageVectorWriter.java)는 청크와 벡터를 저장한다. 예제에서는 모든 문단이 그대로 한 청크가 됐으므로 긴 문단 테스트가 필요하다.

- [ ] `src/test/java/io/github/kimgt0128/papershelf/index/SectionAwareChunkerTest.java`에 128/256 두 설정에서 같은 긴 문단을 처리하는 테스트를 먼저 쓴다. 실제 토크나이저로 모든 청크가 상한 이하인지, 겹침 0에서 청크 연결 결과가 원문과 같은지, 이모지가 있는 입력의 UTF-16 시작 오프셋으로 원문을 복원하는지 확인한다. 64·257은 시작 단계에서 거부하고 256은 기존 결과와 같아야 한다.
- [ ] `./gradlew test --tests '*SectionAwareChunkerTest'`에서 새 테스트 실패를 확인한 뒤 청커에 `papershelf.chunk.max-tokens`(기본 256, 허용값 128 또는 256)을 적용한다. MiniLM의 모델 입력 상한은 256으로 유지한다.
- [ ] 같은 테스트를 통과시키고 `feat: 청크 토큰 상한 설정과 원문 위치 검증`으로 커밋한다.
- [ ] `src/main/java/io/github/kimgt0128/papershelf/index/PaperTokenProfile.java`에 절단·패딩 없는 토크나이저로 원문 문단 수, 128·256 초과 문단 수, 최대 토큰 수를 세는 책임을 둔다. `PaperTokenProfileTest.java`에서 실제 길이가 경계와 같은 문단은 초과 수에 들어가지 않는지 확인한다. 계측은 실험 프로필에서만 켜고 모든 비교군에 동일하게 적용한다.
- [ ] 워커에서 논문별 구조화 로그 `paper.indexed` 한 건을 남긴다. 키는 `paper_id,version,html_bytes,paragraphs,over_128,over_256,max_paragraph_tokens,chunks,index_ms,reused`. 기존 색인을 재사용한 건은 시간 비교에서 제외한다. 청킹·추론·DB 쓰기를 포함하는 `index_ms`와 접수→완료 벽시계 시간을 구분한다. 워커 테스트로 신규·재접수 두 경우를 확인하고 별도 커밋한다.

**통과** — `./gradlew test bootJar` 성공. 두 상한에서 긴 문단의 경계가 실제로 달라지고 문단 ID·원문은 같다. 실험 실행은 이 변경을 병합한 뒤 시작한다.

## ③ 경태 · 빈 DB에서 5편 적재 및 자원 측정

- [ ] 전원 연결 상태에서 Java 버전, Git SHA, manifest SHA, 모델 SHA, 설정, 실행 시작 시각을 `runs/<실행명>/environment.txt`에 적는다. 다른 대형 작업을 중지하고 `sysctl vm.swapusage`와 `df -h .` 값을 저장한다. 설치·모델 다운로드 시간을 색인 시간에서 제외한다.
- [ ] 실행마다 새 Compose 프로젝트명을 사용한다. 아래는 A군 첫 실행이다. 셸에 `PAPERSHELF_DB_PASSWORD`를 먼저 설정한다. B군은 A의 앱을 종료한 뒤 같은 명령에서 `RUN`을 새 이름으로, `LIMIT`을 128로 바꾼다. DB는 `docker compose stop db`로 멈추면 볼륨이 보존된다.

```bash
RUN=pilot256-r1
LIMIT=256
export COMPOSE_PROJECT_NAME="papershelf-$RUN" PAPERSHELF_DB_PORT=25439
mkdir -p "runs/$RUN"
docker compose up -d --wait db
./gradlew bootJar
java -jar build/libs/papershelf-0.0.1-SNAPSHOT.jar \
  --server.port=28089 --papershelf.chunk.max-tokens="$LIMIT" > "runs/$RUN/app.jsonl" 2>&1 &
APP_PID=$!
```

- [ ] `curl -fsS http://localhost:28089/actuator/health`에서 `UP`을 확인한다. **스키마 생성 후, 접수 전** 아래 SQL을 실행한다. `passages`가 0이 아니면 새 프로젝트명으로 다시 시작한다. 저장기는 동일 ID·버전에 청크가 이미 있으면 색인을 건너뛴다.

```bash
docker compose exec -T db psql -U papershelf -d papershelf \
  -c 'SELECT count(*) AS chunks, pg_database_size(current_database()) AS db_bytes FROM passages'
curl -fsS http://localhost:28089/ingest -H 'Content-Type: application/json' \
  -d "{\"runId\":\"$(uuidgen)\",\"shardId\":\"pilot-05\"}"
```

- [ ] 응답의 `jobId`를 `GET /jobs/{jobId}`에서 초당 한 번 조회한다. `COMPLETED`까지 걸린 시간, 실패 시 `failedStage`를 기록한다. 별도 터미널에서 Java PID를 전달받아 `ps -o rss= -p <PID>`를 초당 한 번 기록한다. macOS의 KiB 값을 1024로 나눠 **관측 최대 RSS MiB**를 낸다. 종료 직후 스왑과 같은 SQL의 DB 크기를 다시 기록한다.
- [ ] `SELECT paper_id, version, count(*) FROM passages GROUP BY paper_id, version ORDER BY paper_id, version`으로 5편 각각의 청크 수를 로그와 대조한다. 새 runId로 같은 manifest를 다시 접수해 편수·청크 수가 그대로인지 확인한다. 재접수는 처리량 분모에서 제외한다.

**통과** — 5편 모두 문단·청크 1개 이상, 실패·중복 0건. RSS와 스왑이 급증해 macOS 메모리 압력이 노란색·빨간색으로 지속되면 확장을 멈추고 가장 큰 파일 한 편을 단독 재실행한다. 파일별 벡터 누적을 먼저 조사한다.

## ④ 두 사람 · 답을 먼저 고정하고 128/256을 비교

- [ ] 각자 파일럿 논문에서 영어 질문 5개를 쓰고 상대가 원문에서 근거를 확인한다. 5편 모두 포함하고, 10개 중 식별자 3개·수치/조건 4개·방법 3개, 긴 문단 근거가 최소 3개가 되게 한다. [예제](../evaluation/demo.json) 형식으로 `evaluation/pilot-gold.json`을 검색 전에 커밋한다. `evidence`의 바깥 배열은 모두 필요한 근거 그룹, 안쪽 배열은 같은 그룹의 허용 가능한 대체 인용이다.
- [ ] 경태는 [평가 스크립트](../scripts/evaluate.py)에 질문별 진단표를 추가한다. `scripts/test_evaluate.py`에서 필수 근거 두 그룹 중 하나만 맞히면 Recall=0.5, 완전 정답=0인지, 대체 근거 또는 중복 청크가 한 그룹으로만 계산되는지 테스트한다. `python3 -m unittest discover -s scripts -p 'test_evaluate.py'` 통과 후 커밋한다.
- [ ] 결과표에는 질문·필수 근거별 첫 순위·정답 문단 후보 존재 여부·정답 인용문 포함 여부·원문 링크를 남긴다. 올바른 버전과 논문 ID도 확인한다. 순위 1~5는 적중, 6~20은 최종 순위 탈락, 0은 인용문 미발견이다. 0이어도 같은 문단이 있으면 경계 분할 여부를 원문과 대조한다.
- [ ] ③과 같은 방식으로 A와 B를 각각 색인하고 아래 명령을 각 앱에 실행한다. 출력 디렉터리는 매번 새 이름을 쓴다. 스크립트가 두 모드를 모두 측정하므로 청킹 선택에는 VECTOR 열을 사용한다.

```bash
python3 scripts/evaluate.py --base http://localhost:28089 \
  --gold evaluation/pilot-gold.json --repeat 5 --out "runs/$RUN/search"
```

**선택** — 필수 근거를 모두 맞힌 질문 수를 우선한다. 동점일 때 질문별 성공·실패까지 같으면 청크 수, DB 증가량, 전체 색인 시간을 순서대로 비교한다. 서로 다른 질문을 맞혀 우열이 갈리거나 시간만 근소하게 다르면 기본 256을 유지한다. 설정·분모·근거를 `runs/<실행명>/decision.md`에 적는다.

## ⑤ 팀원 진단 → 경태 수정 · 확인된 오답 원인 한 개만 변경

| 반복된 현상 | 한 번에 바꿀 대상 | 채택 기준 |
|---|---|---|
| 서로 다른 질문 2개 이상에서 근거가 청크 경계에 걸림 | 선택한 길이에만 12.5% 겹침을 실험한다. 128이면 직전 청크 끝의 본문 최대 16토큰, 256이면 최대 32토큰을 재사용한다. 토크나이저 오프셋으로 원문을 자르고 특수 토큰 포함 상한과 시작점 전진을 검사한다. | 새 DB에서 동일 10문항을 재실행해 최소 1문항 복구, 기존 정답 손실 0. 원문 범위 누락·토큰 초과 0 |
| 식별자가 원문에 있으나 후보에 없음 | `passages.sql`에서 키워드 목록 순위와 RRF 결합 후 순위를 같은 청크 ID로 대조한다. 색인·질의의 식별자 매칭부터 테스트한다. | 대상 근거가 후보 20개에 진입하고 기존 정답을 유지 |
| 상위 5개를 같은 문단의 청크가 차지함 | 후보 20개를 고정하고 문단별 최고 순위 청크를 먼저 선택하는 방식과 비교한다. | 대상 질문의 상위 5개에 필요한 근거 전체가 들어가고 기존 정답을 유지 |

- [ ] 팀원은 실패 질문·기대 인용문·현재 청크를 기록하고 경태는 이 사례로 실패 테스트부터 작성한다. 코드 변경 후 같은 테스트와 파일럿 평가를 다시 실행한다. 위 조건을 충족하지 않으면 현재 설정을 유지한다.
- [ ] 파일럿에서 VECTOR/HYBRID도 비교한다. HYBRID의 완전 정답이 순증 1개 이상이고 검색 p95가 VECTOR의 1.2배 이내일 때 HYBRID를 기본 후보로 선택한다. 선택한 청킹·겹침·검색 방식을 **⑥의 평가 전에** 고정한다.

## ⑥ 두 사람 · 누적 20편에서 처음 보는 질문으로 시연 판정

- [ ] 팀원은 기존 5편과 추가 15편을 `corpus/team-20/`에 모은다. ①의 inventory와 manifest 규칙을 적용한다. 새 논문 중 서로 다른 10편에서 질문을 하나씩 작성하고 두 사람이 답을 확인해 `evaluation/team-holdout.json`을 고정한다. 개발용과 최종 평가용에 같은 논문의 다른 버전이 섞이지 않도록 ID를 대조한다.
- [ ] 경태는 새 DB에 `team-20`을 접수하고 ③의 SQL에서 고유 논문 20개를 확인한다. 별도 테스트 DB에서 같은 문단 번호에 다른 내용이 있는 v1·v2를 넣어 상세 요청은 지정 버전, 전체 검색은 최신 색인 버전을 인용하는지 검사한다. `./gradlew test --tests '*IngestionWorkerTest'`를 사용한다.
- [ ] ④의 명령에서 gold만 최종 평가 파일로 교체한다. 질문·모드별 워밍업 1회는 제외하고 5회 반복한다. 방식별 **품질 10문항, 서버 검색 지연 50회**다. 동일 질문의 순위가 반복마다 달라지면 그 질문을 표시하고 단일 순위를 대표값으로 쓰지 않는다.
- [ ] 팀원은 `report.html`에서 두 방식의 질문별 순위와 원문 링크를 확인하고 `summary.csv`의 Recall@5·MRR@5·p95를 대조한다. 서버의 `elapsedMs`와 HTTP 왕복 시간은 구분한다.

**시연 통과** — 20/20편 적재, 잘못된 버전 인용 0건, 미리 선택한 방식이 새 질문 **8/10개 이상에서 필수 근거 전체를 상위 5개에 포함**. 실패하면 결과를 보존하고 수정은 개발셋에서 한다. 기존 평가 스크립트는 상세 검색에 고정돼 있다. 전체 코퍼스 검색 지연을 발표하려면 동일 질문에서 paperId·version을 생략해 별도로 측정하고 다른 표에 기록한다.

## ⑦ 경태 · 50편과 100편 중 다음 누적량 결정

- [ ] 유사한 파일 크기 분포를 전제로 20편 실측 HTML 총량·DB 증가량·벽시계 색인 시간에 각각 5를 곱해 **100편 한 설정**을 추정한다. 로그는 보관할 실행 횟수와 실제 로그 바이트를 반영한다. 비교용 DB를 두 개 유지하면 두 볼륨 모두 디스크 예산에 더한다. 큰 파일 3편의 처리 시간과 최대 RSS를 함께 적는다.
- [ ] 100편 한 설정의 예상 색인 시간이 **30분 이내**, 보관할 HTML·실험 DB·로그 합계가 **5 GiB 이내**, ③의 메모리 경고가 재현되지 않으면 100편으로 간다. 시간 또는 디스크 예산을 넘으면 **누적 50편**에서 다시 측정한다. 메모리 경고나 적재 실패가 반복되면 현재 편수에서 원인을 먼저 고친다.
- [ ] 두 사람은 최종 표에 고유 논문 수, HTML MiB, 총 문단·청크, DB 증가 MiB, 총 적재 시간, 관측 최대 RSS, 완전 정답 수/문항 수, Recall@5, 검색 p95를 남긴다. 운영 예산과 추정치는 실측 열과 구별한다.

**완료** — 다른 팀원이 같은 Git SHA·manifest 해시·모델·청킹 설정에서 같은 문단과 청크 수를 재현하고, 결과표의 각 수치를 실행 로그 또는 평가 파일에서 찾을 수 있다. 512/1024토큰 모델 비교와 JVM·GC 변경은 이 기준선을 확보한 뒤 별도 실험으로 진행한다.
