# PaperShelf

HPC 논문을 저장하고 질문의 근거가 되는 문단을 찾는 Spring Boot 프로젝트다. 첫 버전에서는 외부 클라이언트가 HTTP로 논문 묶음의 적재를 접수하고 상태를 조회할 수 있다. 작업은 PostgreSQL에 `QUEUED`로 저장된다. 논문을 실제로 읽는 워커와 검색기는 [구현 계획](docs/hpc-rag-spring-implementation-plan.md)의 다음 단계다.

## 로컬 실행

Java 21과 Docker Compose를 사용한다. 프로젝트 루트에서 실행한다. DB 볼륨을 다시 사용할 때는 처음 설정한 로컬 비밀번호를 사용한다.

```bash
export PAPERSHELF_DB_PASSWORD='choose-a-local-password'
docker compose up -d --wait
./gradlew test
./gradlew bootRun
```

API는 기본 `http://localhost:8080`, DB는 `127.0.0.1:15433`에서 열린다. 서버 상태는 `GET /actuator/health`로 확인한다. 다른 클라이언트에서 호출하려면 접근 가능한 서버 주소를 사용한다. 현재 API는 로컬 개발용이며 인증을 구성하기 전에는 공개 인터넷에 노출하지 않는다.

## 적재 접수와 조회

아래 명령은 같은 `runId`에서 `demo-01` 묶음을 접수한다. `runId`는 UUID, `shardId`는 1~128자의 공백이 아닌 문자열이다. 같은 두 값을 다시 보내면 같은 `jobId`를 반환한다.

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

반환된 `Location`에 GET을 보내면 `200 OK`와 같은 형식의 JSON을 받는다. 잘못된 UUID나 빈 `shardId`는 `400`, 존재하지 않는 작업 ID는 `404`이며 오류 본문은 `application/problem+json`이다.

## 팀 코드 읽기

1. [IngestionController](src/main/java/io/github/kimgt0128/papershelf/ingest/IngestionController.java)에서 두 HTTP 경로와 `ResponseEntity` 상태·헤더를 본다. [SubmitIngestRequest](src/main/java/io/github/kimgt0128/papershelf/ingest/SubmitIngestRequest.java)와 [IngestJob](src/main/java/io/github/kimgt0128/papershelf/ingest/IngestJob.java)은 요청·응답 필드를 정의하는 Java 21 `record`다.
2. [IngestJobStore](src/main/java/io/github/kimgt0128/papershelf/ingest/IngestJobStore.java)는 접수와 조회 SQL을 소유한다. DB 유일 제약과 `ON CONFLICT`가 중복 접수를 처리한다. 스키마는 [Flyway 마이그레이션](src/main/resources/db/migration/V1__create_ingest_jobs.sql)에 있다.
3. [StageLoggingAspect](src/main/java/io/github/kimgt0128/papershelf/logging/StageLoggingAspect.java)는 Spring 빈의 `@LogStage` 메서드가 호출되면 콘솔에 JSON 한 줄을 남긴다. 첫 적용 지점은 `IngestJobStore.submit()`이다. `message`는 `stage.completed` 또는 `stage.failed`이고, 공통 필드는 `stage`와 `elapsed_ms`다. 실패에는 `error_type`도 있다. 인자와 반환값은 기록하지 않는다.
4. [IngestionApiTest](src/test/java/io/github/kimgt0128/papershelf/ingest/IngestionApiTest.java)는 접수·조회·중복·오류 응답을 검증한다. [StageLoggingAspectTest](src/test/java/io/github/kimgt0128/papershelf/logging/StageLoggingAspectTest.java)는 로그를 남겨도 반환값과 예외가 호출자에게 그대로 전달되는지 확인한다.

`stage.completed`는 동기 메서드가 반환된 시점을 뜻한다. 향후 `paper.published`는 색인이 검색 가능해진 DB 커밋 이후에 별도 이벤트로 기록한다. 비동기 작업을 도입할 때는 워커에서 실제로 실행하는 단계에 `@LogStage`를 붙인다.
