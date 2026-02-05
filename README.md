# RECO 2026 ICT Internship: OCR Text Parser

본 프로젝트는 폐기물 계량 증명서 및 영수증의 OCR 결과물(JSON)을 분석하여 핵심 데이터를 추출하고, 데이터의 정합성을 검증하여 구조화된 형태(DB/JSON/CSV)로 저장하는 시스템입니다.

---

## 1. 실행 방법 (Local Execution)

### 환경 준비
* **Java 17** 설치 필수
* **Gradle 8.x** 이상 (프로젝트 내 Gradle Wrapper 포함)

### 빌드 및 실행
```bash
# 1. 저장소 복제
git clone [https://github.com/your-repo/ocr-parser.git](https://github.com/your-repo/ocr-parser.git)
cd ocr-parser

# 2. 프로젝트 빌드 및 테스트 실행
./gradlew clean build

# 3. 어플리케이션 실행
./gradlew bootRun
```
### 결과 확인
H2 Console: http://localhost:8080/h2-console (JDBC URL: jdbc:h2:mem:recodb, 별도 비밀번호 없음)

Output: 실행 결과 파일은 프로젝트 루트의 output/ 디렉토리에 .json 및 .csv 형태로 자동 생성됩니다.

## 2. API 사용 방법 (Postman 예시)
리뷰어의 편의를 위해 샘플 데이터를 즉시 파싱하고 조회할 수 있는 엔드포인트를 제공합니다.

### 1) 샘플 데이터 파싱 및 저장 (POST)
프로젝트 내부에 포함된 샘플 JSON 파일(sample_01 ~ 04)을 파싱하여 DB 및 파일로 저장합니다.
- URL: `POST localhost:8080/api/v1/weight-tickets/samples/{fileName}`
- Example: `POST localhost:8080/api/v1/weight-tickets/samples/sample_04.json`

### 2) 데이터 필터링 조회 (GET)
QueryDSL을 활용하여 조건별 동적 검색을 지원합니다.
- URL: `GET localhost:8080/api/v1/weight-tickets`
- Query Parameters (Optional):
    - `needsReview`: 검토 필요 여부 (`true`/`false`)
    - `carNumber`: 차량번호 부분 일치 검색
- Example (필터링): `GET localhost:8080/api/v1/weight-tickets?needsReview=false&carNumber=80`
- Example (전체조회): `GET localhost:8080/api/v1/weight-tickets`

## 3. 의존성 및 개발 환경
- Framework: Spring Boot 4.0.2
- Language: Java 17 (Toolchain 적용)
- Build Tool: Gradle
- Database: H2 (In-memory)
- ORM & Query: Spring Data JPA, QueryDSL 5.1.0 (Type-safe 동적 쿼리 구현)
- Test: JUnit 5, Mockito, AssertJ
- Library: Lombok, Jackson

## 3. 설계 및 주요 가정
### 1) 데이터 추출 전략: 역추적 파싱 (Reverse Tracking)

가정: 계량 증명서의 핵심 데이터인 '최종 계량 수치'와 '최종 증명 시간'은 대개 문서의 하단부에 위치합니다.

구현: 텍스트 전체에서 정규식을 활용하되, 중량 단위(kg)와 시간 패턴(HH:mm:ss)을 스캔할 때 역방향 우선순위를 부여하여 노이즈(중간 과정 수치)를 배제하고 최종 결과값을 정확히 채택합니다
### 2) 데이터 정합성 가드레일 (Data Guardrail)
자동 결측치 보정: 추출된 중량값이 2개인 경우, 물류 도메인 산식(Gross - Net = Tare)을 자동 적용하여 공차중량을 산출합니다.

이상치(Outlier) 실시간 탐지:
- 미래 시간: 현재 시간보다 미래인 계량 일시 감지 시 플래그 처리.
- 중량 역전: 물리적으로 불가능한 수치(Gross < Net) 감지 시 플래그 처리.

검토 사유 명시: reviewNote 필드를 도입하여 "차량번호 누락", "중량 수치 이상" 등 구체적인 사유를 기록, 운영 효율을 극대화했습니다.

## 4. 프로세스 진행 과정
### 샘플 데이터 분석
- 제공된 4개의 JSON 샘플을 분석한 결과, 문서 유형(증명서, 확인서, 계그표 등)에 관계없이 차량번호, 중량(총/공차/실), 계량일시가 공통 핵심 필드임을 도출했습니다.

### 주요 필드 파싱 로직
- 차량번호: 대한민국 자동차 번호판 규격(8자리/7자리)을 고려한 정규식(\d{2,3}[가-힣]\d{4})을 적용하여 오인식된 공백을 제거 후 추출합니다.
- 중량: kg 단위 앞의 숫자 뭉치를 추출하되, 중간에 삽입된 시간 정보(예: 10:30, 11시 30분)를 정규식으로 먼저 제거하는 Pre-cleaning 단계를 거쳐 숫자 정합성을 확보했습니다.
- 날짜/시간: 날짜 구분자(., /, -)의 다양성을 수용하는 정규식을 설계하고, 문서에서 가장 마지막에 등장하는 타임스탬프를 최종 계량 시점으로 간주합니다.

### 조회 기능 고도화 (QueryDSL)
- 단순 저장을 넘어, 실무 운영 환경을 가정하여 차량번호 검색, 검토 필요 티켓 필터링, 날짜 범위 조회 기능을 QueryDSL로 구현했습니다. 이를 통해 타입 안정성을 확보하고 동적 쿼리 유지보수성을 높였습니다.

## 5. 한계 및 개선 아이디어
### 1) 발행처명(회사명) 파싱의 모호성
한계: 샘플 데이터 내 '동우바이오(주)', '장원C&S' 등 발행처마다 사명 표기 형식이 매우 다양합니다. 단순히 '(주)', '주식회사' 등의 키워드에 의존할 경우 범용성이 떨어져 이번 과제 범위에서는 우선 제외하였습니다.

개선: 향후 리코(RECO) 서비스 내에 등록된 업체 데이터 리스트를 기반으로 추출된 텍스트와 문자열 매칭을 수행한다면, 파싱 정합성을 높일 수 있습니다.

### 2) 중량 단위 누락 및 순서 역전 시 오인식 가능성
한계: 현재 로직은 'kg' 단위를 핵심 이정표로 활용합니다. 만약 단위 명시가 없거나, 총중량/공차중량/실중량의 기입 순서가 일반적인 관행(순차적 기입)과 다를 경우 잘못된 필드에 매핑될 위험이 있습니다.

개선: 본 프로젝트에서 구현한 '도메인 가드레일'을 더욱 고도화하여, 파싱된 결과가 일반적인 무게의 대소관계(Gross > Net 등)에 부합하지 않을 경우 관리자에게 즉시 알림을 보내는 로직을 강화할 수 있습니다.

### 3) LLM을 활용한 교차검증
한계: 정규식(Regex)은 속도가 빠르고 명확하지만, 비정형 텍스트의 유연한 변화에는 취약합니다.

개선: OCR 추출 텍스트는 전체 용량이 크지 않으므로, 신뢰도(Confidence)가 낮거나 리뷰 플래그가 발생한 데이터에 한하여 LLM(GPT-4o 등)을 통한 교차 검증을 수행할 수 있습니다. 정규식의 '효율성'과 LLM의 '추론 능력'을 결합한다면 더욱 견고한 파싱 엔진이 될 것입니다.

## 6. 프로젝트 구조

```bash
.
├── src
│   ├── main
│   │   ├── java/kr/co/reco/ocr
│   │   │   ├── application      # Service(Parsing 로직), DTO
│   │   │   ├── domain           # Entity(WeightTicket), Repository
│   │   │   ├── infrastructure   # OCR Extractor, File Export
│   │   │   └── presentation     # Controller
│   │   └── resources
│   │       ├── samples/         # 샘플 JSON 데이터
│   │       └── application.yml  # H2/JPA 설정
│   └── test                     # @Nested 구조의 단위/통합 테스트
├── output/                      # 파싱 결과 저장소 (JSON, CSV)
└── build.gradle
```
