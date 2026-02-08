# 프로세스 진행 과정

## 프로세스 진행과정

reco의 ICT Internship 개발 과제를 수행하게 되었다. 첫 번째 과제인 “과제A. OCR → Parsing (계근지/영수증 텍스트 파싱)”에 대한 진행과정을 담아보겠다.

### 샘플 데이터 분석 및 도출

리코가 폐기물을 다루는 만큼 이를 운반하는 차량에 대한 정보가 중요하다. 업박스 차량의 정보가 담긴 계근지(차량에 화물을 적재한 상태에서 무게를 측정하고, 그 중량을 공식적으로 기록한 증명서)를 OCR을 통해 json으로 추출된 샘플 데이터가 4개 있다. json 값은 엄청 크지만 내용물들이 text라는 필드에서 다시 종합이 되는 것으로 보인다. 또, confidence 필드를 통해 OCR 추출물의 신뢰도를 나타내는 것으로 보인다.

**샘플1**

`"text": "계 량 증 명 서 \n계량일자: 2026-02-02 0016 \n차량번호: 8713 \n거 래 처: 곰욕환경폐기물 \n품종명랑 05:26:18 12,480 kg \n명: \n중 량: \n05:36:01 7,470 kg \n실 중 량: 5,010 kg \n* 위와 같이 계량하였음을 확인함. \n동우바이오(주) \n2026-02-02 05:37:55 \n37.105317, 127.375673",`

**샘플2**

`"text": "* 계 그 표 * \n날 짜: 2026-02-02-00004 \nID-NO : 010889 \n차번호: 80구8713 \n상 호: 고요환경 \n품 명: 식물 \n구 분: 입고 \n총중량: 02:07 13 460 kg \n· \n차중량: 02 : 13 7 560 kg \n, \n실중량: 5 900 kg \n장원C&S \n2026-02-02 02:14:23 \n37.718114, 126.844940",`

**샘플3**

`"text": "** 계 량 확 인 서 ** \n(공급자 보관용) \n없다. \nN \n계량 일자: 2026-02-01 5 \n차량 번호: 5405 입고 \n회 사 명 : \n제 품 명 : \n총 중 량 : 11시 33분 14,080 kg \n공차중량 : 11시 39분 13,950 kg \n실 중 량 : 130 kg \n공육을 unle \n정우리사이클링 (주) \n경기도 화성시 팔탄면 노하길454번길 23 \nTel) 031-354-7778 \n* 상기와 같이 계량하였음을 증명합니다. * \n2026-02-01 11:55:35",`

**샘플4**

`"text": "계 량 증 명 표 \n(주) 하 은 펄 프 \n경기도 화성시 팔탄면 포승향남로 2960-19 \nTEL : (031)359-9127 \nFAX : (031)359-9128 \n신성(푸디스트) 귀하 \n입 고입고 \n품 명 국판 구 분 \n출 \n차량 No. 0580 \n일 시 2025-12-01 \n계량횟수 0022 \n총 중 량 14,230 kg (09:09) \n공차중량 12,910 kg (09:09) \n실 중 량 1,320 kg 감 량 0 kg \n비 고 1,320 kg 취급자 \n계량표는 상기와 같이 계량하였음을 증명함.",`

문서의 형태는 ‘계량 증명표’, ‘계그표?’, ‘계량 확인서’로 확인이 되었다. 우선 ‘계그표”는 계량 증명표를 잘못 인식한 결과로 가정하겠다. 이외에도 다른 주요 필드는 명칭은 다를 수 있지만 차량의 총 중량, 공차 중량, 실 중량이 있다. 또 회사명과 차량 번호, 계량일 등이 주요 필드로 보인다. 이에 다음과 같은 엔티티를 구성해보았다.

```java
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class WeightTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String documentType; // 증명서, 확인서, 계량표 등 정규화된 값

    @Column(nullable = false)
    private String carNumber;

    private Double grossWeight;  // 총중량
    private Double tareWeight;   // 공차중량 (차중량)
    private Double netWeight;    // 실중량

    private LocalDateTime scaledAt; // 계량 일시

    private Double confidence;      // OCR 전체 신뢰도
    private boolean needsReview;    // 임계값 미만일 경우 true

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
```

다음으로는 각 주요 필드를 추출해보겠다.

### 주요 필드 추출

우선 documentType을 추출하기 위해 데이터를 보니 모든 데이터 값에 “계량하였음을 확인”, “계량하였음을 증명함” 등이 나오는 것을 보아 사실상 문서별 유형에서의 차이가 없는것으로 보여 해당 필드를 제외하겠다.

다음으로는 **자동차 번호**를 먼저 파싱해볼 것이다. 아무래도 OCR 파싱이다보니 중간중간 무의미한 공백이 추가되어있기도 하다. 우리나라의 차량번호 형태는 다음과 같다.

‘대한민국 자동차 번호판은 8자리(전기차 등 7자리 포함) 숫자 및 한글 조합으로, 앞 3자리(차종), 가운데 한글(용도), 뒤 4자리(일련번호)로 구성’

이에 다음과 같은 정규식을 세워 (공백 포함) ‘차량번호’, ‘차번호’, ‘차량No’에 대한 추출 패턴을 구현했다.

`"(차\\s*량\\s*번\\s*호|차\\s*번\\s*호|차\\s*량\\s*No\\.?)[^0-9가-힣]*([0-9가-힣\\s]{4,15})"`

이후 두 번째 그룹에서 공백을 전부 없애고 `"(\\d{2,3}[가-힣]\\d{4}|\\d{4})"` 를 통해 차량번호를 추출했다.

다음으로는 **중량**에 대해 파싱을 해볼것이다. 크게는 총중량, 공차중량, 실중량 이렇게 세 가지로 나눠진다. 하지만 샘플 데이터에 따라 다음과 같은 어려움이 있었다.

1. 공차중량이 없는 경우 (총중량과 실중량만 있음)
2. 명칭이 다른 경우 (중량-총중량, 공차중량-차중량)
3. 중간에 시간이 작성된 경우 (11시33분, 11:33)

처음에는 ‘중량’, ‘총중량’, ‘총 중 량’을 정규식으로 뽑아내는 식으로 접근을 했다. 그러다가 다른 접근 방법을 생각해보았다. 무게는 항상 kg이라는 단위를 동반하며, 무게가 3개 있을 경우 순서대로 총중량, 공차중량, 실중량이고, 무게가 2개 있을 경우 총중량, 실중량이다. 그리고 시간이 중간에 들어가는 경우에는 예를 들어 ‘10시 10분’ 혹은 ‘10:10:10’이런 식으로 삽입이 되어있었다. 이에 다음과 같은 규칙으로 파싱을 시도했다.

1. kg 기준 앞의 4~20개의 뭉치를 추출
2. ‘분’이 있는 경우, 분을 포함하여 앞부분 삭제
3. ‘:’이 있는 경우, `숫자:숫자` 또는 `숫자:숫자:숫자` 형태를 찾아서 삭제
4. 이후 남은 값에 대해 ‘,’을 없애고 숫자 형태로 변형

이렇게 추출된 값이 3개가 있다면 총중량, 공차중량, 실중량 필드에 추가하고, 2개가 있다면 총중량과 실중량으로 공차 중량을 계산하여 값을 추가하였다.

다음으로는 **계량 시간**에 대해 파싱을 해보자. 날짜에 대한 명칭은 ‘계량일자’, ‘날짜’, ‘계량 일자’, ‘일시’로 다양한 것을 확인했다. 시간은 text의 중간중간에 나오다가 마지막에 날짜와 함께 다시 나타나는 경우도 있다.

날짜의 경우 주어진 샘플에서는 yyyy-MM-dd로 되어있지만, yy-MM-dd인 경우와 그 구분자가 ‘.’, ‘/’으로 되어있는 경우를 대비하기 위해 다음과 같은 정규식을 세웠다.

`"(\\d{4}|\\d{2})[-./]\\d{2}[-./]\\d{2}"`

시간은 분까지만 표시되고 초가 없는 경우를 대비하여 다음과 같은 정규식을 세웠다.

`"\\d{2}:\\d{2}(?::\\d{2})?"`

계량이 확인(증명)되는 타이밍은 가장 마지막에 표시된 시간이라는 결론을 내렸다. 이에 날짜와 시간에 대해 가장 마지막에 나오는 값을 각각 파싱해서 조합하는 방법을 사용했다.

### 파싱 및 저장 테스트

“출력은 JSON/CSV 등 구조화 형태로 저장”이라는 요구사항에 따라 파싱이 되면서 DB 뿐만 아니라 output/에 .json과 .csv로 저장이 되는 로직을 간단히 구현했다. 이후 scr/main/resources/sample에 저장해두었던 sample_01부터 sample_04까지의 json 파일에 대한 테스트코드를 작성해보자.

```java
@Test
@DisplayName("샘플 01: 표준 계량증명서 파싱 테스트")
void parseSample01() {
    // given
    OcrResult ocrResult = extractor.extract(SAMPLE_PATH + "sample_01.json");

    // when
    WeightTicket result = parsingService.parse(ocrResult);

    // then
    assertThat(result.getCarNumber()).isEqualTo("8713");
    assertThat(result.getGrossWeight()).isEqualTo(12480.0);
    assertThat(result.getNetWeight()).isEqualTo(5010.0);
    assertThat(result.getScaledAt()).isEqualTo(LocalDateTime.of(2026, 2, 2, 5, 37, 55));
}
```

위와 같이 주요 필드인 자동차 번호, 중량, 계량 일시에 대한 값을 테스트하여 전부 통과한 것을 확인했다.

### 조회 기능 구현 및 테스트

요구사항에 명시되어있지는 않았지만 간단한 조회 기능을 구현해볼 것이다. 기존에 진행하던 프로젝트에서 여러 필드가 있는 엔티티에 대해 각 필드에 대한 필터링이 필요한 경우가 있었다. 이에 QueryDSL이라는 기술을 알게되어 강의와 블로그를 찾아보며 정리를 했었는데, 마침 리코에서 “JPA/QueryDSL을 활용한 매핑 및 ORM”이라는 직무내용이 있는 것을 보고 간단히 적용을 해보려고 한다.

WeightTicket에 필터링할 필드가 많지는 않아 다음과 같이 조회용 DTO를 구성해보았다.

```java
@Getter
@Setter
public class WeightTicketSearchRequest {
    private String carNumber;
    private Boolean needsReview;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime start;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime end;

}
```

차량번호, 리뷰 필요 여부(신뢰도가 임계값 미만인 티켓), 날짜 범위로 필터링을 하여 조회를 하는 것이다. 메서드 내부적으로 BooleanExpression을 사용하여 전체 조회와 각 필터 조회 메서드를 구분하지 않고 통합해서 사용할 수 있으며 타입 안정성을 보장해준다.

이에 BeforeEach로 Ticket을 저장한 후 특정 condition에 따라 원하는 값이 잘 조회가 되는지 테스트틑 진행하였다.

### 리팩터링

**검토 요구상황 확장**

기존에는 OCR 파싱의 신뢰도 필드인 confidence가 0.6 미만인 경우 needsReview 필드가 true가 되어 추가적인 검토를 요구한다. 이에 추가로 차량번호, 계량 일시, 실중량과 같은 주요 필드가 누락된 경우에도 검토를 요구하도록 수정하였다.

```java
boolean isDataMissing = (carNumber == null || scaledAt == null || grossWeight == 0.0);
boolean isUnreliable = (ocrResult.getConfidence() < 0.6 || weights.isEmpty());
boolean needsReview = isDataMissing || isUnreliable;
```

**검토 요구 이유 추가**

하지만 현재 상태로는 어떤 이유인지 한 눈에 확인하기가 어렵다. 이에 reviewNote 필드를 추가하여 검토가 필요한 경우 어떤 이유인지 키워드를 남기기로 했다.

```java
List<String> reasons = new ArrayList<>();

if (carNumber == null || "UNKNOWN".equals(carNumber)) reasons.add("차량번호 누락");
if (scaledAt == null) reasons.add("계량일시 누락");
if (grossWeight == 0.0) reasons.add("중량 정보 부족");
if (weights.isEmpty()) reasons.add("중량 데이터 추출 실패");
if (confidence < 0.6) reasons.add(String.format("낮은 신뢰도(%.2f)", confidence));

boolean needsReview = !reasons.isEmpty();
String reviewNote = String.join(", ", reasons);
```

위와 같이 간단하게 각 필드의 누락에 따른 짧은 메세지와 함께 reasonNote 문자열을 생성하여 관리자가 보고 한 눈에 파악할 수 있게 하였다.

**이상 데이터: 시간이 현재보다 미래인 경우**

앞에서는 결측치에 대해 다뤄보았다면 이번에는 이상치에 대해 다뤄보겠다. 첫 번째 경우는 계량 시간이 현재보다 미래인 경우이다. 이부분은 OCR의 성능에 따라 자주 일어나진 않겠지만 잘못 인식된 경우 관리자가 확인할 수 있게 처리를 해보자.

```java
if (scaledAt != null && scaledAt.isAfter(now)) {
    reasons.add("계량시간 이상(미래 시간)");
    log.warn("이상 데이터 발견: 현재 시간({})보다 미래인 계량 시간({})이 입력됨", now, scaledAt);
}
```

**이상 데이터: 총중량이 실중량보다 큰 경우**

해당 경우는 숫자 인식에 실패했거나 내가 찾은 패턴(총중량, 공차중량, 실중량 순서)이 틀린 경우라고 볼 수 있다. 보통은 위와 같은 순서도 작성이 되겠지만 파싱 실수 및 알고리즘의 문제를 확인하기 위한 방어로직을 구현해보자.

```java
if (weightValues.grossWeight() > 0 && weightValues.netWeight() > 0 && weightValues.grossWeight() <= weightValues.netWeight()) {
    reasons.add("중량 수치 이상(총중량 <= 실중량)");
    log.warn("비정상 중량 탐지: 총중량({}) <= 실중량({})", weightValues.grossWeight(), weightValues.netWeight());
}
```

### 통합테스트

파싱 및 저장, 조회, 그리고 각각에 대한 테스트코드를 작성해보았다. 이제 통합테스트를 통해 다음과 같은 흐름을 검증해보자.

- json이 OcrResult에 담겨 파싱이 되어 WeightTicket이 생성되고 저장되는 흐름
- 필터에 맞게 DB에서 조회가 되는 흐름

이전 프로젝트에서는 TestRestTemplate를 사용하여 HTTP 요청을 보내서 테스트를 해보았다. 그러던 중 MockMvc에 대해 알게되었다.

1. 관심사의 분리

   : 우리가 테스트하고 싶은 건 "작성한 비즈니스 로직이 HTTP 요청을 받아 DB까지 잘 흘러가는가"이지, "내장 톰캣이 소켓 통신을 잘하는가"가 아니기 때문

2. 속도와 효율

   : 서버를 매번 띄우는 `TestRestTemplate`에 비해 수배 이상 빠름


현재는 네트워크나 부하 관련 테스트가 아닌 API 요청이 컨트롤러와 서비스 레이어를 거쳐서 DB까지 잘 연결이 되어 올바른 반환값이 나오는지가 중요하다. 이에 MockMvc로 테스트코드를 작성하였다.

`"통합 시나리오 1: 샘플 JSON 파일을 파싱하여 DB에 저장하고 결과를 반환한다"`

`"통합 시나리오 2: QueryDSL 필터링을 통해 특정 차량번호를 검색한다"`

해당 시나리오들은 프로젝트에 위치한 json 샘플 파일을 사용한다. 그렇기에 서비스로직대로 output 디렉터리에 json과 csv 파일을 생성한다. 매 테스트 이후 자동으로 삭제되도록 AfterEach에서 cleanUp 작업을 해주자.

### 리팩터링 - 1차

어느정도 기능적인 부분과 테스트코드는 작성이 되었다. 항상 RESTAPI를 기준으로 CRUD를 해오다보니 이런 사전과제 미션에서도 API를 호출하는 코드를 짜도 되는지 모르겠다. 하지만 QueryDSL을 통한 조회를 위해 저장 기능과 함께 API 주소를 만들었으니 이에 대한 반환 형식도 개선을 해보자.

1. ApiResponse 적용

   첫 프로젝트의 서버 파트장은 프론트에게 친절한 코드가 좋은 코드라고 했다. ApiResponse를 구현하여 반환값의 형식을 `isSuccess`, `code`, `message`, `result` 로 고정을 해서 반환값 파싱시에 불필요한 if문들이 반복되지 않게 하자.

2. CustomException 적용

   기존에는 의도나 맥락이 불분명한 `RuntimeException` 을 사용했다. 클라이언트는 HTTP 500만 보고는 문제를 특정할 수 없었다. 이에 `ErrorCode`와 `CustomException`을 도입하여 규격화된 응답을 보낼 수 있게 되었다.

3. 정적 팩터리 메서드 도입 및 도메인 정합성 강화

   기존에는 외부 어디서든 `WeightTicket`을 `new`나 빌더로 객체를 생성할 수 있었다. 하지만 이런 방식은 불완전하거나 비즈니스 규칙에 어긋나는 데이터를 생성할 수 있었다. 이에 기본 생성자를 `private`으로 숨기고 `WeightTicket`에 대해 정적 팩터리 메서드를 생성하여 내부에서 `validate` 메서드로 검증 후 생성자를 호출하여 티켓을 생성하게 되었다.

4. 텍스트 추출 로직의 관심사 분리 (RegexExtractor)

   `ParsingServiceImpl`은 정규식 패턴 정의, 문자열 전처리, 비즈니스 흐름 제어까지 너무 많은 책임을 혼자 지고 있었다. 이를 해결하기 위해 `RegexExtractor`로 문자열 처리 로직을 분리했다. 이에 서비스 레이어는 비즈니스 로직에만 집중하고 정규식을 이용한 문자열 파싱은 `RegexExtractor`에서 담당하게 되어 단일책임 원칙을 준수했다. 이제 서비스 전체를 실행하지 않고도 정규식 패턴만 따로 테스트가 가능하게 되었다.


아키텍쳐를 더 깔끔하게 바꾸려던 리책터링을 진행하고 변경사항에 한해서만 테스트코드를 수정하던 참에 새로운 결함을 발견했다!

```java
@Test
@DisplayName("총중량이 실중량보다 작거나 같은 경우(중량 역전) needsReview가 true여야 한다")
void shouldMarkNeedsReviewWhenWeightsAreInverted() {
    // given: 총중량(5,000) < 실중량(12,000)인 비정상 데이터
    OcrResult invertedWeightResult = OcrResult.builder()
        .fullText("차량번호 12가3456 계량일시 2026-02-05 10:00 총중량 5,000kg 실중량 12,000kg")
        .confidence(0.99).build();

    // when
    WeightTicket result = parsingService.parse(invertedWeightResult);
    
    // then
    assertThat(result.isNeedsReview()).isTrue();
    assertThat(result.getReviewNote()).contains("중량 수치 이상");
}
```

위 코드에서 총중량(공차 중량+ 실중량)보다 실중량이 크면 `boolean needsReview`가 `true`가 되어 관리자가 검토를 할 수 있게 하려는 의도였다. 하지만 결과는 아래와 같았다.

Expected :true
Actual   :false

이에 출력을 해보니 총중량이 2055000.0로 나온 것이다. 즉, 앞의 계량일시에서도 데이터를 가져온 것이다. 이에 긴급하게 정규식을 고쳐보기로 했다. 다시 한 번 중량 파싱 로직을 검토해보자.

`"([^kg\\n]{4,20})\\s*kg"` 를 통해 kg 앞의 4~20개의 뭉텅이를 가져온다.

→ 2-05 10:00 총중량 5,000 까지 가져와짐꼭

```java
private String getOnlyNumber(Matcher kgMatcher) {
    String segment = kgMatcher.group(1);
    String cleanSegment = segment;

    if (segment.contains("분")) {
        cleanSegment = segment.replaceAll(".*분\\s*", "");
    } else if (segment.contains(":")) {
        cleanSegment = segment.replaceAll("\\d{1,2}\\s*:\\s*\\d{1,2}(\\s*:\\s*\\d{1,2})?", "");
        cleanSegment = cleanSegment.replaceAll(".*[:시]\\s*", "");
    }

    return cleanSegment.replaceAll("[^0-9,.]", "").replaceAll("[,\\s]", "");
}
```

‘분’이 있으면 ‘분’포함 앞부분을 제거하고, ‘:’가 있으면 시간 형태(HH:mm:ss)의 부분을 제거한다.

→  2-05총중량 5,000이 되었다가 2055000으로 완성되어 반환됨

이에 첫 정규식을 통과한 이후 ‘문자-숫자-kg’ 순서가 있는 경우 문자를 포함하여 앞부분을 삭제하는 정규식을 만들어보자.

`replaceAll("^.*[^0-9,.\\s]+", "")` 을 통해서 역순으로 추적을 하며 숫자(0-9), 콤마(,), 점(.)을 제외한 모든 것을 삭제 대상으로 보는 것이다. 이를 `KG_PATTERN` 통과 후 적용을 해보니 오류가 났던 테스트는 통과를 했지만 기존 샘플들에 적용하기에는 문제가 있어보인다.

`05:36:01 7,470 kg` 의 경우 ‘:’가 문자열로 인식이 되어 앞부분이 날아가는 경우 `01 7,470 kg` 이 남아서 17470.0이 될 수 있다. 이에 순서를 바꿔서 ‘분’이나 ‘HH:mm:ss’ 패턴이 있는 경우를 먼저 처리한 후 마지막에 적용하는 방식으로 변경했다.

```java
private String getOnlyNumber(Matcher kgMatcher) {
    String segment = kgMatcher.group(1);
    String cleanSegment = segment;

    if (segment.contains("분")) {
        cleanSegment = segment.replaceAll(".*분\\s*", "");
    } else if (segment.contains(":")) {
        cleanSegment = segment.replaceAll("\\d{1,2}\\s*:\\s*\\d{1,2}(\\s*:\\s*\\d{1,2})?", "");
        cleanSegment = cleanSegment.replaceAll(".*[:시]\\s*", "");
    }
    cleanSegment = cleanSegment.replaceAll("^.*[^0-9,.\\s]+", "");

    return cleanSegment.replaceAll("[^0-9,.]", "").replaceAll(",", "");
}
```

### 정규식 테스트

앞선 리팩터링으로 이제는 ParsingServiceImpl에서 RegexExtrator가 분리되었다. 이에 정규식에 대해서 따로 테스트를 진행할 수 있게 되었다. 추출기는 ‘중량’, ‘차량 번호’, ‘계량일시’를 추출한다.

중량 추출 검증을 위한 예시를 제미나이를 통해 받았다. 이를 @ParameterizedTest를 통해 하나의 메서드 내에서 수행해보자.

```java
@ParameterizedTest
@CsvSource({
    "'총중량 13 460 kg', 13460.0",
    "'2026-02-05 5,000kg', 5000.0",
    "'05:36:01 10시 7,470kg', 7470.0",
    "'실중량 12000kg', 12000.0",
    "'2026.10.10 8,150 kg', 8150.0"
})
```

2, 5번 케이스에서 실패했다!  2번은 앞의 날짜와 합쳐져서 55000.0이 니왔고, 5번은 결과가 나오지 않았다. 정규식을 바탕으로 분석을 한 결과 ‘분’이나 ‘:’도 없었고 모든 케이스를 통과하여 2026.10.10 8,150 자체로 나온 것이다. 그렇기에 숫자로 변환되지 않았고 결과가 없던 것이다. 사실 샘플 json을 보면 중량값 앞에 시간은 있지만 날짜가 있는 경우는 없었다. 날짜가 있더라도 시간과 함께 나오기에 내가 미리 세워둔 날짜 필터에 걸려서 해결이 될 것이다. 하지만 혹시나를 위해 시간을 처리하기 전에 `"\\d{2,4}[-./]\\d{1,2}[-./]\\d{1,2}"` 를 추가하여 방어로직을 세워서 해결했다.

### 리팩터링 - 2차

내가 구현한 로직에서는 confidence가 0.6 미만인 경우 needsReview가 true가 된다.
하지만 여기서 **0.6**이라는 수치는 다음과 같은 한계를 가진다.

1. **불확실성**: OCR 엔진의 성능이나 입력 이미지의 품질에 따라 0.5가 충분히 정확할 수도, 0.7도 불안정할 수도 있는 상황에서 임의로 지정된 값
2. **유지보수 저하**: 엔진 교체나 비즈니스 정책 변경으로 임계값을 수정해야 할 때마다 소스 코드를 수정하고 재빌드/재배포해야 하는 번거로움
3. **매직 넘버(Magic Number)**: 코드 깊숙한 곳에 숫자가 박혀 있어, 추후 유지보수 시 해당 수치의 기원과 의미를 파악이 어려움

이에 외부 설정으로 주입하는 방식을 도입해보자. application.yml에 threshold 값을 지정한다. 처음에는 WeightTicket 엔티티에서 값을 받으려고 했지만, 이는 도메인 로직만 담는 것이 좋을듯하다. 우리는 지금 create라는 정적 팩터리 메서드를 통해서만 WeightTicket을 발행하기 때문에 서비스단에서 이를 부를 때 파라미터에 같이 담아주는 방식으로 전달을 하여 해결했다.

### 리팩터링 - 3차

정리해보면 현재 우리는 다음과 같은 전략을 기반으로 수행한다.

1. ocrResult가 없거나 weights가 2개 미만이면 계근지를 생성하지 않고 예외처리
2. 그 외의 경우는 needsReview를 true로 하고 reviewNote에 사유 추가

ocrResult가 없다면 정말 저장할 내용이 없기에 예외처리를 하는게 나을 수 있지만, 특정 필드가 추출되지 않은 것에 대해서는 OCR에 입력된 원본 이미지와 비교하며 수기로 채울 수 있게 하는 것이 더 좋은 전략이라고 생각이 되었다. 현재 개발 과제에서는 추출된 json만 받았지만, 이미지를 입력 받았다면 needsReview가 true인 경우에 reviewNote와 함께  이미지를 반환하는 전략을 생각해봤다. 우선 다음과 같이 전략을 수정하자.

중량이 2개 미만이 추출되면 예외처리 → 계근지 생성 후 검토 필요항목에 (원본 이미지와 함께) 추가

needsReview가 true인 계근지 목록은 이미 QueryDSL을 통한 필터를 통해 확인할 수 있다. 처음에는 needsReview가 true인 계근지만 수정 가능하게 하려다가 관리자가 따로 오류를 확인하고 수정할 수 있기 때문에 그부분은 열어두었다. 수정이 될 경우 needsReview를 false로 바꾸고 reviewNote 내용을 수정 완료가 되었다는 메세지로 변경하였다. 이후 통합 테스트코드를 작성하여 마무리 하였다.

### 회고

테스트코드로 엣지 케이스를 만들어보기 전까지는 과제B로 넘어갈까 고민도 많이 했다. 사전과제가 처음이다보니 둘 중 하나만 하는것과 둘 다 하는것 중에 많이 고민을 했지만, 나한테는 아직 넉넉한 시간은 아닌가보다. 리코에서 하는 업무를 작은 미션처럼 제공한것이니 결국 인턴에 합격하면 나머지 미션에 대한 업무도 해볼 수 있으리라 생각한다.

초반 설정이나 아키텍쳐에 대해서는 AI의 도움을 많이 받았다. 까먹은 부분도, 이미 아는 부분도 있지만 빠르게 만들어보고 깨져보는것도 중요하다고 생각했기 때문이다. 대신 AI가 제공한 코드를 외우진 못하더라도 이해가 안되는 부분이 있으면 잠시 멈추고 이해를 해보았다.

이번 과제A는 정규표현식을 많이 활용했다. 처음에는 그저 AI에게 샘플을 던져주고 정규표현식을 만들도록 해봤다. 하지만 추출에 실패할때마다 내가 그 이유를 모르고 AI가 개선해준 내용을 몰랐다. 그렇기에 잠시 내려놓고 전공서적과 유투브 강의를 통해 딱 1시간만 투자하여 정규표현식 공부를 하고 정리를 했다. 물론 외우진 못했기때문에 정규식 기호들을 화면에 띄운상태로 개발을 진행했다. 그리고 샘플 데이터 4개에 대한 text를 한 눈에 볼 수 있게 나열한 후 같은 키워드끼리 형광표시를 하며 작업했다. 그렇게 내가 직접 작성한 정규식을 AI에게 보여주니 ‘무릎 탁 치는 아이디어’, ‘너무 기발한 아이디어’ 같은 대답을 해주었다. (그동안 넌 뭘한거니?!). 샘플 데이터에서 규칙을 찾고 문제를 풀다보니 예전에 알고리즘을 풀던 재미가 생각났다.

그동안 해커톤이나 프로젝트를 할 때는 막연히 "사람들이 이런 서비스를 좋아하겠지?"라는 예상으로 움직였다. 하지만 이번 미션을 통해 현업에서 실제로 쓰이는, '의미 있는 기능'을 구현한다는 실감을 얻었다. 낯선 환경에서의 스트레스도 있겠지만, 내가 하는 일이 실질적인 가치를 만든다는 확신이 있다면 기꺼이 즐기며 성장할 수 있을 것 같다.
