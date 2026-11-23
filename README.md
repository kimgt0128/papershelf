# PaperShelf

PaperShelf의 목표는 고성능 컴퓨팅(HPC) 논문에서 질문에 답하는 문단을 찾고, 해당 버전의 원문에서 근거를 확인하는 것이다. 예를 들어 “이 MPI 벤치마크는 몇 번 반복했는가?”라는 질문에는 실험 횟수와 함께 그 조건을 설명한 문단을 제시한다.

작업을 접수하면 워커가 로컬 HTML에서 문단을 추출해 PostgreSQL에 저장한다. 논문 ID·버전·문단 번호로 본문과 출처를 조회할 수 있다. MiniLM이 문단 청크를 384차원 벡터로 바꾸고, 검색 API가 상위 청크와 원문 문단을 함께 반환한다.

## 아키텍처

![논문 적재와 질문 검색의 구성](docs/diagrams/architecture.svg)

## 측정 지표

- 색인 처리량(편/s)은 검색 가능해진 고유 논문 수를 전체 적재 시간으로 나눈 값이다. 같은 논문 묶음에서 워커 수와 배치 크기를 각각 바꿔 비교한다.
- 색인 지연 p95(ms)는 논문별 접수부터 색인 공개 커밋까지 걸린 시간의 95백분위수다. 같은 적재 부하에서 비교하며 HTTP `202` 응답 시간은 따로 기록한다.
- 검색 지연 p95·p99(ms)는 질문 임베딩 시작부터 검색 순위 확정까지 걸린 시간의 95·99백분위수다. 같은 질문과 요청 빈도로 색인 병행 여부를 바꾸고, 느려진 구간을 GC 정지·락 대기·DB 연결 대기와 대조한다.
- 정답 문단 재현율(Recall@5, %)은 질문마다 전체 정답 근거 중 상위 5개 결과에 포함된 비율을 구해 평균한 값이다. 같은 정답셋에서 벡터 단독 검색과 벡터·키워드 결합 검색을 비교하고 검색 p95도 함께 기록한다.

첫 비교는 논문 20편과 사람이 근거 문단을 표시한 질문 10개로 시작한다. [전체 실험 설계](docs/hpc-rag-spring-implementation-plan.md)

## 로컬 실행

Java 21과 Docker Compose를 사용한다. 프로젝트 루트에서 실행한다. DB 볼륨을 다시 사용할 때는 처음 설정한 로컬 비밀번호를 사용한다.

```bash
export PAPERSHELF_DB_PASSWORD='choose-a-local-password'
docker compose up -d --wait
./scripts/setup-model.sh
./gradlew test
./gradlew bootRun
```

API는 기본 `http://localhost:8080`, DB는 `127.0.0.1:15433`에서 열린다. 서버 상태는 `GET /actuator/health`로 확인한다. 다른 클라이언트에서 호출하려면 접근 가능한 서버 주소를 사용한다. 현재 API는 로컬 개발용이며 인증을 구성하기 전에는 공개 인터넷에 노출하지 않는다.

## 적재 접수와 조회

예제 manifest에 등록된 원문을 먼저 준비한다. HTML은 로컬에만 보관한다.

```bash
curl -fL https://arxiv.org/html/2508.13397v1 -o corpus/demo-01/2508.13397v1.html
```

각 묶음은 `corpus/{shardId}/manifest.txt`에 버전이 붙은 arXiv ID를 한 줄에 하나씩 적고, 같은 디렉터리에 `{ID}.html`을 둔다. 현재는 숫자 형식의 arXiv ID를 지원한다. 같은 `runId`와 `shardId`로 재접수하면 같은 작업 ID를 반환한다.

```bash
curl -i http://localhost:8080/ingest \
  -H 'Content-Type: application/json' \
  -d '{"runId":"4c051714-1841-47c7-a265-81d0f8070da4","shardId":"demo-01"}'
```

응답 예시

```http
HTTP/1.1 202 Accepted
Location: /jobs/087b49aa-296c-45c1-a99a-b07ce86f1333
Content-Type: application/json

{"jobId":"087b49aa-296c-45c1-a99a-b07ce86f1333","runId":"4c051714-1841-47c7-a265-81d0f8070da4","shardId":"demo-01","status":"QUEUED"}
```

반환된 `Location`에서 `QUEUED → PROCESSING → COMPLETED` 상태를 조회한다. 실패하면 `FAILED`와 `failedStage`(manifest, read, parse, store, index)를 반환한다. 중간 실패 전에 저장한 논문은 조회할 수 있다. 입력을 고치고 새 `runId`로 접수하면 같은 원문은 중복 저장하지 않는다. 워커가 종료되면 5분 lease 만료 후 다른 워커가 작업을 이어받는다.

```bash
curl http://localhost:8080/papers/2508.13397/versions/1/paragraphs/47
```

예제 원문의 47번 문단은 §4.1에서 실험 반복 횟수와 오차 막대를 설명한다. 응답의 `sourceUri`로 원문을 연다. 없는 문단은 `404`다.

## 질문 검색과 측정

`POST /search`의 `mode`는 기본 `VECTOR` 또는 `HYBRID`다. 논문 상세 화면은 `paperId`와 `version`을 함께 보내고, 전체 검색에서는 둘 다 생략한다. 전체 검색은 논문별로 색인이 완료된 가장 높은 버전을 사용한다.

```bash
curl -s http://localhost:8080/search -H 'Content-Type: application/json' \
  -d '{"question":"How many times was each benchmark run?","paperId":"2508.13397","version":1,"mode":"HYBRID"}'
python3 scripts/evaluate.py --out runs/first-comparison
```

`hits`는 최종 5개, `candidates`는 최대 20개다. 각 항목의 `text`는 청크, `paragraph`는 같은 버전의 원문 문단, `sourceUri`는 인용 위치다. MiniLM 입력 한도는 특수 토큰을 포함해 256이다. 색인 도입 전에 완료한 작업은 새 `runId`로 접수하면 기존 문단에도 벡터가 생긴다.

측정 결과는 지정한 새 디렉터리의 `report.html`과 `summary.csv`에서 본다. `queries.jsonl`은 반복마다 실제 후보와 정답 근거 순위를 남긴다. `evidenceRanks`에서 0은 후보 20개 밖, 6~20은 최종 5개 밖이다. 기본 예제는 논문 한 편·질문 한 개이며, 팀이 작성한 정답셋은 `--gold`로 지정한다.

## 팀 코드 읽기

브랜치와 커밋 작성 예시는 [GitHub 협업 규칙](docs/github.md)에 있다.

1. [IngestionController](src/main/java/io/github/kimgt0128/papershelf/ingest/IngestionController.java)에서 두 HTTP 경로와 `ResponseEntity` 상태·헤더를 본다. [SubmitIngestRequest](src/main/java/io/github/kimgt0128/papershelf/ingest/SubmitIngestRequest.java)와 [IngestJob](src/main/java/io/github/kimgt0128/papershelf/ingest/IngestJob.java)은 요청·응답 필드를 정의하는 Java 21 `record`다.
2. [IngestJobStore](src/main/java/io/github/kimgt0128/papershelf/ingest/IngestJobStore.java)는 접수와 조회 SQL을 소유한다. DB 유일 제약과 `ON CONFLICT`가 중복 접수를 처리한다. 스키마는 [Flyway 마이그레이션](src/main/resources/db/migration/V1__create_ingest_jobs.sql)에 있다.
3. [StageLoggingAspect](src/main/java/io/github/kimgt0128/papershelf/logging/StageLoggingAspect.java)는 Spring 빈의 `@LogStage` 메서드가 호출되면 콘솔에 JSON 한 줄을 남긴다. 첫 적용 지점은 `IngestJobStore.submit()`이다. `message`는 `stage.completed` 또는 `stage.failed`이고, 공통 필드는 `stage`와 `elapsed_ms`다. 실패에는 `error_type`도 있다. 인자와 반환값은 기록하지 않는다.
4. [IngestionApiTest](src/test/java/io/github/kimgt0128/papershelf/ingest/IngestionApiTest.java)는 접수·조회·중복·오류 응답을 검증한다. [StageLoggingAspectTest](src/test/java/io/github/kimgt0128/papershelf/logging/StageLoggingAspectTest.java)는 로그를 남겨도 반환값과 예외가 호출자에게 그대로 전달되는지 확인한다.

`stage.completed`는 동기 메서드가 반환된 시점을 뜻한다. 향후 `paper.published`는 색인이 검색 가능해진 DB 커밋 이후에 별도 이벤트로 기록한다. 비동기 작업을 도입할 때는 워커에서 실제로 실행하는 단계에 `@LogStage`를 붙인다.
