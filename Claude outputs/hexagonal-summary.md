# 헥사고날 아키텍처 총정리

> tikitaka-ai 프로젝트 기준 · 2026-09-16

---

## 0. 한 문장

> **비즈니스 로직이 기술을 모르게 만든다.**

기술(HTTP, DB, Ollama)은 바뀌지만 업무 규칙은 오래 간다. 그러니 **오래 가는 것을 중심에 두고, 자주 바뀌는 것을 바깥으로 밀어낸다.**

### 안 쓰면 생기는 일

```python
class ProbeService:
    async def run(self):
        res = await httpx.post("http://localhost:11434/api/chat", ...)
        return res.json()["message"]["content"]
```

동작은 한다. 그런데:

1. Ollama → vLLM 교체 시 이 클래스를 뜯어야 함
2. 테스트하려면 진짜 Ollama를 켜야 함
3. HTTP·JSON·포트번호 지식이 업무 로직에 섞여 있음

우리 프로젝트는 **"추론 기기와 모델을 나중에 갈아끼운다"** 가 명시된 요구사항이므로, 이 추상화는 과설계가 아니라 정확히 값을 한다.

---

## 1. 세 계층

| 계층 | 한마디로 | 기술 이름이 등장하나 |
|---|---|---|
| **domain** | 이 시스템이 다루는 **개념** | ❌ 절대 안 됨 |
| **application** | 우리가 **무엇을** 하고, **무엇이 필요**한가 | ❌ 안 됨 |
| **adapter** | 그 필요를 **어떤 기술로** 채우나 | ✅ 여기만 등장 |

```
        ┌──────────────────────────────┐
        │          adapter             │  fastapi, httpx, postgres…
        │   ┌──────────────────────┐   │
        │   │     application      │   │  포트 + 서비스
        │   │   ┌──────────────┐   │   │
        │   │   │   domain     │   │   │  개념 + 규칙
        │   │   └──────────────┘   │   │
        │   └──────────────────────┘   │
        └──────────────────────────────┘
              바깥 → 안쪽 (의존 방향)
```

### 우리 프로젝트에 적용

| 파일 | 계층 | 근거 (import) |
|---|---|---|
| `probe_result.py` | domain | `dataclasses` (표준 라이브러리) |
| `errors.py` | domain | 없음 |
| `llm_probe_port.py` | application | `abc` + 우리 도메인 |
| `probe_service.py` | application | 우리 것만 |
| `ollama_probe_adapter.py` | adapter | **`httpx`** ← 프로젝트에서 유일 |

---

## 2. 의존성 규칙 (전부의 핵심)

> **화살표는 항상 안쪽을 향한다. 예외 없음.**

```
adapter  ──import──▶  application  ──import──▶  domain
   ✅                      ✅

domain   ──import──▶  adapter
   ❌ 이게 생기면 구조가 죽은 것
```

### 확인하는 법

```bash
# domain·application이 외부 라이브러리를 import 하는지 검사
grep -rn "^import \|^from " app --include="*.py" \
  | grep -v "from app\." | grep -v __init__
```

결과에 `adapter/` 경로만 나와야 정상. (`abc`, `time`, `dataclasses` 같은 표준 라이브러리는 예외)

### 삭제 테스트

- `adapter/`를 통째로 지워도 → `application/`은 여전히 말이 됨
- `application/`을 지우면 → `adapter/`는 import 에러로 즉사

**의존이 한 방향뿐**이라는 증거.

---

## 3. 포트와 어댑터

### 포트 = 약속 (인터페이스)

```python
# app/connect_test/application/port/outbound/llm_probe_port.py
from abc import ABC, abstractmethod
from app.connect_test.domain.probe_result import ProbeResult


class LLMProbePort(ABC):
    @abstractmethod
    async def probe(self, prompt: str) -> ProbeResult:
        ...
```

- 몸통이 `...` — **"어떻게"는 여기서 안 정한다**
- `ABC` + `@abstractmethod` 의 유일한 기능: **약속만 적힌 것**과 **약속을 안 지킨 것**의 생성을 막음
- 이름이 `OllamaPort`가 아닌 이유: 포트는 **"필요"를 표현하지 "기술"을 표현하지 않는다**

### 어댑터 = 구현

```python
# app/connect_test/adapter/outbound/llm/ollama_probe_adapter.py
class OllamaProbeAdapter(LLMProbePort):
    async def probe(self, prompt: str) -> ProbeResult:
        ...  # httpx로 실제 호출
```

### 왜 포트는 안쪽, 어댑터는 바깥쪽에 사나 — 콘센트 비유

```
벽의 220V 콘센트 구멍  =  포트    → 건물(우리 코드)이 규격을 정함
거기 꽂는 변환 플러그   =  어댑터  → 기기(외부 기술)가 맞춰서 옴
```

해외 기기가 "나는 110V니까 벽을 바꿔줘"라고 못 한다. **기기 쪽이 어댑터를 사서 맞춘다.**

우리도 똑같다:
- application: "`probe(prompt) -> ProbeResult` 형태로 줘" ← 규격을 우리가 정함
- adapter: "알겠어, Ollama JSON을 그 모양으로 변환할게" ← 바깥이 맞춤

**인터페이스를 안쪽 패키지에 둔 것만으로 의존 방향이 뒤집힌다.** 이게 "의존성 역전"의 전부다. 별다른 마법 없음.

---

## 4. 인바운드 / 아웃바운드

### 기준은 "주도권"

| | 방향 | 뜻 | 원어 |
|---|---|---|---|
| **인바운드** | 바깥 → 우리 | 우리가 **불려감** | driving |
| **아웃바운드** | 우리 → 바깥 | 우리가 **먼저 부름** | driven |

"요청이냐 응답이냐"가 아니라 **누가 핸들을 잡았냐**가 기준.

### 이름표는 데이터가 아니라 **문**에 붙는다

건물 정문으로는 사람이 들어오기도 나가기도 하지만, 정문은 여전히 정문이다.

```
        ┌─ 정문 (인바운드) ─┐              ┌─ 후문 (아웃바운드) ─┐
브라우저 │ ① 요청   →       │              │ ② 요청   →          │ Ollama
        │ ④ 응답   ←       │    [우리]    │ ③ 응답   ←          │
        └──────────────────┘              └─────────────────────┘
```

①과 ④는 방향이 반대인데도 **둘 다 인바운드.** 브라우저와 맞닿은 그 문에서 벌어진 일이기 때문.

판단할 때는 "들어와? 나가?"가 아니라 **"어느 쪽 문에서 오간 거지?"** 를 물어라.

### 구현이냐 호출이냐

| | 포트와의 관계 |
|---|---|
| 아웃바운드 어댑터 | 포트를 **구현**한다 (`class OllamaProbeAdapter(LLMProbePort)`) |
| 인바운드 어댑터 | 서비스를 **호출**한다 (`await service.run(...)`) |

상속 화살표가 반대다. 그래서 인바운드는 포트 없이도 동작한다 (우리가 `port/inbound`를 생략한 이유).

### 헷갈리는 사례

| | TCP 연결을 여는 쪽 | 로직을 촉발하는 쪽 | 결론 |
|---|---|---|---|
| FastAPI 라우터 | 브라우저 | 브라우저 | 인바운드 |
| Kafka 컨슈머 | **우리** | Kafka 메시지 | **인바운드** |
| 스케줄러 | 우리 | 시간 | 인바운드 |
| Ollama 클라이언트 | 우리 | **우리** | 아웃바운드 |

**"내 로직이 누구 때문에 시작됐나"** 로 판단하면 안 틀린다.

### 어댑터 카탈로그

**인바운드** — 바깥이 우리를 깨우는 통로
: FastAPI 라우터 / DTO / 미들웨어, CLI 커맨드, 스케줄러, Kafka·SQS 컨슈머, gRPC

**아웃바운드** — 우리가 바깥을 부르는 통로
: LLM 클라이언트, DB 리포지토리, Redis, S3, Slack·메일, 외부 API, **시계**

> 시계가 의외의 예: 테스트에서 "2026년 1월 1일인 척"하려면 `datetime.now()`를 직접 부르면 안 되고 `ClockPort`로 빼야 한다. **"내 맘대로 못 바꾸는 바깥 것"은 전부 어댑터 후보.**

---

## 5. 데이터가 지나가는 네 가지 모양

한 번의 요청이 네 번 옷을 갈아입는다.

| 모양 | 방향 | 규격 주인 | 사는 곳 |
|---|---|---|---|
| ① `ProbeRequest` | 인바운드 | **우리** | `adapter/inbound/web/` |
| ② `payload` dict | 아웃바운드 | **Ollama** | `adapter/outbound/llm/` |
| ③ Ollama JSON | 아웃바운드 | **Ollama** | `adapter/outbound/llm/` |
| 🔵 `ProbeResult` | — | **우리** | `domain/` |
| ④ `ProbeResponse` | 인바운드 | **우리** | `adapter/inbound/web/` |

```
브라우저
   │ ① {"prompt": "안녕?"}
   ▼
[ router.py ]  ← 인바운드 어댑터
   │  service.run("안녕?")         ← 그냥 str
   ▼
[ probe_service.py ]  ← 기술 모름
   │  llm.probe("안녕?")
   ▼
[ ollama_probe_adapter.py ]  ← 아웃바운드 어댑터
   │ ② {"model":..., "messages":[...], "stream":false}
   ▼
 Ollama
   │ ③ {"message":{"content":"안녕하세요!"}, "eval_count":12, "done":true, ...}
   ▼
[ ollama_probe_adapter.py ]  ← ★ 번역 지점 1
   │ 🔵 ProbeResult(model=..., answer="안녕하세요!", latency_ms=612, eval_count=12)
   ▼
[ probe_service.py ] → [ router.py ]  ← ★ 번역 지점 2
   │ ④ {"model":..., "answer":"안녕하세요!", "latency_ms":612, "tokens_per_second":19.61}
   ▼
브라우저
```

### 여기서 읽히는 것

- **인바운드 쪽은 우리가 규격을 정하고, 아웃바운드 쪽은 남의 규격을 따라간다**
- Ollama가 응답 형식을 바꾸면 → ②③만 수정. ①④와 도메인은 그대로
- 우리가 API 스펙을 바꾸면 → ①④만 수정. ②③과 도메인은 그대로
- **두 변화가 서로에게 번지지 않는다.** 가운데 `ProbeResult`가 완충재이기 때문

어댑터 없이 Ollama JSON을 그대로 브라우저에 던졌다면, Ollama 업데이트 한 번에 모든 API 클라이언트가 깨졌을 것이다.

---

## 6. 의존성 주입 (DI)

### 나쁜 버전

```python
class ProbeService:
    def __init__(self):
        self._llm = OllamaProbeAdapter(...)   # 자기가 직접 만듦
```

→ 영원히 Ollama. 교체도 테스트도 불가능.

### 좋은 버전

```python
class ProbeService:
    def __init__(self, llm: LLMProbePort):    # 밖에서 받음
        self._llm = llm
```

차이는 **한 줄**. 타입이 구현체가 아니라 **인터페이스**라는 게 전부다.

> DI는 이름이 거창한데 실체는 *"`__init__`에서 만들지 말고 인자로 받아라"* 가 전부다.

### 조립은 한 곳에서만

```python
# app/config/container.py — 유일하게 "누가 진짜인지" 아는 파일
llm: LLMProbePort = OllamaProbeAdapter(base_url=settings.OLLAMA_BASE_URL, ...)
probe_service = ProbeService(llm=llm)
```

vLLM으로 바꾸려면 **이 파일에서 한 줄**만 바꾼다.

---

## 7. 테스트가 공짜로 따라온다

포트가 있으면 **가짜를 끼울 자리**가 생긴다.

```python
class FakeLLM(LLMProbePort):
    async def probe(self, prompt: str) -> ProbeResult:
        return ProbeResult('fake-model', f'[받은] {prompt}', 100, 5)

service = ProbeService(FakeLLM())   # Ollama 없이 서비스 전체 검증
```

| 진짜 어댑터로 테스트 | 가짜로 테스트 |
|---|---|
| Ollama 서버 켜야 함 | 아무것도 안 켜도 됨 |
| 1~3초 | 0.001초 |
| 매번 답이 다름 | 항상 같은 값 → 검증 가능 |
| "연결 끊김" 재현 어려움 | `raise LLMUnreachableError` 한 줄 |

**서비스가 `httpx`를 직접 불렀다면 이 중 무엇도 불가능하다.**

---

## 8. 예외도 인터페이스의 일부

어댑터는 **번역기**다. 인프라 예외를 도메인 예외로 바꿔서 던진다.

```
httpx.ConnectError      ──▶  LLMUnreachableError    (못 닿음)
httpx.TimeoutException  ──▶  LLMUnreachableError
404 / 500 응답           ──▶  LLMResponseError       (닿았는데 이상함)
JSON 파싱 실패           ──▶  LLMResponseError
```

```python
except httpx.HTTPStatusError as e:   # ← 자식을 먼저
    raise LLMResponseError(...) from e
except httpx.HTTPError as e:         # ← 부모를 나중에
    raise LLMUnreachableError(...) from e
```

- **순서 주의**: `HTTPStatusError`는 `HTTPError`의 자식. 순서를 바꾸면 자식 블록이 영원히 실행 안 됨
- **`from e`**: 원래 예외가 `__cause__`에 보존돼 로그에 진짜 원인까지 찍힘

서비스가 `except httpx.ConnectError:` 를 쓰는 순간, 라이브러리 교체 시 서비스를 고쳐야 한다. **그래서 예외도 번역한다.**

---

## 9. 판별 체크리스트

파일을 어디 둘지 모를 때 순서대로 적용:

**① import 문을 봐라 (가장 빠름)**
외부 라이브러리를 import 하면 → **adapter**. 안 하면 → application/domain.

**② "기술을 바꾸면 이 파일을 고쳐야 하나?"**
고쳐야 한다 → **adapter**. 아니다 → **application**.

**③ "이 로직은 누구 때문에 시작됐나?"**
바깥이 나를 불러서 → **인바운드**. 내가 필요해서 → **아웃바운드**.

**④ 삭제 테스트**
지웠을 때 안쪽이 여전히 말이 되면 → 그건 바깥이었다.

---

## 10. 흔한 실수

| 실수 | 왜 문제인가 |
|---|---|
| 폴더만 나누고 의존 방향은 무시 | **방향이 핵심.** 폴더는 결과일 뿐 |
| 도메인에 `pydantic.BaseModel` 사용 | 도메인이 라이브러리에 묶임. `dataclass`는 표준이라 안 묶임 |
| 포트 이름을 `OllamaPort`로 지음 | vLLM 어댑터가 `OllamaPort`를 상속하는 코미디 |
| 서비스에서 `httpx.ConnectError` 잡기 | 기술이 안쪽으로 샘 |
| DTO·컨트롤러를 application에 둠 | fastapi·pydantic에 묶인 물건 = adapter |
| 모든 것에 인터페이스를 만듦 | 안 바뀌는 데 쓰면 **과설계** |
| 기능끼리 가로로 import | `chat` → `embedding` 직접 호출 금지. 포트를 거쳐야 함 |

---

## 11. 우리 프로젝트 지도

```
app/
├── connect_test/
│   ├── domain/
│   │   ├── probe_result.py                     ✅ S1
│   │   └── errors.py                           ✅ S1
│   ├── application/
│   │   ├── port/outbound/
│   │   │   └── llm_probe_port.py               ✅ S2   ← 규격
│   │   └── service/
│   │       └── probe_service.py                ✅ S3   ← 유스케이스
│   └── adapter/
│       ├── outbound/llm/
│       │   └── ollama_probe_adapter.py         ✅ S4   ← 포트를 구현
│       └── inbound/web/
│           ├── dto.py                          ⬜ S6   ← 요청/응답 모양
│           └── router.py                       ⬜ S6   ← 서비스를 호출
├── config/
│   ├── settings.py                             ⬜ S5   ← 환경변수
│   └── container.py                            ⬜ S5   ← 조립
└── main.py                                     ⬜ S6
```

현재 **아웃바운드 날개만** 완성. S5~S6에서 인바운드 날개를 채우면 양쪽이 맞춰진다.

---

## 12. 이 구조가 값을 하는 순간

| 하고 싶은 것 | 고쳐야 할 것 |
|---|---|
| 맥 → 다른 PC로 추론 이전 | `.env` 한 줄 (`OLLAMA_BASE_URL`) |
| qwen3:1.7b → 더 큰 모델 | `.env` 한 줄 (`MODEL_NAME`) |
| Ollama → vLLM | 어댑터 파일 1개 + `container.py` 한 줄 |
| Ollama → OpenAI API | 어댑터 파일 1개 + `container.py` 한 줄 |
| FastAPI → CLI로도 호출 | 인바운드 어댑터 1개 추가 |
| Ollama 없이 테스트 | 가짜 어댑터 3줄 |

**여섯 경우 모두 `domain/`과 `application/`은 한 글자도 안 바뀐다.**

---

## 부록 · 용어

| 용어 | 뜻 |
|---|---|
| 포트 (Port) | "무엇이 필요한가"를 적은 인터페이스. 안쪽에 산다 |
| 어댑터 (Adapter) | 포트를 실제 기술로 구현한 것. 바깥에 산다 |
| 인바운드 / driving | 바깥이 우리를 부르는 문 |
| 아웃바운드 / driven | 우리가 바깥을 부르는 문 |
| DTO | 어댑터 경계에서 쓰는 데이터 전달 객체 |
| DI (의존성 주입) | 필요한 걸 직접 만들지 않고 밖에서 받는 것 |
| 의존성 역전 | 인터페이스를 안쪽에 둬서 화살표를 뒤집는 것 |
| 테스트 더블 | 진짜 대신 세우는 가짜 (스턴트 대역) |
| ABC | Abstract Base Class. 파이썬 표준 라이브러리 `abc` 모듈 |
| 육각형인 이유 | 특별한 의미 없음. "여러 면에 어댑터를 붙일 수 있다"는 그림일 뿐 |
