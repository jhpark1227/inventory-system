# API 설계 문서

> 상위 문서: [design.md](design.md) · 화면 설계: [ui-design.md](ui-design.md)
> 근거: [functional-requirements-detail.md](functional-requirements-detail.md)

공통: 요청/응답은 JSON (다운로드 제외), 문자 인코딩 UTF-8. 인증은 없다.
모든 API는 **HTTP 세션 단위**로 동작한다. 세션마다 자기 데이터(저장본, 작업본)를 서버 메모리에 가진다.

## 1. 개념: 저장본과 작업본

| 용어 | 설명 |
|------|------|
| 저장본 | [저장]으로 확정된 값. 다운로드 파일에 기록되는 값 |
| 작업본 | 입고/출고가 반영된 편집 중인 값 |
| 변경된 품목 | 작업본 수량 ≠ 저장본 수량인 품목 |
| `dirty` | 변경된 품목이 하나라도 있음 (저장하지 않은 변경) |
| `unexported` | 저장은 했지만 마지막 다운로드(또는 첨부/시작) 이후 내려받지 않음 |

- 새 세션은 프로젝트에 포함된 예시 데이터(`sample/inventory.xlsx`)로 시작한다.
- **서버는 파일을 쓰지 않는다.** 파일은 다운로드 API가 요청 시점에 만들어 내려줄 뿐이다.

## 2. API 목록

| 기능 | 메서드 | 경로 | 바꾸는 대상 |
|------|--------|------|-------------|
| 재고 조회 | GET | `/api/items` | 없음 |
| 입고 | POST | `/api/items/{id}/inbound` | 작업본 |
| 출고 | POST | `/api/items/{id}/outbound` | 작업본 |
| 저장 | POST | `/api/items/save` | 작업본 → 저장본 |
| 되돌리기 | POST | `/api/items/discard` | 작업본 ← 저장본 |
| 엑셀 첨부 | POST | `/api/items/upload` | 저장본, 작업본 전체 |
| 엑셀 다운로드 | GET | `/api/items/download` | 없음 (파일 생성만) |

## 3. 공통 응답 형식

### 3.1 품목 (`ItemResponse`)

| 필드 | 타입 | 설명 |
|------|------|------|
| `id` | 숫자 | 품목ID |
| `name` | 문자열 | 품목명 |
| `quantity` | 숫자 | 작업본의 현재 수량 (0 ~ 999,999,999,999,999) |
| `savedQuantity` | 숫자 | 저장본의 수량 |
| `changed` | 불리언 | `quantity`가 `savedQuantity`와 다르면 `true` |
| `updatedAt` | 문자열 또는 `null` | 마지막으로 **저장된** 일시 (ISO 8601, 시간대 없음). 첨부한 파일에 값이 없으면 `null` |

### 3.2 목록 (`ItemListResponse`)

| 필드 | 타입 | 설명 |
|------|------|------|
| `fileName` | 문자열 | 현재 사용 중인 파일명 (예시 데이터는 `inventory.xlsx`) |
| `dirty` | 불리언 | 저장하지 않은 변경이 있는지 |
| `changedCount` | 숫자 | 변경된 품목 수 |
| `unexported` | 불리언 | 내려받지 않은 저장이 있는지 |
| `items` | 배열 | `ItemResponse` 목록 (등록순, 파일의 행 순서) |

## 4. 재고 조회

`GET /api/items`

`200 OK`

```json
{
  "fileName": "inventory.xlsx",
  "dirty": true,
  "changedCount": 1,
  "unexported": false,
  "items": [
    { "id": 1, "name": "무선마우스", "quantity": 30, "savedQuantity": 30, "changed": false, "updatedAt": "2026-11-11T11:30:00" },
    { "id": 2, "name": "키보드",     "quantity": 25, "savedQuantity": 20, "changed": true,  "updatedAt": "2026-11-11T11:30:00" },
    { "id": 3, "name": "USB 메모리", "quantity": 50, "savedQuantity": 50, "changed": false, "updatedAt": "2026-11-11T11:30:00" }
  ]
}
```

## 5. 입고 / 출고 (작업본 변경)

`POST /api/items/{id}/inbound`
`POST /api/items/{id}/outbound`

요청

```json
{ "quantity": 5 }
```

`200 OK` – 변경된 품목과 전체 변경 상태

```json
{
  "item": { "id": 2, "name": "키보드", "quantity": 25, "savedQuantity": 20, "changed": true, "updatedAt": "2026-11-11T11:30:00" },
  "dirty": true,
  "changedCount": 1,
  "unexported": false
}
```

- 작업본만 바뀐다. 저장본은 바뀌지 않는다.
- 같은 품목에 여러 번 입고/출고하면 작업본에 누적된다. 누적 결과가 `savedQuantity`와 같아지면 `changed`는 `false`가 된다.

## 6. 저장

`POST /api/items/save`

요청 본문 없음.

`200 OK` – 저장 후의 전체 목록 (`ItemListResponse`) + 저장 건수

```json
{
  "savedCount": 1,
  "fileName": "inventory.xlsx",
  "dirty": false,
  "changedCount": 0,
  "unexported": true,
  "items": [
    { "id": 1, "name": "무선마우스", "quantity": 30, "savedQuantity": 30, "changed": false, "updatedAt": "2026-11-11T11:30:00" },
    { "id": 2, "name": "키보드",     "quantity": 25, "savedQuantity": 25, "changed": false, "updatedAt": "2026-11-11T15:20:41" },
    { "id": 3, "name": "USB 메모리", "quantity": 50, "savedQuantity": 50, "changed": false, "updatedAt": "2026-11-11T11:30:00" }
  ]
}
```

처리 규칙

1. 변경된 품목이 없으면 아무것도 바꾸지 않고 `savedCount: 0`으로 응답한다. `unexported`도 바꾸지 않는다.
2. 변경된 품목의 저장본 수량을 작업본 수량으로 바꾸고, `updatedAt`을 저장 시각으로 갱신한다.
3. 1건 이상 저장하면 `unexported`를 `true`로 한다.
4. 파일 입출력이 없으므로 저장은 실패하지 않는다.

## 7. 되돌리기

`POST /api/items/discard`

요청 본문 없음.

`200 OK` – 작업본을 마지막 저장 상태로 복원한 목록 (`ItemListResponse`, `dirty: false`). `unexported`는 그대로 유지한다.

## 8. 엑셀 첨부

`POST /api/items/upload`

요청: `multipart/form-data`

| 필드 | 타입 | 설명 |
|------|------|------|
| `file` | 파일 | `.xlsx` 파일 1개, 5MB 이하 |

`200 OK` – 불러온 뒤의 전체 목록 (`ItemListResponse`) + 불러온 품목 수

```json
{
  "importedCount": 3,
  "fileName": "재고현황.xlsx",
  "dirty": false,
  "changedCount": 0,
  "unexported": false,
  "items": [
    { "id": 1, "name": "무선마우스", "quantity": 30, "savedQuantity": 30, "changed": false, "updatedAt": null },
    { "id": 2, "name": "키보드",     "quantity": 20, "savedQuantity": 20, "changed": false, "updatedAt": null },
    { "id": 3, "name": "USB 메모리", "quantity": 50, "savedQuantity": 50, "changed": false, "updatedAt": null }
  ]
}
```

처리 규칙

1. 업로드된 파일이 없거나, 확장자가 `.xlsx`가 아니거나, 엑셀로 열 수 없으면 400으로 거부한다.
2. 파일을 메모리에서 읽어 **전체를 검증**한다. 형식 규칙은 [functional-requirements-detail.md](functional-requirements-detail.md) 4.1을 따른다.
   - 헤더: A~C열이 `품목ID`, `품목명`, `현재 수량`이어야 한다 (D열 `최종 수정일시`는 선택).
   - 행: 품목ID는 1 이상의 정수이며 중복이 없고, 품목명은 비어 있지 않으며, 수량은 0 ~ `MAX_QUANTITY`의 정수(수식 불가)여야 한다.
   - 데이터 행은 1개 이상 1,000개 이하이다. 완전히 빈 행은 건너뛴다.
3. 오류가 하나라도 있으면 400으로 응답하고(`message`에 행별 이유를 최대 20건 담는다) **아무것도 바꾸지 않는다**.
4. 검증을 통과하면 세션의 데이터를 통째로 교체한다.
   - 원본 파일 바이트와 파일명을 세션에 보관한다 (다운로드 때 사용).
   - 저장본과 작업본을 파일 내용으로 만든다. `dirty`, `unexported`는 `false`가 된다.
5. 이전 데이터는 복구할 수 없다. 확인 절차는 화면에서 처리한다.

## 9. 엑셀 다운로드

`GET /api/items/download`

`200 OK` – 파일 내려받기 (JSON이 아님)

| 헤더 | 값 |
|------|----|
| `Content-Type` | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` |
| `Content-Disposition` | `attachment; filename*=UTF-8''{인코딩된 파일명}` |

- 파일명: `{원본 파일명}_수정_yyyyMMdd_HHmmss.xlsx`
- 내용: 세션에 보관한 원본 파일을 열어, 각 품목의 **저장본** 수량(C열)과 최종 수정일시(D열)만 갱신한다. D열이 없으면 헤더와 함께 추가한다. 서식과 다른 열, 다른 시트는 유지된다.
- **작업본(저장하지 않은 변경)은 포함되지 않는다.**
- 다운로드가 성공하면 `unexported`를 `false`로 한다 (이후 `GET /api/items`로 확인). 메모리의 다른 데이터는 바뀌지 않는다.

## 10. 수량 처리 규칙

입고/출고 시 작업본 수량을 기준으로 검증한다.

| 구분 | 규칙 |
|------|------|
| 입력 | 정수만 허용 (소수, 문자열, 누락은 거부), 0 이상 `MAX_QUANTITY` 이하 |
| 입고 | 작업본 수량 + 입고 수량 > `MAX_QUANTITY` 이면 거부 |
| 출고 | 출고 수량 > 작업본 수량 이면 거부 (음수 재고 불가) |
| 수량 0 | 허용 (작업본이 그대로이므로 변경으로 보지 않는다) |

| 상수 | 값 |
|------|----|
| `MIN_QUANTITY` | 0 |
| `MAX_QUANTITY` | 999,999,999,999,999 |

## 11. 오류 응답

오류 응답은 **메시지만** 담는다. 오류 코드와 상세 목록 필드는 없다.
상태 코드는 **400(사용자 요청 오류)** 과 **500(서버 오류)** 두 가지로만 구분한다.

```json
{ "message": "재고가 부족합니다. 현재 재고는 12개입니다." }
```

화면은 HTTP 상태 코드로 성공/실패를 구분하고, `message`를 그대로 알림에 보여준다.
엑셀 첨부의 형식 오류는 오류가 난 행의 이유를 `message` 안에 줄바꿈(`\n`)으로 이어 담는다 (최대 20건).

```json
{ "message": "엑셀 파일의 내용이 올바르지 않아 불러오지 못했습니다.\n3행: 현재 수량은 0 이상의 정수여야 합니다.\n5행: 품목ID 2가 중복되었습니다." }
```

| HTTP | 구분 | 발생 조건 | 발생 API |
|------|------|-----------|----------|
| 400 | 사용자 요청 오류 | 수량이 정수가 아니거나 범위 밖, 누락 | 입고, 출고 |
| 400 | 사용자 요청 오류 | 존재하지 않는 품목ID | 입고, 출고 |
| 400 | 사용자 요청 오류 | 출고 수량이 작업본 수량보다 큼 | 출고 |
| 400 | 사용자 요청 오류 | 입고 후 수량이 상한 초과 | 입고 |
| 400 | 사용자 요청 오류 | 파일 누락, `.xlsx`가 아님, 열 수 없음, 헤더나 행 내용 오류 | 엑셀 첨부 |
| 400 | 사용자 요청 오류 | 파일이 5MB 초과 | 엑셀 첨부 |
| 500 | 서버 오류 | 다운로드 파일 생성 실패 등 예기치 못한 오류 | 모든 API |

- 잘못된 JSON, 필수 값 누락, 없는 경로, 허용되지 않는 메서드처럼 스프링이 4xx로 분류하는 오류도 400으로 응답한다 (`message`는 "잘못된 요청입니다. 입력 내용을 확인해 주세요.").
- 오류가 나면 세션의 데이터(저장본, 작업본)는 바뀌지 않는다.
- 엑셀 첨부의 파일 크기 제한(5MB)은 서버 설정(`spring.servlet.multipart.max-file-size`, `max-request-size`)으로 두고, 초과 시 400으로 응답한다.
- 다운로드 오류는 JSON 오류 응답으로 내려가므로 화면은 파일을 `fetch`로 받아 상태 코드로 오류를 구분해야 한다.
- 400의 `message`는 사용자가 원인을 알고 고칠 수 있는 구체적인 한국어 문장이다.
- 500의 `message`는 내부 정보를 노출하지 않는 일반 문장이다 (예: "서버 오류가 발생했습니다. 잠시 후 다시 시도하세요.").
