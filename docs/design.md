# 설계 문서: 소모품 재고 관리 프로그램

> 근거 문서: [assignment.md](assignment.md), [functional-requirements.md](functional-requirements.md), [functional-requirements-detail.md](functional-requirements-detail.md)
> 세부 설계: [api-design.md](api-design.md), [ui-design.md](ui-design.md)
> 테스트: [test-strategy.md](test-strategy.md)

## 1. 설계 목표

- 사용자가 엑셀 파일을 **첨부**해 서버 메모리로 불러오고, 편집한 뒤 수정된 엑셀 파일을 **다운로드**한다. 서버는 파일을 쓰지 않는다 (DB, 서버 파일 저장소 없음).
- 화면(Thymeleaf)은 껍데기만 렌더링하고, 데이터는 REST API로 받아 브라우저에서 그린다 (CSR).
- 입고/출고는 **작업본**에만 반영하고, **[저장]** 으로 메모리의 **저장본**에 확정한다. 다운로드는 저장본을 파일로 만든다.
- 데이터는 HTTP 세션 단위로 메모리에 두며, 동시 요청 제어는 하지 않는다.

## 2. 기술 스택

| 구분 | 선택 |
|------|------|
| 언어/빌드 | Java 25, Gradle |
| 프레임워크 | Spring Boot 4.1.2 (`webmvc`, `thymeleaf`) |
| 엑셀 처리 | Apache POI (`poi-ooxml`) |
| 프론트엔드 | Thymeleaf 템플릿 1개 + 순수 JavaScript(`fetch`) + CSS |
| 기타 | Lombok |

**의존성 변경**: 현재 `build.gradle`의 `spring-boot-h2console`, `h2`는 제거하고 `org.apache.poi:poi-ooxml`을 추가한다.

## 3. 전체 구조

```
브라우저 (index.html + app.js)
   │  GET /                  → 화면 껍데기
   │  GET/POST /api/items…   → JSON
   │  POST /api/items/upload → 엑셀 파일 전송
   │  GET /api/items/download→ 엑셀 파일 수신
   ▼
Controller (PageController, ItemApiController)
   ▼
Service (ItemService)          ← 업무 규칙(수량 검증, 재고 부족, 상한)
   ▼
InventorySession (세션 메모리)  ← 원본 파일 바이트, 파일명, 저장본/작업본, 상태 플래그
   ▲                 ▲
ExcelItemParser   ExcelExporter   ← 엑셀 ↔ 품목 변환 (순수 함수, 파일 I/O 없음)
```

### 3.1 패키지 구성 (`com.inventorysystem`)

| 패키지 | 클래스 | 역할 |
|--------|--------|------|
| `web` | `PageController` | `GET /` 에서 `index` 템플릿 반환 |
| `web` | `ItemApiController` | 조회/입고/출고/저장/되돌리기/첨부/다운로드 API |
| `web` | `ApiExceptionHandler` | 예외를 JSON 오류 응답(`message`만)으로 변환: 사용자 요청 오류는 400, 그 외 예기치 못한 오류는 500 (파일 크기 초과는 400) |
| `service` | `ItemService` | 업무 규칙 처리, 각 기능의 흐름 조율 |
| `service` | `InventorySession` | `@SessionScope` 빈. 원본 파일 바이트, 파일명, 품목별 저장본/작업본 수량, 최종 수정일시, `unexported` 플래그를 보관하고 `dirty`, `changedCount`를 계산한다 |
| `service` | `SampleDataLoader` | 새 세션이 시작될 때 `sample/inventory.xlsx`를 읽어 초기 데이터를 만든다 |
| `excel` | `ExcelItemParser` | 엑셀 바이트를 읽어 형식 규칙을 검증하고 품목 목록(행 번호 포함)으로 변환한다. 첨부와 예시 데이터 로딩이 같은 규칙을 쓴다 |
| `excel` | `ExcelExporter` | 원본 바이트와 품목의 저장본 값을 받아 수량과 최종 수정일시를 갱신한 새 엑셀 바이트를 만든다 |
| `domain` | `Item`, `Quantity`(수량 상수) | 품목ID, 품목명, 저장본 수량, 작업본 수량, 최종 수정일시, 원본 행 번호 |
| `dto` | `ItemResponse`, `ItemListResponse`, `ChangeResponse`, `SaveResponse`, `ImportResponse`, `StockChangeRequest`, `ErrorResponse`(`message`만) | API 입출력 |
| `exception` | `InvalidRequestException`(사용자 요청 오류, 400), `ExportException`(다운로드 파일 생성 실패, 500) | 업무 예외 |

`ExcelItemParser`와 `ExcelExporter`는 바이트 입출력만 하는 순수 변환 클래스라서, 파일 시스템 없이도 단위 테스트할 수 있다. 서버에 저장소 계층(Repository)은 두지 않는다.

## 4. 데이터 설계

### 4.1 엑셀 파일 형식

- 시트: `재고` (없으면 첫 번째 시트), 1행은 헤더

| 열 | 헤더 | 타입 | 설명 |
|----|------|------|------|
| A | 품목ID | 숫자 | 1 이상의 정수, 고유 |
| B | 품목명 | 문자 | |
| C | 현재 수량 | 숫자 | 0 ~ 999,999,999,999,999 |
| D | 최종 수정일시 | 날짜/시간 | 선택. `yyyy-MM-dd HH:mm:ss` 서식 |

세부 검증 규칙은 [functional-requirements-detail.md](functional-requirements-detail.md) 4.1을 따른다.

### 4.2 초기 데이터

| 품목ID | 품목명 | 현재 수량 |
|--------|--------|-----------|
| 1 | 무선마우스 | 30 |
| 2 | 키보드 | 20 |
| 3 | USB 메모리 | 50 |

- 예시 데이터가 담긴 `src/main/resources/sample/inventory.xlsx`를 만들어 저장소에 포함한다.
- 새 세션이 시작되면 `SampleDataLoader`가 이 파일을 읽어 세션 메모리에 올린다. 파일명은 `inventory.xlsx`로 표시한다.
- 정렬은 파일의 행 순서(등록순) 그대로이며 다시 정렬하지 않는다.

### 4.3 도메인 규칙 상수

`MIN_QUANTITY` = 0, `MAX_QUANTITY` = 999,999,999,999,999 (규칙 상세는 [api-design.md](api-design.md))

## 5. API 설계

상세는 [api-design.md](api-design.md)를 참고한다.

| 기능 | 메서드 | 경로 |
|------|--------|------|
| 재고 조회 | GET | `/api/items` |
| 입고 (작업본) | POST | `/api/items/{id}/inbound` |
| 출고 (작업본) | POST | `/api/items/{id}/outbound` |
| 저장 (작업본 → 저장본) | POST | `/api/items/save` |
| 되돌리기 (저장본 → 작업본) | POST | `/api/items/discard` |
| 엑셀 첨부 | POST | `/api/items/upload` |
| 엑셀 다운로드 | GET | `/api/items/download` |

## 6. 세션 메모리와 처리 흐름

### 6.1 세션 메모리 (`InventorySession`)
- HTTP 세션마다 하나. 세션이 처음 쓰일 때 `SampleDataLoader`로 초기화한다.
- 보관 항목: 원본 파일 바이트, 파일명, 품목 목록(품목ID, 품목명, 저장본 수량, 작업본 수량, 최종 수정일시, 원본 행 번호), `unexported`.
- `dirty`는 작업본 ≠ 저장본인 품목이 있는지로 계산한다.
- 세션 만료 시간은 8시간(`server.servlet.session.timeout`)이다. 서버를 재시작하거나 세션이 만료되면 데이터는 사라지고 예시 데이터로 다시 시작한다.
- 세션마다 독립이라 사용자 사이에 데이터가 섞이지 않는다. 한 세션 안의 동시 요청은 제어하지 않는다.

### 6.2 입고/출고 흐름
1. 품목을 찾는다 (없으면 400).
2. 작업본 수량을 기준으로 수량 규칙을 검증한다 ([api-design.md](api-design.md) 참고).
3. 작업본 수량을 바꾼다. 저장본은 건드리지 않는다.
4. 변경된 품목과 변경 상태(`dirty`, `changedCount`, `unexported`)를 응답한다.

### 6.3 저장 흐름
1. 변경된 품목이 없으면 아무것도 바꾸지 않고 `savedCount: 0`으로 응답한다.
2. 변경된 품목의 저장본 수량을 작업본 수량으로 바꾸고 최종 수정일시를 저장 시각으로 갱신한다.
3. 1건 이상 저장하면 `unexported`를 `true`로 한다.
4. 전체 목록과 저장 건수를 응답한다.

파일 입출력이 없으므로 저장은 실패하지 않는다.

### 6.4 되돌리기 흐름
모든 품목의 작업본 수량을 저장본 수량으로 되돌린다. `unexported`는 그대로 둔다.

### 6.5 엑셀 첨부 흐름
1. 업로드된 파일을 메모리로 읽고 크기와 확장자(`.xlsx`)를 확인한다.
2. `ExcelItemParser`로 헤더와 모든 행을 검증하고 품목 목록으로 변환한다. 오류가 있으면 행별 이유(최대 20건)를 메시지에 담아 400으로 종료한다. 이 단계까지는 세션 데이터를 바꾸지 않는다.
3. 세션의 원본 바이트, 파일명, 품목 목록을 새 값으로 통째로 교체한다. 저장본과 작업본은 파일 내용으로 같게 두고, `unexported`는 `false`로 한다.
4. 불러온 품목 수와 전체 목록을 응답한다.

- 파일 업로드 크기는 `spring.servlet.multipart.max-file-size`/`max-request-size`로 5MB에 제한한다.
- 파일을 디스크에 저장하지 않는다.

### 6.6 엑셀 다운로드 흐름
1. 세션의 원본 바이트를 POI로 연다.
2. 각 품목의 원본 행 번호를 찾아 C열에 **저장본** 수량을, D열에 최종 수정일시를 쓴다 (값이 없는 품목은 D열을 비워 둔다). D열이 없으면 헤더를 추가한다. 셀 서식은 원본 셀의 것을 유지하고, D열의 새 셀에는 `yyyy-MM-dd HH:mm:ss` 서식을 준다.
3. 결과 바이트를 `{원본 파일명}_수정_yyyyMMdd_HHmmss.xlsx` 이름으로 응답한다.
4. 응답이 성공하면 `unexported`를 `false`로 한다. 실패하면 500으로 응답하고 상태를 바꾸지 않는다.

- 작업본(저장하지 않은 변경)은 파일에 포함되지 않는다.
- 다운로드는 저장본과 작업본을 바꾸지 않는다.

## 7. 화면 설계

상세는 [ui-design.md](ui-design.md)를 참고한다.

- 단일 페이지 `/`에서 조회, 변경, 저장, 되돌리기, 엑셀 첨부, 엑셀 다운로드를 모두 처리한다.
- Thymeleaf는 골격만 렌더링하고 데이터는 API 호출로 그린다 (CSR).

## 8. 설정

```yaml
spring:
  application:
    name: inventory-system
  servlet:
    multipart:
      max-file-size: 5MB
      max-request-size: 5MB
server:
  servlet:
    session:
      timeout: 8h
```

예시 데이터 파일은 클래스패스 `sample/inventory.xlsx`로, 최대 데이터 행 수(1,000)는 `ExcelItemParser`의 상수로 고정한다 (설정값으로 두지 않는다).

## 9. 구현 순서 (제안)

1. `build.gradle` 의존성 정리 (H2 제거, POI 추가), `application.yaml` 설정
2. 도메인, 예외, DTO
3. `ExcelItemParser`, `ExcelExporter`
4. 예시 데이터 `sample/inventory.xlsx` 생성, `SampleDataLoader`
5. `InventorySession`, `ItemService`
6. API 컨트롤러 (조회/입고/출고/저장/되돌리기/첨부/다운로드), 예외 핸들러
7. 화면 (`index.html`, `app.js`, `style.css`)
8. README 작성
9. 테스트 (별도 테스트 문서 기준)
