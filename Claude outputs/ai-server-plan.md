# tikitaka AI Server 구축 계획서

> 작성일: 2026-09-15 · 상태: 초안(v0.1)

---

## 1. 목표

**한 줄 요약:** 외부에서 HTTPS로 호출 가능한 LLM API를 만들되, **실제 추론을 돌리는 기기는 언제든 갈아끼울 수 있게** 한다.

| 구분 | 내용 |
|---|---|
| 최종 산출물 | 인증된 클라이언트가 호출하는 공개 LLM 엔드포인트 |
| 현재 단계 | MacBook Air + qwen3:1.7b 로 동작 검증 |
| 다음 단계 | 상시 가동 PC + 더 큰 모델로 교체 |
| 연동 대상 | tikitaka 백엔드 (헥사고날, PostgreSQL, EC2) |

### 비목표 (이번엔 안 하는 것)

- 모델 파인튜닝 / 자체 학습
- 멀티 GPU, 오토스케일링
- 벡터 DB / RAG — 나중에 붙일 수 있게 자리만 비워둠

---

## 2. 핵심 설계 원칙

> **추론 위치는 "코드"가 아니라 "설정값"이다.**

이 계획서에서 제일 중요한 문장. 지금 맥에서 돌리다가 6개월 뒤 다른 PC로 옮길 때, **바꿔야 할 게 환경변수 한 줄이어야** 한다. 이걸 만족시키려면 두 가지가 필요하다.

1. **LLM 호출을 포트/어댑터로 추상화** — 이미 쓰고 있는 헥사고날 아키텍처가 정확히 이 문제를 푸는 도구다
2. **엔드포인트를 환경변수로 주입** — `OLLAMA_BASE_URL` 하나로 로컬/원격/다른 기기 전환

---

## 3. 아키텍처

```
  [클라이언트]
       │  HTTPS (공개 인터넷)
       ▼
┌──────────────────────────┐
│  EC2 (기존 인스턴스)      │
│  ┌────────────────────┐  │
│  │ nginx (TLS, 라우팅) │  │
│  └─────────┬──────────┘  │
│            ▼             │
│  ┌────────────────────┐  │      ┌───────────────┐
│  │ ai-server(FastAPI) │  │      │ tikitaka 백엔드│
│  │  인증/제한/프롬프트  │  │      │ + PostgreSQL  │
│  └─────────┬──────────┘  │      └───────────────┘
└────────────┼─────────────┘
             │  Tailscale VPN (암호화 사설망)
             ▼
   ┌───────────────────────┐
   │  추론 기기 (교체 대상)   │
   │  ollama :11434         │
   │  지금: MacBook Air      │
   │  나중: 고성능 PC        │
   └───────────────────────┘
```

### 역할 분담

| 위치 | 책임 |
|---|---|
| **EC2 / ai-server** | 공개 진입점, API 키 인증, rate limit, 프롬프트 조립, 로깅, 타임아웃·폴백 |
| **추론 기기 / ollama** | 순수 추론만. 외부에 절대 직접 노출하지 않음 |

### 이 분리가 주는 것

- 공개 IP·도메인·TLS 인증서 관리가 **EC2 한 곳**으로 집중
- 추론 기기는 공인 IP도, 공유기 포트포워딩도 필요 없음 (Tailscale이 NAT를 통과)
- **기기 교체 = 환경변수 한 줄 + 재시작.** 클라이언트는 아무것도 모름
- EC2 인스턴스 타입을 올릴 필요 없음 → 비용 그대로

---

## 4. 네트워크 연결 방식 비교

EC2에서 집 PC로 어떻게 닿을 것인가. 이게 이 구성의 유일한 난관이다.

| 방식 | 장점 | 단점 | 판단 |
|---|---|---|---|
| **Tailscale** | 공유기 설정 불필요, 기기마다 고정 IP(`100.x.x.x`), 종단간 암호화, 무료 티어 100대 | 양쪽에 데몬 설치 필요 | **채택** |
| 포트포워딩 + DDNS | 추가 의존성 없음 | 공유기 설정 필요, 집 IP 노출, 학교/기숙사 망이면 대개 불가능 | 기각 |
| Cloudflare Tunnel | 무료, 도메인 연결 편함 | HTTP 전용, 설정이 Tailscale보다 번거로움 | 차선 |
| ngrok | 제일 빠른 시작 | 무료는 URL이 매번 바뀜, 대역폭 제한 | 임시 테스트용만 |

**Tailscale을 고른 결정적 이유:** 나중에 추론 기기를 바꿀 때, 새 기기에 Tailscale만 깔면 끝이다. 공유기를 다시 만질 필요도, 방화벽 규칙을 다시 짤 필요도 없다. "교체 가능성"이라는 이 프로젝트의 핵심 요구사항에 가장 잘 맞는다.

---

## 5. ai-server 구조 (헥사고날)

```
ai-server/
├── app/
│   ├── domain/                     # 순수 도메인. 외부 의존성 0
│   │   ├── message.py              #   Message, Role
│   │   └── conversation.py         #   Conversation
│   │
│   ├── application/
│   │   ├── port/
│   │   │   ├── inbound/
│   │   │   │   └── chat_usecase.py     # 들어오는 계약 (ABC)
│   │   │   └── outbound/
│   │   │       └── llm_port.py         # ★ 나가는 계약 (ABC)
│   │   └── service/
│   │       └── chat_service.py         # 유스케이스 구현
│   │
│   ├── adapter/
│   │   ├── inbound/web/
│   │   │   ├── router.py               # FastAPI 라우터
│   │   │   └── dto.py                  # 요청/응답 스키마
│   │   └── outbound/llm/
│   │       └── ollama_adapter.py       # ★ LLMPort 구현체
│   │
│   ├── config/
│   │   ├── settings.py                 # 환경변수 로딩
│   │   └── container.py                # 의존성 주입
│   └── main.py
├── Dockerfile
├── pyproject.toml
└── .env.example
```

### ★ 표시가 이 설계의 심장

```python
# application/port/outbound/llm_port.py
class LLMPort(ABC):
    @abstractmethod
    async def generate(self, messages: list[Message], opts: GenOptions) -> str: ...

    @abstractmethod
    def stream(self, messages: list[Message], opts: GenOptions) -> AsyncIterator[str]: ...
```

`ChatService`는 **이 인터페이스만** 안다. 구현체가 ollama인지, vLLM인지, OpenAI API인지 전혀 모른다. 그래서:

| 하고 싶은 것 | 바꿔야 할 것 |
|---|---|
| 맥 → 다른 PC로 추론 이전 | `.env` 의 `OLLAMA_BASE_URL` 한 줄 |
| qwen3:1.7b → 더 큰 모델 | `.env` 의 `MODEL_NAME` 한 줄 |
| ollama → vLLM 전환 | 어댑터 파일 하나 추가 + DI 한 줄 |
| ollama → OpenAI API 전환 | 어댑터 파일 하나 추가 + DI 한 줄 |

**도메인과 서비스 코드는 네 경우 모두 단 한 글자도 안 바뀐다.** 이게 헥사고날을 쓰는 이유이고, "나중에 갈아끼울 예정"이라는 요구사항에 대한 답이다.

### 주요 환경변수

```bash
OLLAMA_BASE_URL=http://100.x.x.x:11434   # ← 기기 교체 시 여기만 수정
MODEL_NAME=qwen3:1.7b
MAX_TOKENS=512
REQUEST_TIMEOUT=60
API_KEYS=key1,key2
```

---

## 6. 단계별 마일스톤

| # | 마일스톤 | 완료 기준 | 상태 |
|---|---|---|---|
| M0 | 로컬 ollama 동작 | `ollama run` 으로 대화 성공 | ✅ 완료 |
| M1 | HTTP API 이해 | curl로 `/api/chat` 호출, 토큰/초 측정 | 🔄 진행중 |
| M2 | FastAPI 최소 버전 | `/health`, `/chat` 이 로컬 ollama 호출 성공 | ⬜ |
| M3 | 헥사고날 리팩터링 | 포트/어댑터 분리 + SSE 스트리밍 | ⬜ |
| M4 | 도커화 | `docker compose up` 으로 ai-server 기동 | ⬜ |
| M5 | Tailscale 연결 | EC2에서 집 PC ollama 호출 성공 | ⬜ |
| M6 | 외부 공개 | 인터넷에서 인증 후 호출 성공 | ⬜ |

---

## 7. 보안

### 절대 규칙

> **Ollama의 11434 포트를 공개 인터넷에 노출하지 않는다.**

Ollama에는 인증이 **아예 없다.** 노출되는 순간 누구나 모델을 실행·다운로드·삭제할 수 있고, 남의 GPU로 채굴하듯 쓰는 사례가 실제로 있다. Tailscale 사설망 안에서만, EC2에서만 닿게 한다.

### 계층별 방어

| 계층 | 조치 |
|---|---|
| 추론 기기 | ollama를 `127.0.0.1` + Tailscale 인터페이스에만 바인딩 |
| Tailscale ACL | EC2 노드에서 오는 11434 트래픽만 허용 |
| EC2 보안그룹 | 443/80만 개방. 11434는 절대 열지 않음 |
| ai-server | API 키 헤더 검사, IP별 rate limit |
| 요청 단위 | `num_predict` 상한, 요청 타임아웃, 입력 길이 제한 |

`num_predict` 상한이 없으면 요청 하나가 추론 기기를 몇 분씩 점유한다. 공개 API에서는 필수.

---

## 8. 리스크

| 리스크 | 영향 | 완화 |
|---|---|---|
| 집 PC 꺼짐 / 맥 덮개 닫힘 | 서비스 전면 중단 | 헬스체크 + 명확한 503 응답. 중장기: 상시 가동 PC로 이전 |
| 집 인터넷 업로드 대역폭 | 응답 지연 | 텍스트라 트래픽 자체는 작음. 스트리밍으로 체감 개선 |
| 모델 콜드스타트 | 첫 요청 수 초 지연 | ollama `keep_alive` 설정으로 메모리 상주 |
| 추론 기기 IP 변경 | 연결 끊김 | Tailscale이 고정 `100.x` IP 부여 — 해결됨 |
| 동시 요청 폭주 | 추론 기기 과부하 | ai-server에서 동시성 제한 + 큐잉 |

---

## 9. 다음에 결정할 것

- [ ] 대화 이력을 PostgreSQL에 저장할지 (저장한다면 tikitaka 백엔드와 스키마 공유 여부)
- [ ] 목표 모델 스펙 — 새 PC의 VRAM 기준으로 7B/14B 중 선택
- [ ] 도메인 및 TLS 인증서 (Let's Encrypt)
- [ ] ai-server를 tikitaka 레포에 넣을지, 별도 레포로 뺄지

---

## 부록: 용어

| 용어 | 뜻 |
|---|---|
| Ollama | 로컬에서 LLM을 돌리고 HTTP API로 노출해주는 런타임 |
| 포트(Port) | 헥사고날에서 "무엇을 할 수 있는지"를 정의한 인터페이스 |
| 어댑터(Adapter) | 포트를 실제 기술로 구현한 것 (예: OllamaAdapter) |
| NDJSON | 한 줄에 JSON 하나씩 흐르는 스트리밍 형식. Ollama가 쓰는 방식 |
| SSE | Server-Sent Events. 브라우저가 이해하는 스트리밍 표준 |
| Tailscale | WireGuard 기반 P2P VPN. 기기들을 하나의 사설망으로 묶어줌 |
