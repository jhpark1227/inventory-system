# 소모품 재고 관리 프로그램

마우스, 키보드, USB 등 소모품의 재고를 엑셀로 관리하는 상황을 가정해, 재고를 **조회**하고 **입고/출고로 수량을 변경**할 수 있게 만든 웹 프로그램입니다. AI 활용 능력 테스트 과제로 만들었습니다.

- 엑셀 파일(`.xlsx`)을 첨부해 불러오고, 편집한 뒤 수정된 엑셀 파일을 내려받습니다.
- 서버에는 DB나 파일 저장소가 없고, 데이터는 사용자 세션의 메모리에만 있습니다.
- 처음 접속하면 예시 데이터(무선마우스 30, 키보드 20, USB 메모리 50)로 바로 사용해 볼 수 있습니다.

## 실행 방법

### 준비물
- JDK 25 (Gradle toolchain이 `25`를 요구합니다)
- 별도 설치가 필요한 DB는 없습니다. Gradle은 포함된 `./gradlew`를 사용합니다.

### 실행

```bash
./gradlew bootRun
```

브라우저에서 <http://localhost:8080> 으로 접속합니다.

8080 포트를 이미 쓰고 있으면 다른 포트로 실행합니다.

```bash
./gradlew bootRun --args='--server.port=18080'
```

### 테스트

```bash
./gradlew test
```

## 사용 방법

| 동작 | 설명 |
|------|------|
| 재고 조회 | 접속하면 품목ID, 품목명, 현재 수량, 최종 수정일시가 표시됩니다. |
| 입고 / 출고 | 품목 행의 수량 칸에 숫자를 입력하고 [입고] 또는 [출고]를 누릅니다. 변경은 **작업본**에만 반영되고, 변경된 행은 강조되며 `20 → 25`처럼 표시됩니다. |
| 저장 | [저장]을 누르면 작업본의 변경이 서버 메모리의 **저장본**으로 확정되고 변경된 품목의 최종 수정일시가 갱신됩니다. |
| 되돌리기 | 저장하지 않은 변경을 모두 버리고 마지막 저장 상태로 돌아갑니다. |
| 다운로드 | 저장본을 반영한 `.xlsx` 파일을 내려받습니다. 저장하지 않은 변경은 포함되지 않습니다. |
| 엑셀 첨부 | 가지고 있는 `.xlsx` 파일을 선택해 업로드하면 그 내용으로 재고 데이터가 바뀝니다. |

일반적인 흐름: (필요하면 엑셀 첨부) → 입고/출고 → 저장 → 다운로드

### 수량 규칙
- 0 이상 `999,999,999,999,999`(15자리) 이하의 정수만 입력할 수 있습니다. 엑셀의 숫자 정밀도(15자리)와 자바스크립트의 안전 정수 중 더 낮은 쪽을 기준으로 했습니다.
- 재고보다 많이 출고할 수 없고(음수 재고 불가), 입고 후 수량이 상한을 넘을 수 없습니다.

### 첨부할 수 있는 엑셀 파일 형식
- `.xlsx`, 5MB 이하, 데이터 행 1~1,000개
- `재고` 시트를 사용하고, 없으면 첫 번째 시트를 사용합니다.
- 1행은 헤더이며 아래 열 순서와 이름을 지켜야 합니다.

| 열 | 헤더 | 필수 | 규칙 |
|----|------|------|------|
| A | 품목ID | O | 1 이상의 정수, 중복 불가 |
| B | 품목명 | O | 공백만 있는 값 불가 |
| C | 현재 수량 | O | 0 ~ 999,999,999,999,999의 정수 (소수, 음수, 문자, 수식 불가) |
| D | 최종 수정일시 | 선택 | 비어 있거나 날짜/시간 값 |

파일에 오류가 하나라도 있으면 아무것도 바뀌지 않고, 오류가 난 행 번호와 이유(최대 20건)를 알려줍니다. 예시 파일은 [`src/main/resources/sample/inventory.xlsx`](src/main/resources/sample/inventory.xlsx)에 있습니다.

### 알아둘 점
- 데이터는 **세션 단위 메모리**에 있습니다. 브라우저를 새로고침해도 유지되지만, 서버를 재시작하거나 세션이 만료(8시간)되면 사라지고 예시 데이터로 다시 시작합니다. 화면의 "현재 파일" 이름으로 알 수 있습니다.
- 저장하지 않은 변경이나 내려받지 않은 저장이 있으면 화면에 표시되고, 페이지를 떠날 때 경고합니다.
- 다운로드할 때는 첨부한 원본 파일을 기반으로 수량과 최종 수정일시만 갱신하므로, 사용자가 만든 서식, 다른 열, 다른 시트가 유지됩니다.

## API

JSON을 주고받는 REST API이며 세션 단위로 동작합니다. 상세 명세는 [docs/api-design.md](docs/api-design.md)를 참고하세요.

| 기능 | 메서드 | 경로 |
|------|--------|------|
| 재고 조회 | GET | `/api/items` |
| 입고 | POST | `/api/items/{id}/inbound` |
| 출고 | POST | `/api/items/{id}/outbound` |
| 저장 | POST | `/api/items/save` |
| 되돌리기 | POST | `/api/items/discard` |
| 엑셀 첨부 | POST | `/api/items/upload` (`multipart/form-data`, 필드 `file`) |
| 엑셀 다운로드 | GET | `/api/items/download` |

```bash
# 키보드(품목ID 2) 5개 입고
curl -c cookie.txt -b cookie.txt -H 'Content-Type: application/json' \
  -d '{"quantity": 5}' http://localhost:8080/api/items/2/inbound
```

오류 응답은 `{ "message": "..." }` 형태이고, 상태 코드는 사용자 요청 오류 `400`과 서버 오류 `500` 두 가지만 씁니다. 세션 쿠키(`-c`, `-b`)를 함께 보내야 같은 데이터를 이어서 사용할 수 있습니다.

## 프로젝트 구조

```
src/main/java/com/inventorysystem
├── domain      Item(저장본/작업본 수량), Quantity(수량 규칙 상수)
├── dto         API 요청/응답
├── excel       ExcelItemParser(검증·변환), ExcelExporter(원본 서식 유지 내보내기)
├── exception   InvalidRequestException(400), ExportException(500)
├── service     InventorySession(세션 메모리), ItemService, SampleDataLoader
└── web         PageController(화면), ItemApiController(API), ApiExceptionHandler
src/main/resources
├── templates/index.html      데이터 없는 화면 골격 (Thymeleaf)
├── static/js, static/css     화면 동작과 스타일 (API를 호출해 그리는 CSR)
└── sample/inventory.xlsx     예시 데이터
docs                          요구사항, 설계, 테스트 전략 문서
```

기술 스택: Java 25, Spring Boot 4.1.2 (Web MVC, Thymeleaf), Apache POI, JUnit 5 / AssertJ / MockMvc

## 테스트

테스트 전략은 [docs/test-strategy.md](docs/test-strategy.md)에 정리했고, 핵심 기능만 두 종류로 나눠 검증합니다.

- **도메인 단위 테스트** (스프링 없이 실행): `Item`, `Quantity`, 수량·저장·되돌리기 규칙, 엑셀 파싱/내보내기
- **통합 테스트** (`@SpringBootTest` + `MockMvc`): 조회 → 입고/출고 → 저장 → 되돌리기, 오류 응답, 첨부 → 저장 → 다운로드, 잘못된 엑셀 첨부

화면(HTML/JavaScript)은 자동 테스트 대신 직접 확인합니다.

## 문서

| 문서 | 내용 |
|------|------|
| [docs/assignment.md](docs/assignment.md) | 과제 정의 |
| [docs/functional-requirements.md](docs/functional-requirements.md) | 기능 요구사항 개요 |
| [docs/functional-requirements-detail.md](docs/functional-requirements-detail.md) | 확정된 세부 요구사항 |
| [docs/design.md](docs/design.md) | 전체 설계 |
| [docs/api-design.md](docs/api-design.md) | API 명세 |
| [docs/ui-design.md](docs/ui-design.md) | 화면 설계 |
| [docs/test-strategy.md](docs/test-strategy.md) | 테스트 전략 |

## AI 활용 내용

이 프로젝트는 AI 코딩 도구(Claude Code)와 대화하며 **문서를 먼저 만들고, 문서를 근거로 구현**하는 순서로 진행했습니다.

1. **요구사항 구체화**: 과제 원문을 읽고, 정해야 할 항목(표시 항목, 검증 규칙, 저장 방식, 동시성 등)을 AI가 질문과 추천안으로 정리했고 사람이 하나씩 결정했습니다. 결정된 내용은 `docs/`의 요구사항 문서에 남겼습니다.
2. **기술 검토**: 엑셀 파일을 직접 수정할 수 있는지 Apache POI로 작은 시험 코드를 돌려 확인했고, 동시 수정과 저장 방식(서버 파일 수정 vs. 업로드/다운로드)의 장단점을 비교한 뒤 방향을 정했습니다.
3. **설계 문서 작성과 수정**: 요구사항이 바뀔 때마다(즉시 반영 → 저장 버튼, 엑셀 첨부, 서버 파일 대신 다운로드, 오류 응답 단순화) 기능 요구사항, API, 화면, 전체 설계 문서를 함께 갱신했습니다.
4. **구현과 테스트**: 문서와 테스트 전략을 근거로 REST API, 엑셀 처리, 화면을 구현하고 테스트를 작성했습니다. 구현 결과가 문서와 달라지면 문서도 같이 고쳤습니다.
5. **검증**: 자동 테스트를 실행하고, 실제 서버를 띄워 `curl`과 브라우저로 동작을 확인했으며, 커밋 전에 요구사항과 어긋나는 부분이 없는지 한 번 더 리뷰했습니다.
6. **작업 지침**: [AGENTS.md](AGENTS.md)에 "코드 작성 전에 `docs/`의 관련 문서를 먼저 읽는다"는 규칙을 두어, AI가 문서를 기준으로 작업하도록 했습니다.

모든 설계 결정과 요구사항 변경은 사람이 확인하고 확정했으며, AI는 초안 작성, 구현, 검증을 맡았습니다.
