# AGENTS.md

## 작업 진행 방식

사용자의 요청을 받으면 코드를 작성하거나 수정하기 전에 `/docs` 폴더에서 요청에 적합한 문서를 먼저 찾아 읽고, 그 내용을 근거로 작업을 진행한다.

### 문서 선택 가이드

| 요청 유형 | 참고 문서 |
| --- | --- |
| 과제 목적, 범위, 제출 조건 | `docs/assignment.md` |
| 기능 요구사항 개요 | `docs/functional-requirements.md` |
| 확정된 세부 요구사항, 정책, 예외 처리 | `docs/functional-requirements-detail.md` |
| 전체 구조, 도메인/데이터 모델, 아키텍처 | `docs/design.md` |
| API 엔드포인트, 요청/응답 명세 | `docs/api-design.md` |
| 화면 구성, UI 흐름 | `docs/ui-design.md` |
| 테스트 작성, 검증 방식 | `docs/test-strategy.md` |

### 규칙

1. 요청과 관련된 문서를 모두 찾아 읽는다. 한 요청이 여러 문서에 걸칠 수 있다.
2. 각 문서 상단의 상위 문서·근거 문서 링크를 따라가며 필요한 내용을 추가로 확인한다.
3. 문서 간 내용이 충돌하면 `functional-requirements-detail.md`(확정 사항)를 우선한다.
4. 문서에 없거나 모호한 내용은 임의로 가정하지 말고 사용자에게 확인한다.
5. 구현이 문서와 달라지는 경우, 코드와 함께 해당 문서도 갱신하거나 그 사실을 사용자에게 알린다.
