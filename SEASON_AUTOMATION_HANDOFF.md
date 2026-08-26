# 티키타카 — 시즌/랭킹 자동화 작업 인수인계 문서

> 다른 개발자·AI가 이 작업을 이어받기 위한 문서다.
> **1단계(도메인 규칙 확정)는 완료**되었고, 2~10단계가 남아 있다.
> **코드는 아직 한 줄도 수정하지 않았다.** 인프라(EC2 + RDS 배포)만 구축된 상태다.
>
> **2026-08-11 개정**: 코드 대조 검증(문서 서술과 실제 코드 일치 확인) 후 미결 사항 1·2·3·4번 확정 반영(§5 확정 내역 — 홀수는 셀프 미션 매치(BYE), 0명 동네는 매칭 제외),
> 발견된 문제 수정 — 시드 SQL 날짜 지침 명확화(2단계), 라운드 생성 단일 트랜잭션 명시(7단계 멱등성),
> 8-6 재계산 범위와 삭제 정책의 연결(미결 5번·8-6), 홀수 부전승의 시즌 합산 불이익 명시(미결 4번), 총점 오차 수치 정정(§5).

---

## 1. 프로젝트 개요

**티키타카** — 지역(Location) 단위로 팀을 이뤄 미션을 수행하고, AI가 채점한 점수로 **지역 랭킹**(주 지표)을 겨루는 서비스.

### 기술 스택

| 항목 | 값 |
|---|---|
| 언어/런타임 | Java 21 |
| 프레임워크 | Spring Boot 4.1.0 |
| ORM | Spring Data JPA / Hibernate 7.4.1 |
| DB | PostgreSQL 16 |
| 아키텍처 | **헥사고날 아키텍처** |
| 빌드 | Gradle (Kotlin DSL) |
| 외부 연동 | AWS S3 (이미지), Gemini API (AI 채점) |

### 패키지 구조 (헥사고날) — 실제 코드 기준

```
com.raon.tikitaka
├── adapter/
│   └── <도메인>/
│       ├── XxxController.java          # 웹 어댑터 (도메인 폴더 바로 아래)
│       ├── dto/                        # 요청/응답 DTO
│       └── out/
│           ├── XxxJpaRepository.java   # Spring Data JPA 인터페이스
│           └── XxxPersistenceAdapter.java  # 아웃바운드 포트 구현
├── application/
│   └── <도메인>/
│       ├── XxxService.java             # 유스케이스 구현 (@Service, 포트 인터페이스를 implements)
│       ├── in/  XxxUseCase.java        # 인바운드 포트 (인터페이스)
│       └── out/ XxxRepositoryPort.java # 아웃바운드 포트 (인터페이스)
├── domain/
│   └── board/ enums/ keyword/ location/ match/ post/ product/ ranking/ token/ user/ userItem/
└── global/
    ├── exception/
    └── response/
```

**주의 — `adapter/in/` 이라는 폴더는 존재하지 않는다.** 컨트롤러는 `adapter/<도메인>/` 바로 아래에 둔다. 서비스 구현체도 `application/<도메인>/` 바로 아래에 있고, `in/`·`out/`에는 인터페이스만 있다.

**실제 예시** (기존 코드 그대로)

```
adapter/board/BoardController.java
adapter/board/dto/BoardResponse.java
adapter/board/out/BoardJpaRepository.java
adapter/board/out/BoardPersistenceAdapter.java

application/board/BoardService.java          # implements GetBoardUseCase
application/board/in/GetBoardUseCase.java
application/board/out/BoardRepositoryPort.java
```

**이 작업에서 새로 만들 도메인 폴더**

`Stage`·`Match` 엔티티가 `domain/match/`에 있으므로 라운드·매칭 관련 코드는 **`match` 도메인**에 둔다. 랭킹은 `ranking`, 유저는 `user`.

```
application/match/    ← 라운드 생성, 매칭 (Stage·Match를 다루므로)
application/ranking/  ← 점수 누적, 시즌 확정
application/user/     ← 휴면 전환 (현재 out/ 포트만 있고 서비스가 없음)
```

스케줄러와 어드민 컨트롤러도 도메인 폴더 규칙을 따른다.

```
adapter/match/MatchScheduler.java       (스위칭 적용 + 라운드 생성 + 매칭)
adapter/user/UserScheduler.java         (휴면 전환 — 로그인 기능 완성·병합 후에 만들 것)
adapter/ranking/RankingScheduler.java   (시즌 확정)
adapter/match/MatchAdminController.java (수동 트리거)
```

### 이미 구현된 AI 채점 경로

```
application/review/in/ReviewUseCase.java     # evaluate(mission, content, image, contentType) → AiReviewResult
application/review/AiReviewResult.java       # record AiReviewResult(Integer score, String review)
application/review/ReviewService.java
application/review/out/AiReviewPort.java
adapter/review/out/GeminiAdapter.java        # Gemini API 호출
```

**채점은 `PostController.createPost()` 안에서 일어난다.** 컨트롤러가 Gemini를 호출하고, 그 결과 `score`를 `createPost(...)` 인자로 넘겨 저장한다. `PostService`는 이미 매겨진 점수를 받기만 한다.

### 인프라 (구축 완료)

- **EC2** `t4g.micro` (arm64, ap-northeast-2a, RAM 1GB + swap 2GB) — 탄력적 IP 연결됨
- **RDS** PostgreSQL 16.14, `db.t4g.micro`, 단일 AZ, 퍼블릭 액세스 없음 (EC2 보안그룹에서만 접근)
- 배포: EC2에서 `docker compose up -d --build`
- compose 파일 2개 분리
  - `docker-compose.yml` — 배포용 (app만, DB는 RDS)
  - `docker-compose.local.yml` — 로컬용 (db 컨테이너 추가)
  - 로컬 `.env`의 `COMPOSE_FILE` 변수로 자동 병합

**RDS 운영 시간표 (KST)** — 배치 스케줄을 짤 때 피해야 함

| 시간 | 작업 |
|---|---|
| 매일 03:00~03:30 | RDS 자동 백업 |
| 월요일 04:00~04:30 | RDS 유지 관리 |
| **05:00 이후** | **배치 안전 구간** |

---

## 2. 용어

### `Stage` = 라운드

`Stage` 엔티티 한 행이 **라운드 하나**다.

```
Stage 행 1개 = 라운드 1개 = 7일
Stage 행 4개 = 시즌 1개 = 28일
```

이 문서의 산문에서는 **"라운드"** 라고 쓰고, 클래스·컬럼 이름을 가리킬 때만 `Stage` / `stage_id`를 쓴다. 엔티티 이름은 `Match.stage` 등 기존 참조 때문에 `Stage`로 유지한다.

### 점수 계산에 쓰는 기호

| 기호 | 뜻 | 저장 위치 |
|---|---|---|
| `K` | 고정 배율 (67.76) | `application.yaml` |
| `C` | 매칭 시점의 평균 동네 인원 = 전체 ACTIVE 유저 수 / 동네 수 | `Stage.avgLocationMemberCount` |
| `n` | 우리 팀의 매칭 시점 ACTIVE 인원수 | `Match.team1MemberCount` / `team2MemberCount` |
| `minTeamSize` | 조정치 분모의 하한 (5) | `application.yaml` |

---

## 3. 현재 도메인 모델 (변경 전)

### 데이터 흐름

```
Stage(season, round)
 └─ Match(team1: Location, team2: Location, win_team, mission)
     └─ Board (Match와 1:1)
         └─ Post(user, content, score ← AI 점수, ai_review)
```

매치가 열리면 게시판이 생성되고, 유저가 미션 수행 게시물을 올리면 Gemini가 `Post.score`를 매긴다.

### 주요 엔티티

```java
// domain/match/Stage.java
@Entity @Table(name = "stage")
public class Stage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stage_id") private Long stageId;

    @Column(name = "season", nullable = false) private Integer season;
    @Column(name = "round", nullable = false) private Integer round;
    @Column(name = "started_at", nullable = false) private LocalDateTime startedAt;
    @Column(name = "is_active", nullable = false) @ColumnDefault("true") private boolean isActive;
}

// domain/match/Match.java
@Entity @Table(name = "match")
public class Match {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "match_id") private Long matchId;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "stage_id", nullable = false) private Stage stage;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "team1_id", nullable = false) private Location team1;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "team2_id", nullable = false) private Location team2;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "win_team") private Location winTeam;

    @Column(name = "match_type") private MatchType matchType;   // NORMAL, EVENT — @Enumerated 없음(ORDINAL 저장)
    @Column(name = "mission", nullable = false) private String mission;
}

// domain/post/Post.java (일부)
@Entity @Table(name = "post")
public class Post {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "post_id", columnDefinition = "uuid") private UUID postId;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false) private Users userId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "board_id", nullable = false) private Board board;

    @Column(name = "content")     private String content;
    @Column(name = "post_image")  private String postImage;
    @Column(name = "score")       private Integer score;      // AI 점수 (0~100)
    @Column(name = "ai_review")   private String aiReview;
    @Column(name = "location")    private String location;    // 동네 "이름" 문자열
    @Column(name = "is_active", nullable = false) private boolean isActive;
    // createdAt, updatedAt

    // 세터 없음. 값 주입은 정적 팩토리로만 (updateContent/deactivate 변경 메서드는 있음)
    public static Post create(Users author, Board board, String content, String postImage,
                              Integer score, String aiReview, String location) { ... }
}

// domain/user/Users.java (일부)
@Entity @Table(name = "users")
public class Users {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id", columnDefinition = "uuid") private UUID userId;

    @Column(name = "user_name", nullable = false, unique = true) private String userName;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "main_location_id") private Location mainLocation;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "sub_location_id")  private Location subLocation;
    @Enumerated(EnumType.STRING) @Column(name = "role", nullable = false) private UserRole role;
    @Column(name = "point", nullable = false) @ColumnDefault("0") private Integer point;
    // createdAt, updatedAt
}

// domain/ranking/UserRanking.java
@Entity @Table(name = "user_ranking", indexes = @Index(name="idx_user_ranking", columnList="user_rank"))
public class UserRanking {
    @Id @Column(name = "user_id", columnDefinition = "uuid") private UUID userId;   // PK가 user_id
    @Column(name = "user_rank")  private Integer userRank;
    @Column(name = "user_score") private Integer userScore;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
}

// domain/ranking/LocationRanking.java
@Entity @Table(name = "location_ranking", indexes = @Index(name="idx_location_ranking", columnList="location_rank"))
public class LocationRanking {
    @Id @Column(name = "location_id") private Long locationId;                      // PK가 location_id
    @Column(name = "location_rank")  private Integer locationRank;
    @Column(name = "location_score") private Integer locationScore;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
}
```

### 지금 없는 것 (중요)

- **매치·게시판을 생성하는 코드가 전혀 없다.** `BoardController`에는 목록 조회 GET 하나뿐이고, 매칭 로직도 시드도 없다. 현재 게시판은 수동으로 DB에 넣은 것으로 보인다
- **랭킹 서비스·어댑터가 없다.** 엔티티 두 개만 존재하고 아무도 읽거나 쓰지 않는다
- **`Stage.is_active`를 읽는 코드가 없다.** `Stage`를 참조하는 곳은 `BoardResponse`(`season`/`round` 표시)와 `BoardJpaRepository`의 `join fetch m.stage` 둘뿐이며, 어느 쪽도 `is_active`를 보지 않는다 (제거해도 안전)
- **`@EnableScheduling`이 없다.** `TikitakaApplication`에 `@SpringBootApplication`만 있다
- **로그인·회원가입 코드가 없다.** `Token`·`LoginProvider` 엔티티는 있으나 이를 쓰는 리포지토리·서비스·컨트롤러가 하나도 없다. **로그인 기능은 개발이 끝났고 브랜치 병합만 남은 상태다(2026-08-11 사용자 확인).** 단, 이 저장소의 로컬·origin 브랜치(`main`·`domain`·`TK-59`)에는 해당 코드가 아직 보이지 않으므로, **병합 시점에 4단계의 확인 체크리스트를 반드시 점검할 것.** 병합 전까지 휴면 배치를 켜지 않는 규칙은 그대로다
- **`application.yaml`에 `ranking:` / `season:` 설정 블록이 없다.** 3단계에서 추가해야 한다

---

## 4. 해결하려는 문제

> "랭킹, 시즌제 게시판 등을 매주·매달 수동으로 설정해줘야 하는데 이걸 자동화하고 싶다."

### 문제 1 — 매치·게시판을 만드는 주체가 없다

**이것이 자동화의 본체다.** 라운드가 바뀌면 새 매치와 게시판이 생겨야 하는데 그 코드가 없다. 지금은 사람이 DB에 직접 넣고 있다.

### 문제 2 — 라운드 경계를 판정할 수 없다

`Stage`에 `ended_at`이 없어서 **시간만으로 "지금 어느 라운드인지" 판단할 수 없다.** 누군가 `is_active`를 뒤집어줘야 하고, 그 작업이 실패하면 라운드가 넘어가지 않는다. `(season, round)` 유니크 제약도 없어 중복 생성이 가능하다.

### 문제 3 — 랭킹에 라운드/시즌 차원이 없다

`LocationRanking`의 PK가 `location_id`, `UserRanking`의 PK가 `user_id`라서 **대상당 행이 하나뿐이다.**

- 점수를 계속 더하면 시즌 1·2·3 점수가 한 칸에 섞인다
- 시즌이 바뀔 때 0으로 초기화하면 이번엔 지난 시즌 기록이 사라진다
- "지난 시즌 순위" 조회 불가

`(stage_id, location_id)`로 행을 나누면 게시물이 올라올 때 **해당 라운드 행을 찾아 더하기만** 하면 되고, 라운드가 바뀌면 새 행이 자동으로 생긴다.

### 문제 4 — 점수 계산에 필요한 값이 남지 않는다

조정치 계산에 `n`(우리 팀 인원)과 `C`(평균 동네 인원)가 필요한데 **둘 다 어디에도 저장되지 않는다.** 매번 그 순간의 인원을 세면 라운드 중 가입·탈퇴에 따라 같은 AI 점수의 최종 점수가 달라진다(드리프트).

또 게시물의 소속 팀을 `post.userId.mainLocation`으로 판정하면 **유저가 소속을 바꿨을 때 과거 게시물의 소속까지 바뀐다.**

### 문제 5 — 유령 계정이 인원수를 왜곡한다

팀 인원수가 조정치의 분모이므로, 활동하지 않는 계정이 많은 팀이 불이익을 받는다.

---

## 5. 확정된 도메인 규칙 (1단계 — 완료)

### 시즌/라운드 주기

```
라운드 = 7일
시즌   = 4라운드 = 28일

season 1: round 1, 2, 3, 4
season 2: round 1, 2, 3, 4
```

**달력 월이 아니라 고정 28일.** 시즌 길이가 항상 같아야 시즌 간 비교가 공정하다.

첫 라운드(season 1, round 1)의 `Stage` 행은 **수동으로 시드**한다. **`started_at`은 KST 자정 기준으로 넣을 것** — 이 값이 이후 모든 라운드 경계 시각을 결정한다.

라운드 경계는 자정이지만 배치는 05:00~05:20에 돈다. **이 시차 때문에 매치·게시판은 라운드 시작 하루 전에 미리 만든다**(7단계 참조). 경계 시각에 실행되는 코드를 두지 않기 위한 것이다.

### 점수 공식

```
개인 점수  = round(AI점수 × K)
지역 기여  = round(AI점수 × K × 조정치)

조정치 = C / max(n, minTeamSize)
```

**개인 점수에는 조정치를 곱하지 않는다.** 팀 규모와 무관하게 순수 AI 점수로만 겨룬다.

**계산 예시** (`C` = 100, AI 80점, 전원 참여)

| 동네 | 인원 `n` | 조정치 | 개인 점수 | 1인당 지역 기여 | 동네 총점 |
|---|---|---|---|---|---|
| 시골동 | 30 | 100/30 = 3.333 | 5,421 | 18,069 | 542,070 |
| 서면동 | 200 | 100/200 = 0.5 | 5,421 | 2,710 | 542,000 |
| 유령동 | 1 | 100/**5** = 20 | 5,421 | 108,416 | 108,416 |

> 총점은 **게시물마다 반올림한 뒤 합산**한 값이다(8-2 구현이 그렇게 되어 있다). 반올림 없이 계산하면 둘 다 542,080이 나오지만, 실제로는 위 값이 된다. 30명과 200명 동네의 총점이 약 0.013%(70점) 차이로 사실상 일치하는 것이 이 설계의 목표다.

정상 크기 동네는 인구와 무관하게 대등해지고, 1~2명짜리 극단 케이스만 `minTeamSize`에 막힌다.

> 위 값은 `double`로 끝까지 계산하고 마지막에 한 번만 반올림한 결과다. 조정치를 3.33으로 미리 반올림하면 18,051이 나와 값이 어긋난다.

### 설정값 (`application.yaml`에 외부화 — 하드코딩 금지)

> **아직 파일에 없다.** 현재 `application.yaml`에는 `spring`, `server.port`, `cloud.aws.s3`, `gemini` 블록만 있다. 아래를 **3단계에서 추가**할 것.

```yaml
ranking:
  base-multiplier: 67.76      # K
  min-team-size: 5            # 조정치 분모의 하한
  dormant-days: 7             # 휴면 전환 기준

season:
  round-days: 7               # 라운드 1개 길이
  rounds-per-season: 4        # 시즌당 라운드 수 → 시즌 = 28일
  prefetch-days: 30           # 미래 라운드(Stage 행)를 며칠치 미리 만들어둘지
  match-open-ahead-days: 1    # 매치·게시판을 라운드 시작 며칠 전에 만들어둘지
```

### 결정 배경

| 결정 | 내용 | 이유 |
|---|---|---|
| 개인 랭킹에 조정치 **미적용** | `AI × K` 만 | 조정치를 쓰면 "어느 팀 소속이냐"가 개인 순위를 좌우함 |
| `n`은 **매칭 시점에 확정** | `Match`에 컬럼 | 라운드 중 인원 변동이 점수에 영향을 주지 않음 |
| `C`도 **매칭 시점에 확정** | `Stage`에 컬럼 | 유저가 계속 가입하므로 실시간 값을 쓰면 늦게 올린 사람이 유리해짐 |
| 조정치 분자에서 **상대 팀 인원수 제거** | `두 팀 평균` → `C` | 아래 "폐기된 공식" 참조 |
| 조정치 상한 대신 **분모 하한** | `C / max(n, 5)` | 상한 방식(`min(ratio, 2.5)`)은 평균의 40% 미만 동네를 전부 캡에 걸리게 해 작은 동네가 손해를 봤다 |
| `K`를 **소수(decimal)** | 67.76 | 정수 K는 점수가 둥글게 나옴. 소수면 AI가 5의 배수를 줘도 결과가 흩어짐 |
| 계산은 `double`, **최종만 반올림** | 중간 반올림 금지 | 중간에 int로 자르면 해상도가 뭉개짐 |
| 점수는 **실시간 누적** | 게시물 작성 시 즉시 | 조정치 입력값이 작성 시점에 이미 확정되어 있음 |
| 랭킹 행은 **라운드 단위** | `(stage_id, location_id)` | 가장 잘게 저장해야 라운드·시즌 둘 다 만들 수 있음 |
| **시즌 최종 순위는 별도 테이블에 박제** | `season_location_result` | 동네 프로필의 시즌별 등수 히스토리용. 과거 등수가 흔들리면 안 됨 |
| 지역 스위칭은 **다음 라운드부터** | `pendingLocationSwap` 예약 | 라운드 중 소속이 바뀌면 확정된 `n`과 실제 인원이 어긋남 |
| `sub_location`은 **점수와 무관** | 스위칭 대상일 뿐 | `main`+`sub`로 세면 한 유저가 두 동네에 중복 카운트되어 `C`가 어긋남 |
| 참가 팀이 아닌 유저의 게시물은 **403 거부** | `null` 저장 대신 예외 | "모든 게시물은 참가 팀 소속"을 DB가 보장하게 함 |
| 라운드 중 가입자의 참여는 **그대로 허용** | `n` 재설정 안 함 | 아래 "라운드 중 가입자" 참조 |

### 폐기된 공식 — 왜 바꿨는지

**원래 공식**은 `조정치 = min(두 팀 평균 인원수 / 우리 팀 인원수, 2.5)` 였다.

조정치 자체는 비율이라 규모에 흔들리지 않는다(30 vs 40 → 1.167, 200 vs 250 → 1.125). **문제는 동네 점수가 팀원 기여의 합계라는 점이었다.**

```
동네 총점 = Σ(AI × K × 평균인원/n) = K × 평균인원 × AI평균
```

1인당 기여는 비슷해도 **더하는 사람 수가 다르므로 총점이 인구에 비례**한다.

| 매치 | 1인당 기여 | 동네 총점 |
|---|---|---|
| 30 vs 40 | 6,324 | 189,720 |
| 200 vs 250 | 6,098 | **1,219,600** |

**6.4배 차이.** 지역 랭킹은 모든 동네를 한 줄로 세우는 것이므로, 인구 많은 동네가 매 시즌 우승하고 작은 동네는 순위표 바닥에 고정된다.

**해결**: 분자에서 상대 팀 인원수를 빼고 전역 값 `C`로 대체했다. 상대 팀 인원수는 공정성에 기여하는 바가 없다 — 두 팀 모두 각자의 인원으로 나누는 것만으로 이미 대등하기 때문이다.

### 새 공식의 성질

```
동네 총점 = Σ(AI_i × K × C/n) = K × C × (ΣAI_i / n)
                              = K × C × 참여율 × 참여자 AI 평균
```

**인구수가 사라지고 "얼마나 많이 참여했고 얼마나 잘했나"만 남는다.**

`C`가 커지면(유저 증가) 모든 동네 점수가 동일 비율로 커지므로 **순위에는 영향이 없다.** 시즌이 지날수록 절대 점수가 인플레되지만 등수 비교에는 문제가 없다.

### 라운드 중 가입자 — 재설정하지 않는다

`n`은 라운드 시작 시점의 인원이다. 라운드 중 가입한 유저도 글을 쓸 수 있으므로 실제 참여자가 `n`을 넘어 총점이 부풀 수 있다. **이를 보정하지 않는다.**

`n`을 재설정하면 두 가지 부작용이 생긴다(30명 → 35명 예시).

| | `n` 고정 (30) | `n` 재설정 (35) |
|---|---|---|
| 시점별 공평 | 차이 없음 | **먼저 낸 사람이 17% 유리** (1/30 vs 1/35) |
| 성장 보상 | 총점 `K×C×93.3` | 총점 `K×C×80` — 5명을 데려와 전원 참여시켜도 **총점 그대로** |

재설정이 얻는 것은 "참여율이 100%를 넘지 않는다"는 지표상의 깔끔함뿐인데, 대가로 타이밍 불공평과 성장 억제를 받는다. **부풀림은 버그가 아니라 성장 보너스로 본다.** 신규 가입은 모든 동네에 고르게 생기므로 순위 영향도 작다.

### 미결 사항 → 확정 내역 (2026-08-11 사용자 결정)

1. **`UserRanking` — 함께 구현하기로 확정.** 5단계에서 `LocationRanking`과 동일한 구조로 `(stage_id, user_id)` 유니크로 재설계하고, **8-2 누적 코드에 개인 점수(`round(AI × K)`) 누적 한 줄**과 **8단계 영속화 파일 3개**(`UserRankingJpaRepository`, 어댑터, 포트 메서드)를 함께 만든다. 개인 점수에는 조정치를 곱하지 않는다(§5 점수 공식 참조)
2. **매칭 알고리즘 — 랜덤 + 직전 라운드 상대 회피로 확정 (7단계 구현 중 재결정 — "인원수 근접" 안 폐기).** 참가 동네를 무작위로 섞어 인접 짝짓기하되, 직전 라운드에서 붙었던 짝이 나오면 재셔플한다(최대 10회, 회피 불가능하면 리매치 허용 + 경고 로그).
   - **폐기 근거**: ① 인원 균형은 조정치(`C/n`)가 이미 수학적으로 보정하므로 인원수 근접의 점수상 실익이 없다 ② 인원수 정렬 기반 인접 짝짓기는 동네 인원이 안정적이면 **정렬 순서가 매주 같아져 매주 같은 상대와 붙는** 부작용이 있다 (유저 경험 문제)
   - 직전 라운드 판정: 라운드는 빈틈없이 이어지므로 `ended_at = 이번 라운드의 started_at`인 라운드. BYE(셀프) 매치는 상대가 아니므로 회피 대상에서 제외
   - **ACTIVE 0명 동네는 매칭에서 제외 (확정).** 주민이 없어 글 쓸 사람도 없으므로 매치·게시판을 만들지 않는다. 상대 동네가 빈 게시판과 대결하는 구도를 방지한다. 시즌 확정 시에는 기존 규칙("모든 동네에 행 생성")대로 `final_score = 0`으로 기록된다. `C` 계산의 분모(동네 수)는 기존 정의(전체 Location 수) 유지 — 모든 동네에 동일하게 적용되는 값이라 순위에 영향이 없다
3. **미션 조달 — 키워드 조합 + AI 다듬기로 확정 (2026-08-11 재결정, `mission_pool` 안은 폐기).** 기존 `keyword` 테이블(명사/형용사)이 원래 미션 생성용이므로 이를 재사용한다. 매칭 배치(7단계)가 키워드를 조합해 Gemini로 자연스러운 미션 문장을 만들고, 결과를 `Match.mission`에 **복사(스냅샷)** 해 저장한다. **추가 스키마 변경 없음**
   - **Gemini 실패 시 템플릿 폴백 필수** — 배치가 외부 API 때문에 라운드를 못 여는 일이 없어야 한다. 실패하면 "{형용사} {명사}을(를) 찾아 찍어보세요" 같은 고정 템플릿 조합으로 대체
   - `keyword` 테이블에 **시드 데이터 필수** — 비어 있으면 미션을 만들 수 없어 라운드 전체가 실패한다. 7단계에 "키워드 0건이면 에러 로그 + 라운드 중단" 방어를 넣는다
   - 구현 전에 `keyword.type`의 실제 저장값('명사'/'형용사' 등)을 DB에서 확인할 것 — 이 테이블을 쓰는 코드가 없어 문서만으로는 값 형식을 알 수 없다
4. **동네 수가 홀수일 때 — 셀프 미션 매치(BYE)로 확정.** 부전승 동네에도 매치를 만들되 `team2 = team1` + `MatchType.BYE`로 두고 게시판을 정상 개설한다.
   - **근거**: 단순 부전승은 그 라운드 점수가 0인데 시즌 순위는 4라운드 **합계**라 부전승 동네가 구조적으로 불리하고, 주민들이 그 주에 할 것이 없다. 점수 공식 `K × C × 참여율 × AI평균`은 **상대 동네와 무관**하므로 셀프 매치에서도 점수가 정상 누적되어 두 문제가 모두 해소된다
   - `team1_member_count = team2_member_count = 그 동네 인원`으로 저장하면 8-2 점수 계산이 분기 없이 그대로 동작한다. 유니크 제약 `(stage_id, team1_id)`·`(stage_id, team2_id)`도 통과한다. `win_team`은 null 유지(승패 없음)
   - **부전승 로테이션**: 인원수 정렬 후 "남는 마지막 동네"를 부전승으로 하면 **항상 최소 인원 동네만 부전승**이 된다. 대신 매칭 전에 **BYE 이력이 가장 오래된(또는 없는) 동네를 먼저 부전승으로 뽑고**, 나머지를 인원수 근접으로 짝짓는다. BYE 이력은 `match`에서 `match_type = 'BYE'`로 조회하면 되므로 별도 컬럼이 필요 없다. 동률(BYE 이력이 없는 동네가 여럿)일 때는 `location_id` 순으로 안정화한다
   - `MatchType`에 `BYE` 값 추가는 **3단계**(ORDINAL → STRING 전환 시)에 함께 한다. 화면에는 description("미션 위크" 등)으로 표시 — `BoardResponse`가 이미 `matchType` description을 내려주므로 프론트 전달 경로는 있다
5. **게시물 삭제 시 점수 — "뺀다"로 확정 (8단계 진입 시 결정).** 삭제(soft delete, `Post.deactivate()`) 시 그 게시물의 개인·동네 점수를 랭킹에서 차감한다.
   - **근거**: "점수 받고 삭제"하는 악용을 막고, 누적값과 원본(활성 게시물)이 항상 일치한다
   - **구현**: 스냅샷 컬럼들(`Post.score`·`teamLocation`, `Match.n`, `Stage.C`)로 저장 시와 동일한 공식으로 재계산해 **음수 delta로 UPSERT** — 별도 컬럼 불필요. 라운드는 시계가 아니라 `post.board.match.stage` 사슬로 유도(8-2와 동일 원칙)
   - 이미 종료된 라운드의 게시물을 삭제해도 차감한다(시즌 진행 중이면 합산에 반영됨). 시즌 확정 후라면 `location_ranking`만 변하고 확정 결과는 그대로다(6번 정책과 일관)
   - 8-6 검증 배치를 만든다면 재계산은 **활성 게시물만** 대상 (이 정책과 일치)
6. **시즌 확정 후 원본 변경 — "번복하지 않는다"로 확정 (8단계 진입 시 결정).** `season_location_result`는 한 번 쓰이면 절대 바뀌지 않는다. 확정 후 위반 게시물이 삭제되어도 발표된 등수는 유지 — 유저가 받은 결과가 소리 없이 바뀌는 경험을 막는 것을 우선한다. 심각한 부정행위는 다음 시즌 제재 등 운영으로 해결한다

### 별개 작업 (나중에 검토)

현재 `GEMINI_PROMPT`는 위반 시 감점 계산까지 LLM에게 시킨다("3,4,5번을 어긴 경우에는 산정된 점수에 0.2를 곱해줘"). LLM에게 산술을 맡기면 불안정하다. **위반 여부만 받아 Java에서 곱하는 방식**을 권한다.

```json
{ "score": 73, "review": "...", "violations": [3] }
```

`AiReviewResult` record에 `violations` 필드를 추가하면 된다.

참고: AI 채점 해상도(점수가 5의 배수로 몰리는 문제)는 이미 해결되어 있다. 프롬프트에 "5로 나누어떨어지게 하지 말라"는 지시가 있고 `K`가 소수라 결과가 흩어진다.

또 하나(이번 작업 범위 밖, 상점 도메인): `Product.productType`에도 `@Enumerated`가 없어 **ORDINAL(정수)로 저장**되고 있다. `Match.matchType`과 같은 잠재 위험(enum 중간에 값 추가 시 기존 데이터 의미가 밀림)이므로, 상점 쪽을 손볼 때 `@Enumerated(EnumType.STRING)` 전환을 검토할 것.

---

## 6. 랭킹 시스템 전체 그림

테이블 3개가 각각 무엇을 담는지 먼저 이해해야 한다.

### ① `stage` — 달력 + 라운드별 스냅샷

시간 구간을 미리 정의해둔 표. 여기에 **그 라운드의 `C` 값**도 함께 박아둔다.

| stage_id | season | round | started_at | ended_at | avg_location_member_count | swap_applied |
|---|---|---|---|---|---|---|
| 1 | 1 | 1 | 8/4 | 8/11 | 100.0 | true |
| 2 | 1 | 2 | 8/11 | 8/18 | 103.5 | true |
| 3 | 1 | 3 | 8/18 | 8/25 | 107.1 | true |
| 4 | 1 | 4 | 8/25 | 9/1 | 110.0 | false |
| 5 | 2 | 1 | 9/1 | 9/8 | 112.8 | false |

시즌 1은 stage 1~4, 시즌 2는 stage 5부터.

> `C`가 "점수 파라미터"인데 달력 테이블에 들어가는 것이 어색해 보일 수 있다. 그러나 `C`는 **라운드마다 하나뿐인 전역 값**이라 다른 곳에 두면 중복이 생긴다. `Stage`가 "라운드의 모든 것"을 담는 표라고 보면 일관된다.

### ② `location_ranking` — 진행 중인 기록 (라운드별)

**게시물이 올라올 때마다 실시간으로 쌓이는 곳.**

| stage_id | location_id | location_score |
|---|---|---|
| 1 | 서면동 | 47,231 |
| 1 | 남포동 | 39,102 |
| 2 | 서면동 | 51,003 |
| 2 | 남포동 | 62,880 |

`(stage_id, location_id)` 조합당 한 행. 게시물이 올라오면 코드는 이렇게 동작한다.

```
게시물 → 게시판 → 매치 → 라운드(stage 2)
→ (stage 2, 서면동) 행에 점수 더하기 (없으면 생성)
```

주가 바뀌면 게시판이 속한 라운드가 달라지므로 자동으로 다른 행에 쌓인다. **라운드 경계에 실행되는 코드가 없다.**

### ③ `season_location_result` — 확정된 역사 (시즌별)

시즌이 끝나면 그 시즌의 라운드 점수를 합산해 등수를 박제하는 곳.

| season | location_id | final_rank | final_score |
|---|---|---|---|
| 1 | 남포동 | 1 | 210,445 |
| 1 | 서면동 | 2 | 198,332 |

**한 번 쓰이면 바뀌지 않는다.** 동네 프로필에서 "시즌 1: 2등, 시즌 2: 1등" 히스토리를 보여줄 때 이 표만 읽는다.

### 전체 흐름

```
8/3  05:10  (하루 전) 예정 소속으로 인원 집계 → C 계산 → 8/4 라운드의 매치·게시판 생성
8/4  00:00  라운드 시작 — 실행되는 코드 없음. 필터 조건이 바뀌며 새 게시판이 나타남
8/4  00:10  지역 스위칭 적용
8/4 ~ 9/1   게시물이 올라올 때마다 → ② 에 실시간 누적
8/31 05:10  (하루 전) 9/1 시작 예정인 시즌 2 라운드 1의 매치·게시판 생성
9/1  00:00  시즌 2 라운드 1 시작 → ② 의 stage 5부터 다시 쌓임
9/1  00:10  지역 스위칭 적용
9/1  05:20  시즌 1 종료 확인 → ② 의 stage 1~4를 합산 → ③ 에 등수 기록
```

### ②와 ③이 둘 다 필요한 이유

- **②만 있으면** — 시즌 등수를 볼 때마다 4주치를 합산하고 전체 동네와 비교해야 한다(윈도우 함수 필요). 나중에 게시물이 삭제되면 과거 등수가 소리 없이 바뀐다
- **③만 있으면** — "이번 주 우리 동네 몇 위?"를 보여줄 수 없다. 시즌 중간에는 데이터가 없다

**②는 진행 중인 기록, ③은 확정된 역사다.**

---

## 7. 작업 단계 (2~10)

### 순서의 근거

스키마를 먼저 전부 갖춘 뒤 매칭을 만들고, 매칭이 돌기 시작한 다음에 게시판 필터를 건다.

**필터를 매칭보다 먼저 걸면 안 된다.** 매치를 만드는 코드가 없는 상태에서 "현재 라운드만" 필터를 넣으면, 라운드가 넘어가는 순간 게시판 목록이 영구히 빈다.

| 단계 | 내용 | 성격 |
|---|---|---|
| 2 | `Stage`에 `ended_at` + 유니크 | 스키마 |
| 3 | `Match`·`Stage`·`Post`에 스냅샷 컬럼 | 스키마 |
| 4 | `Users`에 휴면 + 스위칭 예약 | 스키마 |
| 5 | 랭킹 테이블 재설계 | 스키마 |
| 6 | 라운드 자동 생성 유스케이스 | 로직 |
| 7 | **매치·게시판 자동 생성 (매칭)** | 로직 |
| 8 | 게시판 필터 + 점수 누적 + 스위칭 적용 + 시즌 확정 + 휴면 전환 | 로직 |
| 9 | 스케줄러 + ShedLock | 로직 |
| 10 | 로컬 검증 + 배포 | 검증 |

---

### 2단계 — `Stage`에 `ended_at` 추가

> **시작 전에 §8 `ddl-auto: update`의 한계 표를 먼저 읽을 것.** `ended_at`은 not null이라 기존 `stage` 행이 있으면 컬럼이 생성되지 않고, 앱은 경고만 남긴 채 정상 기동한다.

```java
@Entity
@Table(name = "stage",
       uniqueConstraints = @UniqueConstraint(columnNames = {"season", "round"}))
public class Stage {
    // ...
    @Column(name = "started_at", nullable = false) private LocalDateTime startedAt;
    @Column(name = "ended_at",   nullable = false) private LocalDateTime endedAt;   // 추가
    // is_active 제거
}
```

#### 첫 라운드 시드 (이 단계에서 함께)

`stage` 테이블이 비어 있으면 6~10단계가 **전부 무동작인데 에러도 나지 않는다.** 2단계에서 첫 행을 반드시 넣을 것.

```sql
-- started_at은 KST 자정 기준. 이 값이 이후 모든 라운드 경계를 결정한다.
-- ⚠️ 날짜는 예시를 복붙하지 말고 "실행하는 날의 직전 자정"으로 넣을 것.
--    미래 날짜를 넣으면 그 시각까지 현재 라운드가 없어 게시판이 0건이 된다.
--    (의도적으로 서비스 오픈일 자정을 미래로 잡는 경우라면, 그때까지 게시판 0건을 감수하는 것)
-- 예: 2026-08-11에 실행한다면 ↓
insert into stage (season, round, started_at, ended_at)
values (1, 1, '2026-08-11 00:00:00', '2026-08-18 00:00:00');
-- ended_at은 항상 started_at + 7일.
```

`swap_applied` 컬럼은 **3단계에서 추가**되므로 여기서는 넣지 않는다. 3단계 완료 직후 아래를 실행할 것(4단계 스위칭 배치가 진행 중인 라운드에 발동하는 것을 막는다).

```sql
-- 3단계 이후. RDS는 UTC이므로 시간대 변환이 필요하다(아래 경고 참조)
update stage set swap_applied = true
 where started_at <= (now() at time zone 'Asia/Seoul')
   and (now() at time zone 'Asia/Seoul') < ended_at;
```

**핵심 설계 원칙** — 현재 라운드를 시간 쿼리로 판정한다.

```sql
SELECT * FROM stage WHERE started_at <= now() AND now() < ended_at
```

이렇게 하면 **라운드 전환 시점에 아무 코드도 실행되지 않는다.** 스케줄러는 "전환"이 아니라 "미래 라운드를 미리 생성"하는 역할만 맡는다. 스케줄러가 며칠 죽어 있어도 이미 생성된 구간은 정상 동작하며, 복구는 재실행만으로 끝난다.

`is_active`는 읽는 코드가 없으므로 제거해도 안전하다.

---

### 3단계 — 점수 계산용 스냅샷 컬럼

```java
// Match — 매칭 시점 각 팀 ACTIVE 인원수 (n)
@Column(name = "team1_member_count", nullable = false) private Integer team1MemberCount;
@Column(name = "team2_member_count", nullable = false) private Integer team2MemberCount;

// Match — matchType을 안전하게 (아래 "matchType NPE 방어" 참조)
@Enumerated(EnumType.STRING)                        // 추가 — 현재는 ORDINAL 저장
@Column(name = "match_type", nullable = false)      // nullable → not null
@ColumnDefault("'NORMAL'")
private MatchType matchType = MatchType.NORMAL;

// Stage — 매칭 시점 평균 동네 인원 (C)
@Column(name = "avg_location_member_count")   // nullable — 매칭 시 채워짐
private Double avgLocationMemberCount;

// Stage — 이 라운드의 지역 스위칭을 이미 적용했는지 (4단계 참조)
@Column(name = "swap_applied", nullable = false)
@ColumnDefault("false")
private boolean swapApplied;

// Post — 작성 시점 소속 팀
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "location_id", nullable = false)   // 컬럼명은 기존 ERD 그대로 location_id (필드명만 teamLocation)
private Location teamLocation;
```

**`C`는 반드시 `Double`이다.** `C = 전체 유저 / 동네 수`는 `1000/7 = 142.857`처럼 소수가 나온다. `Integer`로 저장하면 142로 절삭되어 "중간 반올림 금지" 규칙을 어긴다.

#### `matchType` NPE 방어

`adapter/board/dto/BoardResponse.java:23`이 이렇게 호출한다.

```java
match.getMatchType().getDescription()
```

`matchType`은 현재 nullable이므로 **`null`이면 여기서 NPE가 나고, 게시판 목록 API 전체가 500이 된다.** 목록을 만들 때 게시판을 하나씩 변환하므로 **한 건만 잘못돼도 정상 게시판까지 전부 안 보인다.**

세 겹으로 막는다.

| 장치 | 막는 경우 |
|---|---|
| Java 필드 기본값 `= MatchType.NORMAL` | 코드로 엔티티를 만들 때 |
| `@ColumnDefault("'NORMAL'")` | 수동 `INSERT`에서 컬럼을 생략할 때 |
| `nullable = false` | 명시적으로 `null`을 넣으려 할 때 |

**Java 기본값이 특히 중요하다.** Hibernate는 필드 값을 INSERT에 포함하므로 DB DEFAULT가 무시된다(`Board.isActive`가 같은 함정이다 — 7단계 경고 참조).

**`@Enumerated(EnumType.STRING)`을 함께 넣는 이유**: 지금은 `@Enumerated`가 없어 **ORDINAL(정수)로 저장**된다. 이 상태로 DB DEFAULT를 걸면 `0`이 되는데, 나중에 `MatchType` enum 중간에 값을 추가하면 기존 데이터의 의미가 밀린다. 문자열로 바꾸면 이 위험이 사라진다.

**`MatchType`에 `BYE` 값도 이 단계에서 추가한다** — 홀수 처리 확정안(§5 확정 내역 4번)의 셀프 미션 매치용. STRING 전환과 같은 단계에 하면 ORDINAL 밀림 걱정이 없다. description은 화면에 그대로 노출되므로 "미션 위크" 등 유저向 문구로 정한다(`BoardResponse`가 description을 내려준다).

> **기존 데이터 주의**: ORDINAL → STRING 전환은 컬럼 타입이 정수 → 문자열로 바뀐다. 기존 행에 `0`/`1`이 있으면 읽히지 않는다. 개발 단계인 지금 재생성하는 것이 가장 빠르다.
>
> **`match`만 DROP하면 안 된다.** `board`·`post`와 함께 떨어뜨려야 FK가 온전히 재생성된다 — §8의 "`match` 테이블 재생성 시 주의"를 반드시 읽을 것.

#### `Match`에 유니크 제약 추가

인원수를 `Match`에 두는 근거는 **"한 동네는 라운드당 한 경기만 뛴다"** 는 전제다. 이 전제가 깨지면 같은 `(라운드, 동네)`의 인원수가 여러 행에 중복 저장되어 갱신 이상이 생긴다. **전제를 DB가 강제하도록 제약을 건다.**

```java
@Entity
@Table(name = "match",
       uniqueConstraints = {
           @UniqueConstraint(columnNames = {"stage_id", "team1_id"}),
           @UniqueConstraint(columnNames = {"stage_id", "team2_id"})
       })
```

**단, 이 제약만으로는 완전히 막지 못한다.**

```
매치1: A동(team1) vs B동
매치2: C동 vs A동(team2)    ← 두 제약 다 통과하지만 A동이 두 경기 중
```

같은 동네가 한쪽은 `team1`, 다른 쪽은 `team2`로 들어가면 뚫린다. **매칭 로직(7단계)에서 "이미 배정된 동네 집합"을 관리해 앱 레벨에서도 검증할 것.** 제약은 흔한 실수(같은 자리에 두 번)를 잡는 안전망이다.

#### `Post.location`(String) 처리 — 컬럼 추가 방식

`Post.location`은 현재 **동네 이름 문자열**이고, `PostService.resolveLocation()`이 작성 시점에 이미 스냅샷하고 있다. 이를 FK로 **교체**하면 API 응답 스펙이 바뀐다.

```
adapter/post/dto/PostDetailResponse.java:29   post.getLocation()
adapter/post/dto/PostSummaryResponse.java:30  post.getLocation()
```

두 DTO 모두 `location: String` 필드를 응답에 노출하므로 프론트 영향이 있다. **교체 대신 컬럼을 추가한다.**

| | String 교체 | **컬럼 추가 (채택)** |
|---|---|---|
| API 응답 | 바뀜 (프론트 협의 필요) | 그대로 |
| DTO 2개 | 수정 필요 | 불필요 |
| 역할 | 표시 + 집계 겸용 | `location`=표시용, `location_id`(FK)=집계용 |

`location` 문자열은 표시용으로 남기고, 점수 집계는 FK 컬럼 `location_id`(엔티티 필드명 `teamLocation`)로 한다. 컬럼명은 기존 ERD의 `post.location_id`를 그대로 쓴다 — 속성명 변경을 최소화하기 위한 결정(2026-08-11). 나중에 프론트를 정리할 때 문자열을 걷어내면 된다.

#### 미션 조달 — 스키마 변경 없음 (미결 3번 재결정 반영)

미션 조달은 **키워드 조합 + AI 다듬기로 확정**되었다(§5 확정 내역 3번). 기존 `keyword` 테이블(명사/형용사, `domain/keyword/Keyword.java` — 현재 아무 코드도 사용하지 않음)을 재사용하므로 **이 단계에서 새 테이블은 만들지 않는다.** 실제 미션 생성 로직은 7단계에서 만든다.

- **이 단계에서 할 일은 하나뿐**: `keyword` 테이블에 시드 데이터(명사·형용사 각각 여러 개)를 INSERT해둘 것. 비어 있으면 7단계 매칭이 `Match.mission`(not null)을 채울 수 없어 라운드 전체가 실패한다
- 구현 전에 `keyword.type`의 실제 저장값 형식을 DB에서 확인할 것 (`select distinct type from keyword`)
- **미션은 매치마다 각각 생성한다 (7단계 구현 중 확정 — "라운드당 공통 1개" 초안 폐기).** 같은 라운드 안에서는 같은 키워드 조합이 반복되지 않게 회피한다(최대 10회 재추첨). 미션별 난이도 차이라는 운 요소가 생기지만 다양성·재미를 우선한 결정
- 키워드 관리 어드민 API는 이 작업 범위 밖(§9 "아직 계획에 없는 것" 참조). 당장은 SQL로 INSERT

#### 변경 파일

| 파일 | 변경 |
|---|---|
| `domain/match/Match.java` | 인원수 2개 + 유니크 제약 2개 + **`matchType` 3중 방어**(`@Enumerated(STRING)`, `nullable=false`, `@ColumnDefault`, Java 기본값) |
| `domain/match/Stage.java` | `avgLocationMemberCount`(Double, nullable) + **`swapApplied`(boolean, not null)** + 정적 팩토리·변경 메서드 |
| `domain/post/Post.java` | `teamLocation` 필드 + `create()` 파라미터 추가 |
| `application/post/in/CreatePostUseCase.java` | 시그니처 — 변경 불필요 (`teamLocation`은 서비스 내부에서 결정) |
| `application/post/PostService.java` | `resolveLocation()` → `resolveTeamLocation()` 로 교체. **반환 타입 `Location`, `null` 반환 대신 403 예외** (8-2 참조) |
| `src/main/resources/application.yaml` | `ranking:` / `season:` 블록 추가 |
| `global/config/RankingProperties.java` · `SeasonProperties.java` | `@ConfigurationProperties` record 신규 (8-2 참조) |

`Post.create()`는 파라미터가 하나 늘어난다. 호출부는 `PostService.createPost()` 한 곳뿐이다.

#### 7단계 전까지 수동으로 매치를 넣을 때

3단계 이후 `Match.team1/2_member_count`가 not null이 되므로, **매칭 자동화(7단계) 완성 전까지 DB에 직접 매치를 넣던 방식은 이 값들도 함께 채워야 한다.** `matchType`(NPE 방지)과 `Stage.avg_location_member_count`(8-2의 null 체크)도 마찬가지다.

> 아래 SQL은 `users.status` 컬럼을 쓰므로 **4단계 이후**에만 실행 가능하다. 3단계와 4단계 사이라면 `status = 'ACTIVE'` 조건을 전부 뺄 것(인원 집계 2곳, 평균 계산 1곳).

```sql
-- 예시: 수동 매치 삽입 시
insert into match (stage_id, team1_id, team2_id, mission, match_type, team1_member_count, team2_member_count)
values (1, 1, 2, '미션 내용', 'NORMAL',
        (select count(*) from users where main_location_id = 1 and status = 'ACTIVE'),
        (select count(*) from users where main_location_id = 2 and status = 'ACTIVE'));

-- 게시판도 함께 넣어야 글쓰기 검증이 가능하다
insert into board (match_id, is_active)
values (currval(pg_get_serial_sequence('match','match_id')), true);

update stage set avg_location_member_count =
       (select count(*)::float from users where status = 'ACTIVE') / (select count(*) from location)
 where stage_id = 1;
```

#### 정규화 검토 (결론: 위반 아님)

이 컬럼들은 **역정규화가 아니라 시점 데이터**다. 판단 기준은 "같은 사실의 중복이냐, 다른 사실이냐"다.

- "지금 A동 인원수"와 "이 매치 시작 시점의 A동 인원수"는 **서로 다른 사실**이며, 후자는 매치가 시작된 순간부터 어디에서도 유도할 수 없다
- 주문 테이블에 주문 시점 상품 가격을 저장하는 것과 동일한 논리다

**중복 여부**: 인원수를 별도 `stage_location_snapshot` 테이블로 뺄지 검토했으나, 한 동네는 라운드당 한 경기만 치르므로 각 동네 인원수가 라운드당 한 번씩만 저장된다. 위 유니크 제약이 이를 보장한다.

> 매칭 규칙이 바뀌어 한 동네가 라운드당 여러 경기를 뛰게 되면 `(stage_id, location_id, member_count)` 테이블로 분리해야 한다.

---

### 4단계 — `Users`에 휴면 + 지역 스위칭 예약

```java
// 휴면 처리
@Column(name = "last_active_at", nullable = false) private LocalDateTime lastActiveAt;

@Enumerated(EnumType.STRING)
@Column(name = "status", nullable = false)
private UserStatus status;                  // 새 enum: ACTIVE, DORMANT

// 지역 스위칭 예약
@Column(name = "pending_location_swap", nullable = false)
@ColumnDefault("false")
private boolean pendingLocationSwap;

// 메인 지역을 필수로 변경 (기존에는 nullable)
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "main_location_id", nullable = false)   // nullable = false 추가
private Location mainLocation;
```

#### `main_location`을 NOT NULL로 바꾸는 이유

**소속 없는 유저는 존재하지 않아야 한다.** 지금은 nullable이라 다음 문제가 생긴다.

- `C = 전체 ACTIVE 유저 수 / 동네 수` 계산에서 소속 없는 유저가 **분자에만** 포함되어 `C`가 과대 계산된다. Σ(동네별 인원) ≠ 전체 유저 수가 되어 정의가 깨진다
- 게시물 작성·게시판 조회 경로마다 null 체크 분기가 필요하다

NOT NULL로 바꾸면 이 분기들이 전부 사라진다.

```java
// 불필요해지는 것들
if (authorLocation == null) return null;         // 현행 PostService.resolveLocation()
if (user.getMainLocation() == null) throw ...    // BoardService.mainLocationId (8-1)
```

`sub_location`은 **nullable 유지**한다. 스위칭을 쓰지 않는 유저가 있을 수 있다.

#### ⚠️ 로그인 브랜치와의 충돌 지점

회원가입 코드는 **아직 병합되지 않은 다른 브랜치에 있다.** `main_location`을 NOT NULL로 만들려면 그쪽에서 **가입 시 메인 지역을 필수로 받아야 한다.** 병합 시 다음을 확인할 것.

- 회원가입 API가 `mainLocationId`를 필수 파라미터로 받는가
- 소셜 로그인 등으로 지역 없이 계정이 먼저 생기는 흐름이 있는가 — 있다면 "지역 선택 완료 전에는 게시물 작성 불가" 같은 별도 상태가 필요하고, 이 경우 NOT NULL 결정을 재검토해야 한다

**백필**: 기존 행에 `main_location_id`가 null인 것이 있으면 NOT NULL 제약 추가가 실패한다.

```sql
select count(*) from users where main_location_id is null;
-- 0이 아니면 기본 지역으로 채우거나 해당 행을 정리한 뒤 제약 추가
ALTER TABLE users ALTER COLUMN main_location_id SET NOT NULL;
```

`domain/enums/UserStatus.java` 신규 생성. 팀 인원수 집계 시 `status = ACTIVE` 만 카운트한다.

#### ⚠️ 휴면 배치를 켜기 전에 반드시 읽을 것

**현재 이 브랜치에는 로그인·회원가입 코드가 없다.** `domain/token/Token.java`, `domain/enums/LoginProvider.java` 엔티티는 있지만 이를 사용하는 리포지토리·서비스·컨트롤러가 하나도 없고, `Users`를 저장하는 코드조차 없다.

**2026-08-11 사용자 확인: 로그인 기능은 개발 완료, 브랜치 병합만 남았다.** (해당 브랜치는 이 저장소의 로컬·origin에는 아직 보이지 않는다 — 아마 push 전이거나 다른 리모트에 있는 것으로 보인다.) 병합 자체는 이 작업과 별개로 진행하면 되고, **병합하는 시점에 아래 체크리스트를 반드시 점검할 것.** 로그인 브랜치는 이 작업(4단계)이 `Users`에 가하는 변경을 모르는 채 작성되었을 것이므로, 충돌 여부를 여기서 걸러야 한다.

**병합 시 확인 체크리스트 (이 문서의 4단계 변경과 맞물리는 것):**

1. 회원가입 API는 `mainLocationId`를 **필수**로 받을 것 — 4단계에서 `users.main_location_id`가 NOT NULL이 된다. 소셜 로그인 등으로 지역 없이 계정이 먼저 생기는 흐름을 만들려면 이 작업의 NOT NULL 결정을 먼저 재협의할 것
2. 인증(로그인) 성공 지점에서 `user.touch()`를 호출할 것 — `last_active_at` 갱신 + DORMANT 복귀
3. `Users`의 `@PrePersist`(createdAt/updatedAt/lastActiveAt/status/point 초기화)는 **이 작업의 4단계에서 먼저 추가한다** — 로그인 쪽에서 중복 추가하지 말고, 유저 생성은 정적 팩토리 + `@PrePersist`에 맡길 것
4. 병합 시점에 `last_active_at`·`status` 백필이 이미 되어 있는지 확인할 것 (아래 SQL)

즉 `lastActiveAt`을 갱신해줄 주체가 지금은 존재하지 않는다. 이 상태에서 휴면 배치(8-5)를 켜면:

```
7일 뒤 → 전 유저 DORMANT
      → 동네별 ACTIVE 인원 0
      → C = 0, n = 0
      → 조정치 0 → 모든 지역 점수가 0으로 누적
```

**에러 없이 랭킹 데이터가 전부 0이 된다.** 가장 발견하기 어려운 종류의 사고다.

**따라서:**

1. **로그인 기능이 병합(또는 개발 완료)되기 전까지 휴면 배치(**8-5**)를 스케줄러에 등록하지 말 것.** 유스케이스는 만들어도 되지만 `@Scheduled`는 붙이지 않는다
2. 로그인 병합 시 위 "확인 체크리스트" 4가지가 충족됐는지 확인할 것
3. 게시물 작성 시에도 `author.touch()`를 호출한다 (8-2 참조)
4. `last_active_at` 컬럼을 추가할 때 **기존 행을 `now()`로 백필**할 것. 안 하면 첫 배치에서 즉시 전원 휴면이다
5. 로그인 코드가 생기기 전까지 로컬 검증(10단계)은 **`users` 행을 SQL로 직접 넣어** 진행한다

```sql
ALTER TABLE users ADD COLUMN last_active_at timestamp;
UPDATE users SET last_active_at = (now() at time zone 'Asia/Seoul');
ALTER TABLE users ALTER COLUMN last_active_at SET NOT NULL;

ALTER TABLE users ADD COLUMN status varchar(20);
UPDATE users SET status = 'ACTIVE';
ALTER TABLE users ALTER COLUMN status SET NOT NULL;
```

`pending_location_swap`은 `@ColumnDefault("false")`가 DDL에 반영되어 PostgreSQL이 기존 행을 자동으로 채우므로 별도 백필이 필요 없다.

#### ⚠️ `Users`에 `@PrePersist`가 없다

현재 `Users`에는 `@PreUpdate`만 있고 `@PrePersist`가 없다. `created_at`·`updated_at`이 NOT NULL인데 최초 저장 시 채워지지 않는다. 이 단계에서 `last_active_at`·`status`까지 NOT NULL로 늘어나므로, **로그인 브랜치가 병합되어 유저를 생성하는 순간 즉시 깨진다.**

```java
@PrePersist
public void prePersist() {
    LocalDateTime now = LocalDateTime.now();
    this.createdAt = now;
    this.updatedAt = now;
    this.lastActiveAt = now;
    if (this.status == null) this.status = UserStatus.ACTIVE;
    if (this.point == null) this.point = 0;      // 아래 참조
}
```

**`point`도 같은 함정이다.** `Integer` + `nullable = false` + `@ColumnDefault("0")` 조합인데 Hibernate가 필드의 `null`을 INSERT에 포함하므로 DB DEFAULT가 무시된다. `Board.isActive`·`Match.matchType`과 동일한 문제다.

`Users`에는 세터가 없으므로 **도메인 메서드를 추가해야 한다.**

```java
public void requestLocationSwap() {
    if (this.subLocation == null) {
        throw new SubLocationNotSetException();      // global/exception 에 신규
    }
    this.pendingLocationSwap = true;
}

public void applyLocationSwap() {          // 라운드 시작 시 호출
    if (!this.pendingLocationSwap) return;
    Location tmp = this.mainLocation;
    this.mainLocation = this.subLocation;
    this.subLocation = tmp;
    this.pendingLocationSwap = false;
}

public void touch() { this.lastActiveAt = LocalDateTime.now(); this.status = UserStatus.ACTIVE; }
public void markDormant() { this.status = UserStatus.DORMANT; }
```

도메인 엔티티에서 `ResponseStatusException`(Spring Web)을 던지지 않는다. 기존 `Users.usePoint()`가 `InsufficientPointException`(`global/exception`)을 쓰는 것과 같은 방식으로 커스텀 예외를 만든다. **그리고 `GlobalExceptionHandler`에 대응 핸들러를 등록할 것** — 현재 핸들러에는 `InsufficientPointException` 하나뿐이라, 등록하지 않으면 이 예외가 500으로 떨어진다.

**`sub_location`이 null인 유저의 스위칭은 요청 단계에서 막아야 한다.** 막지 않으면 라운드 시작 시 `mainLocation`이 null이 되고, 그 유저의 모든 게시물이 8단계에서 403으로 거부된다.

#### 지역 스위칭 규칙

유저는 프로필에서 `main_location` ↔ `sub_location`을 스위칭할 수 있다. **라운드 중에 바꿔도 그 라운드에는 반영되지 않고 다음 라운드부터 적용된다.**

```
1시즌 2라운드 중 스위칭 → 2라운드는 기존 동네 소속 유지
                        → 1시즌 3라운드부터 새 동네 멤버로 참여
```

| 시점 | 동작 |
|---|---|
| 스위칭 요청 | `pendingLocationSwap = true` 로만 표시. `main`/`sub`는 **그대로** |
| 라운드 시작 직후 (00:10) | 별도 배치가 `applyLocationSwap()` 호출 → 실제 교환 + 플래그 해제 |

이 방식의 이점은 **`users.main_location`이 항상 "현재 라운드의 소속"과 일치**한다는 것이다. 게시물 작성 시 별도 조회 없이 쓸 수 있다.

**적용은 라운드 시작 직후여야 한다.** 매일 돌리면 라운드 중에 신청한 것이 같은 라운드에 반영되어 규칙이 깨진다. 매치 생성 배치(라운드 시작 전날)에 묶어도 안 된다 — 아직 진행 중인 라운드에 스왑이 반영되어 그 유저가 현재 라운드 매치에서 튕긴다.

"라운드가 막 시작했는지"는 `Stage`에 플래그를 두어 판단한다.

```java
// Stage
@Column(name = "swap_applied", nullable = false)
@ColumnDefault("false")
private boolean swapApplied;

public void markSwapApplied() { this.swapApplied = true; }
```

```
배치 (매일 00:10):
  current = 현재 라운드
  if (!current.isSwapApplied()) {
      pendingLocationSwap = true 인 유저 전원 applyLocationSwap()
      current.markSwapApplied()
  }
```

> **⚠️ 배포 직후 1회성 조치**: `swap_applied`는 새 컬럼이라 기존 라운드 행이 전부 `false`다. 그대로 두면 배포 다음 자정에 **진행 중인 라운드 한복판에서** 스왑이 발동해, 그 유저는 현재 매치에 없는 동네 소속이 되어 라운드 끝까지 403으로 글을 못 쓴다. 배포 시 현재 라운드를 미리 처리 완료로 표시할 것.
>
> ```sql
> update stage set swap_applied = true
>  where started_at <= now() and now() < ended_at;
> ```

날짜 비교("오늘 시작한 라운드면") 방식보다 낫다. 배치가 그날 실패해도 플래그가 `false`로 남아 **다음 실행에 자동 복구**된다.

UI에는 `메인: A동 (다음 라운드부터 B동)` 처럼 예약 상태를 표시한다.

#### `sub_location`의 역할

`sub_location`은 **점수 계산에 전혀 관여하지 않는다.** 스위칭 대상일 뿐이며 서브 지역 게시판에는 글을 쓸 수 없다. 인원수(`n`)를 셀 때도 `main_location`만 센다. 이 규칙 덕분에 한 유저가 두 동네에 중복 카운트되지 않고 `C` 계산도 정확해진다.

---

### 5단계 — 랭킹 테이블 재설계

> `UserRanking`은 **함께 구현하기로 확정**되었다(§5 확정 내역 1번). 아래는 `LocationRanking` 기준이며, `UserRanking`도 동일한 구조로 재설계한다 — 대리키 `user_ranking_id` + `(stage_id, user_id)` 유니크, `user` `@ManyToOne`(FK), `userScore`는 `Long`, `user_rank` 컬럼·인덱스 제거, `@PrePersist` 추가. 개인 점수는 `round(AI × K)`로 조정치 없이 누적한다(8-2 참조).

#### ① `LocationRanking` — 라운드 단위로 변경

```java
@Entity
@Table(name = "location_ranking",
       uniqueConstraints = @UniqueConstraint(columnNames = {"stage_id", "location_id"}))
public class LocationRanking {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "location_ranking_id")          // PK 관례(테이블명_id) 준수
    private Long locationRankingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_id", nullable = false)
    private Stage stage;                                        // 추가 — 라운드 차원

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;                                  // Long → FK

    @Column(name = "location_score", nullable = false) private Long locationScore;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    @PrePersist   // 기존 코드에는 @PreUpdate만 있어 JPA로 최초 저장하면 not null 위반
    public void prePersist() { this.updatedAt = LocalDateTime.now(); }

    @PreUpdate
    public void preUpdate() { this.updatedAt = LocalDateTime.now(); }
}
```

> 점수 누적은 네이티브 UPSERT로 하므로 이 콜백을 타지 않지만, JPA 저장 경로가 하나라도 생기면 `updated_at`이 null이 되어 제약을 위반한다. 기존 코드에 `@PrePersist`가 없는 것은 랭킹을 저장하는 코드가 아직 없기 때문이다.

| 항목 | 변경 전 | 변경 후 | 이유 |
|---|---|---|---|
| PK | `location_id` | 대리키 `location_ranking_id` | 라운드마다 행이 필요. 이름은 프로젝트 PK 관례(테이블명_id) |
| 유니크 | 없음 | `(stage_id, location_id)` | UPSERT 대상 지정 + 중복 방지 |
| `stage` | 없음 | `@ManyToOne` | 어느 라운드 점수인지 |
| `location` | 생 `Long` | `@ManyToOne` | FK 제약이 없어 참조 무결성이 없었음 |
| `score` | `Integer` | `Long` | 누적값이 커질 수 있음 |
| `location_rank` | 컬럼 + 인덱스 | **제거** | 아래 참조 |

**`location_rank` 컬럼과 `idx_location_ranking` 인덱스를 제거한다.** 실시간 누적 방식에서는 점수가 바뀔 때마다 전체 순위를 다시 매기는 것이 비싸므로 이 컬럼을 채우지 않는다. 항상 NULL인 컬럼과 그 인덱스를 남길 이유가 없다. 라운드 순위는 조회 시 `ORDER BY location_score DESC`로 계산하고, 시즌 확정 순위는 `season_location_result.final_rank`가 담당한다.

#### 엔티티 생성 수단이 없다는 점에 주의

`Stage`, `Match`, `Board`, `LocationRanking`은 `@Getter` + `@NoArgsConstructor(access = PROTECTED)` 뿐이라 **세터도 정적 팩토리도 없다.** (`SeasonLocationResult`는 5단계에서 새로 만드는 클래스다.) 6·7단계에서 이들을 생성하려면 `Post.create(...)` 같은 **정적 팩토리를 각 엔티티에 추가해야 한다.**

```java
// Stage 예시
public static Stage create(Integer season, Integer round,
                           LocalDateTime startedAt, LocalDateTime endedAt) { ... }

public void assignAvgLocationMemberCount(double c) {   // 7단계에서 C를 채울 때 필요
    this.avgLocationMemberCount = c;
}
```

특히 7단계의 "`Stage.avgLocationMemberCount`에 저장"은 **변경 메서드가 없으면 불가능**하다.

#### ② `SeasonLocationResult` — 신규

```java
@Entity
@Table(name = "season_location_result",
       uniqueConstraints = @UniqueConstraint(columnNames = {"season", "location_id"}))
public class SeasonLocationResult {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "result_id")                    // PK 관례(테이블명_id) 준수
    private Long resultId;

    @Column(name = "season", nullable = false)
    private Integer season;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Column(name = "final_rank", nullable = false)  private Integer finalRank;
    @Column(name = "final_score", nullable = false) private Long finalScore;
    @Column(name = "confirmed_at", nullable = false) private LocalDateTime confirmedAt;
}
```

`domain/ranking/SeasonLocationResult.java`에 생성.

**모든 동네에 대해 행을 만들 것.** 그 시즌 `location_ranking` 행이 없는 동네(한 번도 점수를 얻지 못한 동네)도 `final_score = 0`으로 기록해야 프로필에서 "미참여"와 "꼴등"이 구분된다.

#### 조회 쿼리 (JPQL)

**현재 라운드 지역 랭킹**

```java
select r from LocationRanking r
 join r.stage s
where s.startedAt <= :now and :now < s.endedAt
order by r.locationScore desc
```

**진행 중인 시즌의 잠정 순위**

```java
select r.location, sum(r.locationScore)
  from LocationRanking r join r.stage s
 where s.season = :season
 group by r.location
 order by sum(r.locationScore) desc
```

> JPQL은 `order by 2 desc` 같은 위치 정렬을 지원하지 않는다. 집계 함수를 다시 써야 한다.

**동네 프로필 — 시즌별 등수 히스토리**

```java
select r from SeasonLocationResult r
 where r.location = :location
 order by r.season desc
```

세 번째가 단순 조회로 끝나는 것이 `season_location_result`를 만드는 이유다. 이 테이블 없이 하려면 윈도우 함수를 쓴 네이티브 쿼리가 필요하고, 프로필을 열 때마다 전체 동네 × 전체 시즌을 집계해야 한다.

#### 정규화 검토 (결론: 의도된 역정규화)

3단계의 스냅샷 컬럼과 달리 **랭킹 테이블은 명백한 역정규화다.** `locationScore`는 `Post` + `Match`에서 계산 가능한 파생 데이터이고, `SeasonLocationResult`는 파생 데이터의 파생 데이터다.

정당화 근거:

- 순위 조회가 빈번한데 매번 전체 집계하면 비용이 크다
- `SeasonLocationResult`는 **"그 시즌 종료 시점에 확정된 사실"** 로서의 의미가 있다

대가로 미결 사항 5·6번(원본 변경 시 정책)이 생긴다.

---

### 6단계 — 라운드 자동 생성

`application/match/in/EnsureFutureStagesUseCase` (구현체 `application/match/StageService`)

```
1. ended_at이 가장 늦은 Stage 조회 → last
   (테이블이 비어 있으면 → 경고 로그만 남기고 종료한다. 예외를 던지면 시드 전까지 매일 에러 로그가 쌓인다)
2. last.ended_at < (now + prefetchDays) 인 동안 반복:
     새 Stage 생성
       started_at = last.ended_at
       ended_at   = started_at + roundDays(7일)
       round      = last.round + 1
       season     = last.season
       단, last.round == roundsPerSeason(4) 이면 → season + 1, round = 1
     last = 방금 만든 Stage
```

**멱등성**: 매 실행마다 "가장 늦은 라운드"를 다시 읽고 그 뒤부터 이어 만들므로, 두 번 실행해도 이미 만든 구간은 조건에 걸리지 않아 아무 일도 하지 않는다. `(season, round)` 유니크 제약은 동시 실행 같은 예외 상황에서 중복을 **막아주는 안전망**이지, 멱등성 자체는 이 로직에서 나온다.

**밀려도 따라잡는다**: 며칠 중단되어도 재가동 시 조건이 만족될 때까지 반복하므로 밀린 라운드를 한 번에 채운다.

아웃바운드 포트: `application/match/out/StageRepositoryPort`
구현체: `adapter/match/out/StageJpaRepository` + `StagePersistenceAdapter`

---

### 7단계 — 매치·게시판 자동 생성 (매칭)

> **이 단계가 자동화의 본체다.** 현재 코드에는 매치·게시판을 생성하는 로직이 전혀 없다.
> 매칭 관련 결정은 전부 확정되었다(§5 확정 내역 2·3·4번): 랜덤 매칭 + 직전 라운드 상대 회피, 미션은 키워드 조합 + AI 다듬기(실패 시 템플릿 폴백), ACTIVE 0명 동네 제외, 홀수 시 셀프 미션 매치(BYE) + BYE 이력 로테이션.

`application/match/in/OpenRoundUseCase` (구현체 `application/match/MatchService`)

#### 실행 순서 (순서가 중요하다)

```
라운드 시작 하루 전:

1) 동네별 "예정 소속" ACTIVE 인원 집계
   예정 소속 = pendingLocationSwap ? subLocation : mainLocation
   → Match.team1/2_member_count (n)

2) C 계산
   C = 전체 ACTIVE 유저 수 / 동네 수   (Double)
   → Stage.assignAvgLocationMemberCount(c)

3) 매칭 — 랜덤 + 직전 상대 회피 (확정)
   a. ACTIVE 인원 0명 동네를 매칭 대상에서 제외 (§5 확정 내역 2번)
   b. 남은 동네 수가 홀수면 → BYE 이력이 가장 오래된(또는 없는) 동네를 부전승으로 선정,
      셀프 미션 매치 생성: team2 = team1, matchType = BYE,
      team1/2_member_count 둘 다 그 동네 인원 (§5 확정 내역 4번)
   c. 나머지 동네를 무작위로 섞어 인접한 동네끼리 [1,2] [3,4] ... 로 짝지어 Match 생성.
      직전 라운드(ended_at = 이번 started_at)에서 붙었던 짝이 나오면 재셔플(최대 10회),
      회피 불가능하면 리매치 허용 + 경고 로그
   - team1/2_member_count 에 1)의 집계값
   - matchType 을 반드시 채운다 (아래 경고 참조)
   - mission 생성: **매치마다 각각** keyword에서 형용사+명사를 무작위로 뽑아 Gemini로
     자연스러운 미션 문장을 만들고 Match.mission에 복사한다 (BYE 매치 포함)
     · 같은 라운드 안에서 같은 키워드 조합 반복 회피 (최대 10회 재추첨)
     · Gemini 실패 시 고정 템플릿 조합으로 폴백 — 외부 API 때문에 라운드가 안 열리면 안 됨
     · keyword가 0건이면 에러 로그 + 이 라운드 중단 (Match.mission이 not null이라 진행 불가)
   - 이미 배정된 동네를 집합으로 관리해 한 동네가 두 경기에 들어가지 않게 검증

4) 게시판 생성
   Match 마다 Board 하나 (1:1). isActive = true 를 명시할 것 (아래 경고 참조)

※ 1)~4)는 라운드 하나에 대해 단일 트랜잭션으로 묶는다 (아래 "멱등성" 참조)
```

#### 왜 "예정 소속"으로 세는가

**지역 스위칭 적용은 이 배치가 하지 않는다.** 별도 배치가 라운드 시작 직후(00:10)에 수행한다(8-4 참조).

매칭은 라운드 시작 **전날**이고 스왑 적용은 **시작 직후**이므로, 현재 `main_location`으로 세면 스위칭한 유저가 **새 동네의 `n`에 잡히지 않는다.**

```
8/6         유저가 스위칭 신청 (아직 A동, pendingLocationSwap = true)
8/10 05:10  매치 생성 → A동 인원으로 카운트  ← 잘못됨
8/11 00:10  스왑 적용 → B동 소속이 됨
8/11~       B동 게시판에 글 씀 → B동 n에는 이 사람이 없음
```

집계 시 예약 플래그를 반영하면 **매치 생성 시점까지 접수된 스위칭은 정확히 반영된다.**

```sql
-- 스왑 예약자는 sub_location을 소속으로 간주
count(*) where (case when pending_location_swap then sub_location_id else main_location_id end) = :locationId
  and status = 'ACTIVE'
```

`main_location`이 NOT NULL이고(4단계), 스왑 예약은 `sub_location`이 있어야만 가능하므로(4단계 `requestLocationSwap()`) 이 식은 항상 non-null을 반환한다. 따라서 Σ(동네별 인원) = 전체 ACTIVE 유저 수가 성립하고 `C` 계산도 정확하다.

**남는 오차 — 약 19시간 창**

매치 생성은 라운드 시작 전날 05:10, 스왑 적용은 시작 직후 00:10이다. **그 사이(약 19시간)에 접수된 스위칭 요청은 집계에 반영되지 못한다.** 그 유저는 옛 동네 `n`에 잡힌 채 새 동네에서 활동하게 된다.

라운드 중 신규 가입자와 같은 종류의 오차이며, **감수하기로 한다.** 완전히 없애려면 매치 생성 후 스위칭 요청을 차단해야 하는데, 유저에게 "지금은 신청할 수 없습니다"를 설명하기 어렵고 얻는 정확도에 비해 복잡하다. `C`는 영향받지 않고 `n` 귀속만 어긋난다.

#### `Match.matchType`

3단계에서 Java 기본값 `MatchType.NORMAL`을 넣었으므로 매칭 로직이 따로 지정하지 않아도 안전하다. 이벤트 매치를 만들 때만 `EVENT`로 지정한다.

#### ⚠️ `Board.isActive`를 true로 명시할 것

`domain/board/Board.java`의 `isActive`는 `@ColumnDefault("true")`가 붙어 있지만 **primitive `boolean`** 이다. Hibernate는 필드 기본값 `false`를 INSERT에 포함하므로 **DB DEFAULT가 무시된다.** 팩토리에서 명시하지 않으면 생성된 게시판이 전부 `is_active = false`가 되어 8-1 필터(`b.isActive = true`)에 걸려 **하나도 보이지 않는다.** `Post.create()`가 `isActive = true`를 명시하는 것이 선례다.

#### 멱등성 — 라운드 단위 단일 트랜잭션이 전제다

이미 매치가 만들어진 라운드에 다시 실행되면 안 된다. **"해당 라운드에 Match가 하나도 없을 때만 진행"** 조건을 두고, 3단계의 `(stage_id, team1_id)` 유니크 제약을 2차 방어선으로 삼는다.

**⚠️ 이 조건은 라운드 하나의 생성(인원 집계 → C 저장 → 매치 전체 → 게시판 전체)이 단일 트랜잭션일 때만 안전하다.** 매치를 절반쯤 만들다 실패했는데 일부가 커밋되어 있으면, 다음 실행에서 "Match가 하나라도 있음" 조건에 걸려 그 라운드는 **영원히 반쪽으로 고착**되고 재실행으로도 복구되지 않는다. 라운드별로 전부 성공 또는 전부 롤백이어야 한다.

- 트리거 결과가 라운드 여러 개일 때(배치가 밀렸던 경우)는 **라운드마다 별도 트랜잭션**으로 처리한다 — 한 라운드의 실패가 다른 라운드까지 되돌리지 않도록
- 구현 힌트: 스케줄러가 라운드 목록을 조회한 뒤, 라운드 하나를 처리하는 `@Transactional` 메서드를 라운드별로 호출하는 구조 (같은 클래스 내부 호출은 프록시를 타지 않으므로 유스케이스 분리에 주의)

#### 트리거 — 라운드 시작 **전에** 미리 만든다

```java
// 임계 시각은 Java에서 계산한다. JPQL은 :now + 3일 같은 산술을 지원하지 않는다.
LocalDateTime threshold = now.plusDays(props.matchOpenAheadDays());   // = 1

select s from Stage s
 where s.startedAt <= :threshold
   and :now < s.endedAt                                       // 하한 — 이미 끝난 라운드 제외
   and not exists (select 1 from Match m where m.stage = s)
 order by s.startedAt
```

**하한 조건(`:now < s.endedAt`)이 반드시 필요하다.** 없으면 매치 없이 지나간 과거 라운드가 전부 걸려서, 지난 라운드의 게시판이 뒤늦게 생성되고 `assignAvgLocationMemberCount()`가 현재 값으로 덮인다.

**결과가 복수일 수 있다.** 배치가 며칠 밀렸다가 실행되면 두 개 이상 걸린다. 각 라운드를 **독립적으로** 처리할 것 — 라운드마다 인원을 새로 집계하고 그 라운드의 `Stage`에 각각 `C`를 저장한다.

**"이미 시작된 라운드"가 아니라 "곧 시작할 라운드"를 찾는다.** 이것이 중요하다.

라운드 경계는 **자정**인데 배치는 **05:10**에 돈다. 트리거를 `startedAt <= now`로 두면 자정에 라운드가 시작됐지만 매치는 05:10에야 생기므로, **매주 5시간 10분 동안 게시판이 0건**이 된다(목록은 빈 화면, `getMission`은 404 → 글쓰기 불가). 배치가 하루 실패하면 그 공백이 24시간이 된다.

미리 만들면:

```
8/10 05:10  배치 실행 → 8/11 00:00 시작 예정 라운드의 매치·게시판 생성
            (유저 눈에는 변화 없음 — 8-1 필터가 startedAt <= now 를 요구하므로 아직 안 보인다)
8/11 00:00  now()가 넘어가며 새 게시판이 나타남
            → 이 순간 실행되는 코드: 없음
8/11 00:10  지역 스위칭 적용 (별도 배치)
```

**데이터는 미리 준비되지만 유저에게는 라운드 시작 시각에 나타난다.** 미래 라운드(`Stage` 행)를 30일치 미리 만드는 것과 같은 원리이며, 이 문서 전체를 관통하는 원칙이다 — **경계에서 실행되는 코드가 없으면 경계에서 실패할 것도 없다.**

하루 여유를 두므로 배치가 이틀 연속 실패해야 공백이 생긴다. 3일로 늘리면 더 안전하지만 `n`·`C` 스냅샷이 라운드 시작에서 멀어져 실제 인원과 더 어긋난다. **1일이 균형점이다.**

#### 스냅샷 시점에 대한 참고

`n`과 `C`가 **라운드 시작 시점이 아니라 하루 전 기준**이 된다. 문제되지 않는다.

- 어차피 고정값이므로 드리프트는 없다
- 모든 동네에 똑같이 적용되므로 공정성에 영향이 없다
- 하루 사이 인원 변화는 크지 않다
- 스위칭은 위의 "예정 소속" 집계로 이미 보정된다

#### 이 단계에서 만들거나 고칠 파일

**`adapter/match/`, `adapter/location/` 패키지 자체가 아직 없다.** 6단계에서 만든 Stage 관련 파일 외에 다음이 전부 필요하다.

| 파일 | 용도 |
|---|---|
| `application/match/out/MatchRepositoryPort.java` | `save(Match)`, `existsByStage(Stage)` (멱등성 체크) |
| `adapter/match/out/MatchJpaRepository.java` | 위 구현 |
| `adapter/match/out/MatchPersistenceAdapter.java` | 포트 구현 |
| `application/location/out/LocationRepositoryPort.java` | `findAll()`, `count()` — `C` 계산과 8-3의 "전체 Location 기준"에 필수 |
| `adapter/location/out/LocationJpaRepository.java` | 위 구현 |
| `adapter/location/out/LocationPersistenceAdapter.java` | 포트 구현 |
| `application/board/out/BoardRepositoryPort.java` | **`save(Board)` 메서드 추가** — 현재 포트에는 조회 2개뿐이다 |
| `adapter/board/out/BoardPersistenceAdapter.java` | `save` 구현 |
| `application/user/out/UserRepositoryPort.java` | **"예정 소속 기준 동네별 ACTIVE 인원 집계" 메서드 추가** |
| `adapter/user/out/UserJpaRepository.java` | 위 집계 쿼리 (아래 SQL) |
| `application/match/out/KeywordRepositoryPort.java` | 타입별 키워드 무작위 조회 (기존 `keyword` 테이블 사용) |
| `adapter/keyword/out/KeywordJpaRepository.java` + `KeywordPersistenceAdapter.java` | 위 구현 (`adapter/keyword/` 신규 폴더) |
| `application/match/out/MissionGeneratorPort.java` | 키워드 조합 → 미션 문장 생성 (AI) |
| `adapter/match/out/GeminiMissionAdapter.java` | 위 구현 — Gemini 호출, **실패 시 템플릿 폴백 내장** (기존 `GeminiAdapter` 채점용과 별도) |

`Match`·`Board`에 정적 팩토리도 추가해야 한다(5단계 "엔티티 생성 수단" 참조).

#### 어드민 수동 트리거

`adapter/match/MatchAdminController.java`에 같은 유스케이스를 호출하는 엔드포인트를 둔다. 평상시에는 쓰지 않지만 매칭이 실패했을 때의 복구 경로다. **`EnsureFutureStagesUseCase`(6단계)를 호출하는 엔드포인트도 함께 두면** 배포 직후 다음 배치 시각(05:05/05:10)까지 기다리지 않고 첫 라운드·매치를 즉시 만들 수 있다.

---

### 8단계 — 게시판 필터 + 점수 누적 + 배치 유스케이스

#### 8-1. 게시판 조회에 필터 2개

| 필터 | 조건 | 목적 |
|---|---|---|
| **현재 라운드** | `s.startedAt <= :now and :now < s.endedAt` | 시즌제 게시판 자동화 |
| **내 동네** | `t1.locationId = :locationId or t2.locationId = :locationId` | 자기 동네가 참가한 매치만 |

**두 필터를 목록 조회와 단건 조회에 모두 적용한다.** 현재 `BoardController.getBoards()`는 파라미터조차 받지 않고 모든 활성 게시판을 내려준다. 프론트에서 걸러 보여주고 있다면 그것은 화면 처리일 뿐 검증이 아니다.

**단건 조회(`findActiveByIdWithMatch`)가 특히 중요하다.** 이름과 달리 "게시판 상세 조회"가 아니라 **게시물 작성 시 미션을 가져오는 경로**다(`BoardService.getMission()` → `PostController.createPost()`). 게시판 상세 조회 API는 존재하지 않는다. 여기 조건이 없으면 종료된 라운드의 게시판 ID로 계속 글을 쓸 수 있다.

현실적인 사고 경로는 해킹이 아니라 **타이밍**이다. 유저가 23:55에 게시판을 열고 00:01에 제출하면 이미 끝난 라운드에 점수가 들어간다.

`createPost`는 `게시판 조회 → S3 업로드 → Gemini 호출 → 저장` 순서이므로 **가장 앞단인 `getMission`에서 막아야** 낭비가 없다.

**목록 조회**

```java
@Query("""
        select b from Board b
        join fetch b.match m
        join fetch m.team1 t1
        join fetch m.team2 t2
        join fetch m.stage s
        where b.isActive = true
          and s.startedAt <= :now
          and :now < s.endedAt
          and (t1.locationId = :locationId or t2.locationId = :locationId)
        """)
List<Board> findAllActiveWithMatch(@Param("now") LocalDateTime now,
                                   @Param("locationId") Long locationId);
```

**단건 조회**

```java
@Query("""
        select b from Board b
        join fetch b.match m
        join fetch m.team1 t1
        join fetch m.team2 t2
        join fetch m.stage s
        where b.boardId = :boardId
          and b.isActive = true
          and s.startedAt <= :now
          and :now < s.endedAt
          and (t1.locationId = :locationId or t2.locationId = :locationId)
        """)
Optional<Board> findActiveByIdWithMatch(@Param("boardId") Long boardId,
                                        @Param("now") LocalDateTime now,
                                        @Param("locationId") Long locationId);
```

**`BoardService`**

```java
private final BoardRepositoryPort boardRepositoryPort;
private final UserRepositoryPort userRepositoryPort;      // 추가

@Override
public List<Board> getBoards(UUID userId) {
    return boardRepositoryPort.findAllActiveBoards(LocalDateTime.now(), mainLocationId(userId));
}

@Override
public String getMission(Long boardId, UUID userId) {
    Board board = boardRepositoryPort
            .findActiveById(boardId, LocalDateTime.now(), mainLocationId(userId))
            .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "종료되었거나 접근할 수 없는 게시판입니다."));
    return board.getMatch().getMission();
}

private Long mainLocationId(UUID userId) {
    Users user = userRepositoryPort.findByIdWithLocations(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    return user.getMainLocation().getLocationId();   // 4단계에서 NOT NULL
}
```

`UserRepositoryPort.findByIdWithLocations(UUID)`는 이미 존재한다.

**에러 메시지**: 기존 `게시판을 찾을 수 없습니다`를 그대로 두면 "종료된 라운드"인지 "남의 동네"인지 "없는 ID"인지 구분되지 않아 디버깅이 어렵다.

**`BoardController`**

```java
@GetMapping
public ApiResponse<BoardListResponse> getBoards(
        @RequestHeader("Authorization") String authorization) {
    UUID userId = resolveUserId(authorization);
    return ApiResponse.of(200, "게시판 목록 조회 성공",
                          BoardListResponse.from(getBoardUseCase.getBoards(userId)));
}
```

`resolveUserId`는 `PostController`에 이미 있다(`Bearer ` 제거 후 `UUID.fromString`). **중복 구현이 생기므로 공용 유틸이나 `ArgumentResolver`로 빼는 것을 검토할 것.**

`PostController.createPost`도 `getBoardUseCase.getMission(boardId, authorId)`로 인자를 늘려야 한다. `authorId`는 이미 그 메서드 안에서 구하고 있다.

**변경 파일 8개**

| 파일 | 변경 |
|---|---|
| `adapter/board/out/BoardJpaRepository.java` | 기존 두 쿼리에 조건 추가 + **fetch join 조회 1개 신규**(8-2용) |
| `adapter/board/out/BoardPersistenceAdapter.java` | 파라미터 전달 |
| `application/board/out/BoardRepositoryPort.java` | 시그니처 |
| `application/board/in/GetBoardUseCase.java` | 시그니처 |
| `application/board/BoardService.java` | `UserRepositoryPort` 주입 + 로직 |
| `adapter/board/BoardController.java` | `Authorization` 수신 |
| `adapter/post/PostController.java` | `getMission(boardId, authorId)` |
| `adapter/post/out/PostPersistenceAdapter.java` | `getBoard()`에 fetch join 조회 사용 (아래 참조) |

**랭킹 영속화 계층도 이 단계에서 새로 만든다** (5단계는 엔티티만 다뤘다).

| 파일 | 내용 |
|---|---|
| `application/ranking/out/RankingRepositoryPort.java` | `addLocationScore(Stage, Location, long)` + **`addUserScore(Stage, Users, long)`** (개인 랭킹 확정 반영) |
| `adapter/ranking/out/LocationRankingJpaRepository.java` | UPSERT 네이티브 쿼리 |
| `adapter/ranking/out/LocationRankingPersistenceAdapter.java` | 포트 구현 |
| `adapter/ranking/out/UserRankingJpaRepository.java` | `(stage_id, user_id)` UPSERT 네이티브 쿼리 (LocationRanking과 동일 패턴) |
| `adapter/ranking/out/UserRankingPersistenceAdapter.java` | 포트 구현 |
| `adapter/ranking/out/SeasonLocationResultJpaRepository.java` | 시즌 확정용 |
| `adapter/ranking/out/SeasonLocationResultPersistenceAdapter.java` | 포트 구현 |
| `application/ranking/out/SeasonResultRepositoryPort.java` | 시즌 확정 결과 저장·조회 |

**⚠️ 8-1은 중간에 멈출 수 없다.** `GetBoardUseCase`의 시그니처가 바뀌면 `BoardService`·`BoardController`·`PostController`가 동시에 깨진다. 8개 파일을 한 번에 고쳐야 컴파일된다.

**파급 — 게시판 목록 API가 인증을 요구하게 된다.** 지금은 헤더 없이 호출되므로 프론트에서 비로그인으로 목록을 부르고 있다면 401이 난다. 배포 전 확인할 것.

**건드리지 않을 것**

- `Board.is_active` — "라운드 전환"이 아니라 "관리자가 특정 게시판을 숨김"이라는 별개 의미
- `Match` / `Board`에 시간 컬럼 추가 — 둘 다 `Stage`에 종속되어 시간 창을 상속받는다
- 게시물 **조회** API(`GET /api/post/{boardId}`) — 지난 라운드 게시물을 읽는 것은 막지 않는다. 막는 것은 **쓰기**뿐이다

#### 8-2. 점수 실시간 누적 (`PostService.createPost()`)

`PostService.createPost()`는 이미 `@Transactional`이 걸려 있고 게시물을 저장한다. 여기에 누적을 이어 붙인다.

##### 라운드는 시계가 아니라 게시판에서 유도할 것

```java
// ✗ 하지 말 것 — 시계를 별도 근거로 삼으면 게시판이 속한 라운드와 어긋날 수 있다
Stage stage = stageRepositoryPort.findCurrent(LocalDateTime.now()).orElseThrow(...);

// ✓ 게시판 → 매치 → 라운드 사슬을 따라간다
Stage stage = board.getMatch().getStage();
```

시계로 찾으면 "지금 시각"이라는 **두 번째 근거**가 끼어든다. 경계를 걸친 게시물의 경우 과거 라운드의 `n`과 현재 라운드의 `C`를 섞어 계산한 뒤 엉뚱한 행에 저장하게 된다. 게시판에서 유도하면 답이 구조적으로 하나뿐이다.

8-1에서 종료된 라운드에는 글을 못 쓰게 막으므로 평상시엔 두 값이 같지만, **검증에 의존하지 않고 구조로 보장하는 편**이 안전하다.

##### 구현

```java
Match match = board.getMatch();
Stage stage = match.getStage();

Double c = stage.getAvgLocationMemberCount();
if (c == null) {
    throw new IllegalStateException("라운드 " + stage.getStageId() + " 매칭 미완료");
}

Location teamLocation = resolveTeamLocation(author, board);

author.touch();     // last_active_at 갱신 + DORMANT → ACTIVE 복귀 (4단계 참조)

// LAZY 프록시끼리 equals가 어긋날 수 있으므로 ID로 비교할 것
int n = match.getTeam1().getLocationId().equals(teamLocation.getLocationId())
        ? match.getTeam1MemberCount()
        : match.getTeam2MemberCount();

double adjust = c / Math.max(n, props.minTeamSize());      // 5
long teamScore = Math.round(score * props.baseMultiplier() * adjust);   // 67.76
long userScore = Math.round(score * props.baseMultiplier());            // 개인 점수 — 조정치 미적용 (확정 내역 1번)

postRepositoryPort.save(
        Post.create(author, board, content, postImage, score, aiReview,
                    teamLocation.getLocationName(), teamLocation));

rankingRepositoryPort.addLocationScore(stage, teamLocation, teamScore);
rankingRepositoryPort.addUserScore(stage, author, userScore);           // user_ranking (stage_id, user_id) UPSERT
```

##### `teamLocation` 결정 — 참가 팀이 아니면 403

`PostService.resolveLocation()`이 이미 **작성자의 `mainLocation`이 매치의 두 팀 중 하나인지 검증**하고 있다. 검증 로직은 유지하되 반환 타입을 `Location`으로 바꾸고 **`null` 반환을 예외로 교체**한다.

```java
private Location resolveTeamLocation(Users author, Board board) {
    Location authorLocation = author.getMainLocation();   // 4단계에서 NOT NULL이 되므로 null 체크 불필요
    Match match = board.getMatch();
    if (authorLocation.getLocationId().equals(match.getTeam1().getLocationId())) return match.getTeam1();
    if (authorLocation.getLocationId().equals(match.getTeam2().getLocationId())) return match.getTeam2();

    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "이 대결에 참가한 동네가 아닙니다.");
}
```

**기존 동작 변경**: 지금은 참가 팀이 아니어도 `location = null`로 **저장까지 됐다.** 앞으로는 거부된다. 이렇게 해야 `post.location_id`(teamLocation FK)를 `nullable = false`로 두고 **"모든 게시물은 참가 팀 소속"이라는 불변식을 DB가 보장**할 수 있다.

검증 없이 `author.getMainLocation()`을 그대로 넣으면 매치와 무관한 동네 유저의 게시물이 team2로 계산되어 엉뚱한 동네에 점수가 쌓인다.

##### UPSERT + 증분

`addLocationScore`는 `(stage_id, location_id)` 행이 없으면 생성하고, 있으면 더해야 한다.

```sql
INSERT INTO location_ranking (stage_id, location_id, location_score, updated_at)
VALUES (:stageId, :locationId, :delta, :now)
ON CONFLICT (stage_id, location_id)
DO UPDATE SET location_score = location_ranking.location_score + :delta,
              updated_at = :now
```

`adapter/ranking/out/LocationRankingJpaRepository`에 `@Modifying @Query(nativeQuery = true)`로 둔다. **`updated_at`은 DB의 `now()`가 아니라 Java에서 만든 `LocalDateTime`을 파라미터로 넘긴다** — RDS가 UTC라 `now()`를 쓰면 9시간 어긋난다. **JPA 영속성 컨텍스트를 우회하므로 같은 트랜잭션 안의 다른 JPA 연산과 순서가 어긋날 수 있다.** `@Modifying(clearAutomatically = true, flushAutomatically = true)` 사용을 검토할 것.

대리키(`location_ranking_id`/`user_ranking_id`)는 INSERT 컬럼 목록에서 생략한다. Hibernate 6/7 + PostgreSQL은 `generated by default as identity`로 생성하므로 문제없다.

##### 시즌 경계는 자동 처리

게시물이 달린 게시판의 라운드 행에 더하므로, 라운드/시즌이 바뀌면 새 행이 알아서 생긴다. 초기화 작업이 필요 없고 이전 시즌 기록도 그대로 남는다.

##### 주의사항

- **`score` 방어 — 실제 위험은 NPE가 아니라 "조용한 0점"이다.** 현재 `GeminiAdapter.parse()`는 `result.path("score").asInt()`를 쓰는데, Jackson의 `path()` + `asInt()`는 응답에 `score`가 누락되어도 예외나 null 없이 **0을 반환**한다(응답 JSON 자체가 깨진 경우는 `readTree`가 예외를 던져 게시물이 안 만들어지므로 안전). 즉 잘못된 응답이 **0점 게시물로 조용히 저장**될 수 있다. `PostController`에서 채점 직후 **score가 null이거나 0~100 범위 밖이면 502로 거부**하는 검증을 넣고, `GeminiAdapter.parse()`에도 `hasNonNull("score")` 확인을 추가할 것 (`AiReviewResult.score`가 `Integer`인 이상 다른 경로로 null이 올 가능성 대비도 겸한다)
- **점수 누적은 게시물 저장과 같은 트랜잭션에 묶을 것.** 분리하면 게시물은 저장됐는데 점수는 누락된다
- `PostService.createPost()`가 게시판을 가져오는 경로는 `postRepositoryPort.getBoard(boardId)` → **`BoardJpaRepository.findById()`** 다(`PostPersistenceAdapter`가 `BoardJpaRepository`를 주입받아 쓴다). `match`, `team1`, `team2`, `stage`가 전부 LAZY라 위 코드에서 추가 쿼리가 여러 번 나간다. **`BoardJpaRepository`에 세 번째 쿼리를 추가하고 `PostPersistenceAdapter.getBoard()`가 그것을 쓰게 할 것.** `findActiveByIdWithMatch`는 `now`·`locationId` 파라미터가 붙어 재사용할 수 없으므로 별도 메서드가 필요하다

```java
@Query("""
        select b from Board b
        join fetch b.match m
        join fetch m.team1
        join fetch m.team2
        join fetch m.stage
        where b.boardId = :boardId
        """)
Optional<Board> findByIdWithMatchGraph(@Param("boardId") Long boardId);
```
- `PostService`가 `RankingRepositoryPort`를 주입받게 되어 도메인 간 의존이 생긴다. 대안은 별도 `ScoreAccrualUseCase`를 두고 `PostService`가 그것을 호출하는 것
- **`K`와 `minTeamSize`는 주입받아야 한다.** `application.yaml`의 `ranking:` 블록을 `@ConfigurationProperties(prefix = "ranking")` record로 받아 `PostService`에 주입한다. 3단계에서 YAML 블록과 함께 만들 것

```java
@ConfigurationProperties(prefix = "ranking")
public record RankingProperties(double baseMultiplier, int minTeamSize, int dormantDays) {}

@ConfigurationProperties(prefix = "season")
public record SeasonProperties(int roundDays, int roundsPerSeason,
                               int prefetchDays, int matchOpenAheadDays) {}
```

`@EnableConfigurationProperties` 또는 `@ConfigurationPropertiesScan`이 필요하다.

#### 8-3. 시즌 최종 순위 확정

`application/ranking/in/FinalizeSeasonUseCase` (구현체 `application/ranking/RankingService`)

```
1. 확정 대상 시즌 조회
   - 해당 season의 Stage가 정확히 roundsPerSeason(4)개 존재하고
   - 그 4개가 모두 ended_at < now 이고
   - season_location_result에 그 season 행이 없는 것
2. 각 season에 대해:
   a. location_ranking에서 그 시즌 stage들의 점수를 location별로 SUM
   b. 점수가 없는 동네는 0으로 채움 (전체 Location 기준)
   c. 내림차순 정렬해 final_rank 부여
   d. (season, location_id) 로 저장
```

**1번의 "정확히 4개" 조건이 중요하다.** 라운드 생성이 밀려 season N의 Stage가 2개만 있는 상태에서 시간이 흐르면, 이 조건이 없을 경우 **2라운드짜리 시즌으로 확정**되어 버린다. `season_location_result`는 한 번 쓰이면 바뀌지 않으므로 자동 복구도 안 된다.

**멱등성**은 "확정 안 된 시즌만 조회"하는 1번 조건에서 나온다. 유니크 제약은 동시 실행 시의 안전망이다.

시즌당 한 번(28일에 한 번)만 실제 작업이 일어나므로 비용은 사실상 없다.

#### 8-4. 지역 스위칭 적용

`application/match/in/ApplyLocationSwapUseCase` (구현체 `application/match/StageService`)

```
current = 현재 라운드 (startedAt <= now < endedAt)
if (current == null) return;                 // 라운드 공백 — 아무것도 안 함
if (current.isSwapApplied()) return;         // 이미 적용됨

pendingLocationSwap = true 인 유저 전원 applyLocationSwap()
current.markSwapApplied()
```

라운드당 정확히 한 번 실행된다. `swapApplied` 플래그 덕에 배치가 실패해도 다음 실행에 자동 복구된다.

**필요한 포트 메서드** (현재 `UserRepositoryPort`에는 `findByIdWithLocations` 하나뿐이다)

```java
// application/user/out/UserRepositoryPort.java
List<Users> findAllByPendingLocationSwapTrue();

// application/match/out/StageRepositoryPort.java  (6단계에서 생성)
Optional<Stage> findCurrent(LocalDateTime now);                              // 8-4
Optional<Stage> findLatestByEndedAt();                                       // 6단계
List<Stage> findUpcomingWithoutMatch(LocalDateTime now, LocalDateTime threshold);  // 7단계
List<Stage> findAllBySeason(Integer season);                                 // 8-3
```

#### 8-5. 휴면 전환

`application/user/in/DeactivateInactiveUsersUseCase` (구현체 `application/user/UserService` — 신규)

```
last_active_at < now() - dormantDays(7일) 인 유저를 DORMANT로
```

`application/user/`에는 현재 `out/` 포트만 있고 서비스·유스케이스가 없으므로 새로 만든다.

**필요한 포트 메서드**

```java
// application/user/out/UserRepositoryPort.java
List<Users> findAllByLastActiveAtBeforeAndStatus(LocalDateTime threshold, UserStatus status);
```

#### 8-6. 검증 배치 (선택 — 지금은 만들지 않아도 됨)

실시간 누적의 약점은 **누적값만 남고 근거가 사라진다**는 점이다. 중복 요청, 계산 버그, 삭제 정책 불일치로 값이 조용히 틀어질 수 있다. 랭킹은 **틀려도 아무도 모르는 종류의 데이터**라 안전망이 있으면 좋다.

```
라운드 종료 후:
  1. 그 라운드의 Post들을 전부 읽어 점수를 처음부터 재계산
  2. location_ranking 누적값과 비교
  3. 다르면 로그 남기고 보정 (또는 알림만)
```

**재계산이 가능한 이유는 3단계의 스냅샷 컬럼 덕분이다.** `Post.score`, `Post.teamLocation`, `Match.team1/2MemberCount`, `Stage.avgLocationMemberCount`가 전부 박제된 값이라 6개월 뒤에 계산해도 같은 답이 나온다.

**⚠️ 재계산 범위는 미결 5번(삭제 시 점수 정책)과 일치시켜야 한다.** 삭제는 soft delete(`is_active = false`)라 원본 행이 남는다.

| 미결 5번 선택 | 8-6 재계산 대상 | 이유 |
|---|---|---|
| 빼지 않는다 | 비활성 포함 **전체** Post | 활성만 재계산해 "보정"하면 삭제분이 빠진 값으로 덮여 사실상 "뺀다"가 되어 정책과 모순 |
| 뺀다 | **활성** Post만 | 삭제분이 빠진 값이 곧 정답 |

정책과 어긋난 범위로 "보정"을 켜면 검증 배치가 오히려 데이터를 망가뜨린다. 확신이 없으면 **알림만** 모드로 시작할 것.

---

### 9단계 — 스케줄러 + ShedLock

#### ⚠️ `@EnableScheduling`을 먼저 추가할 것

`TikitakaApplication`에 `@SpringBootApplication`만 있고 스케줄링 설정이 전혀 없다. **이것 없이 `@Scheduled`를 붙이면 아무 일도 일어나지 않고 에러도 나지 않는다** — 가장 발견하기 어려운 종류의 실패다.

```java
@SpringBootApplication
@EnableScheduling            // 추가
public class TikitakaApplication { ... }
```

#### 스케줄

| 시각 (KST) | 배치 | 유스케이스 | 실제 작동 | 등록 시점 |
|---|---|---|---|---|
| **00:10** | 지역 스위칭 적용 | `ApplyLocationSwapUseCase` | 라운드당 1회 | 지금 |
| 05:00 | 휴면 전환 | `DeactivateInactiveUsersUseCase` | 매일 | **로그인 기능 완성·병합 후** (4단계 경고 참조) |
| 05:05 | 미래 라운드 생성 | `EnsureFutureStagesUseCase` | 가끔 | 지금 |
| 05:10 | **매치·게시판 생성** | `OpenRoundUseCase` | 라운드당 1회 | 지금 |
| 05:20 | 시즌 최종 순위 확정 | `FinalizeSeasonUseCase` | 시즌당 1회 | 지금 |

전부 **"할 일이 있으면 하고 없으면 아무것도 안 하는"** 구조다. 매일 돌아도 안전하고, 며칠 밀려도 따라잡는다.

**시각 선택의 근거**

- **스위칭 00:10** — 라운드 경계(자정) 직후여야 지난 라운드 신청분만 반영된다. 05:00으로 미루면 5시간 동안 옛 소속으로 남는다
- **매치 생성 05:10** — 라운드 시작 **전날** 것을 만든다. 라운드 시작 후에 만들면 자정~05:10 사이 게시판이 0건이 되어 매주 5시간 서비스가 멈춘다
- **라운드 생성(05:05) → 매치 생성(05:10)** — 라운드 행이 있어야 매치를 붙일 수 있다
- **휴면 전환(05:00) → 매치 생성(05:10)** — ACTIVE 인원 집계가 정확해진다

**한 배치에 묶으면 안 되는 것**: 스위칭 적용과 매치 생성은 시점이 반대다(경계 직후 vs 전날). 묶으면 스위칭이 하루 일찍 적용되어 진행 중인 라운드에서 유저가 튕긴다.

RDS 백업(03:00~03:30)과 유지 관리(월 04:00~04:30)를 피한 시간대다.

#### 예시

```java
@Component
@RequiredArgsConstructor
public class MatchScheduler {
    private final EnsureFutureStagesUseCase ensureFutureStages;
    private final OpenRoundUseCase openRound;
    private final ApplyLocationSwapUseCase applyLocationSwap;

    @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Seoul")     // 00:10
    @SchedulerLock(name = "applyLocationSwap", lockAtMostFor = "10m")
    public void applySwaps() { applyLocationSwap.execute(); }

    @Scheduled(cron = "0 5 5 * * *", zone = "Asia/Seoul")      // 05:05
    @SchedulerLock(name = "ensureFutureStages", lockAtMostFor = "10m")
    public void ensureStages() { ensureFutureStages.execute(); }

    @Scheduled(cron = "0 10 5 * * *", zone = "Asia/Seoul")     // 05:10
    @SchedulerLock(name = "openRound", lockAtMostFor = "10m")
    public void openUpcomingRounds() { openRound.execute(); }
}
```

**타임존**: 컨테이너 TZ가 `Asia/Seoul`로 설정되어 있지만(`docker-compose.yml`), `@Scheduled`에 `zone`을 명시할 것.

#### ShedLock

현재 EC2 1대라 없어도 동작하지만, 인스턴스를 2대로 늘리는 순간 배치가 중복 실행된다.

```kotlin
implementation("net.javacrumbs.shedlock:shedlock-spring:<version>")
implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:<version>")
```

**⚠️ 버전 확인 필수.** 이 프로젝트는 Spring Boot **4.1**(Spring Framework 7, `spring-boot-starter-webmvc` 신규 네이밍)이다. 널리 쓰이는 ShedLock 5.x는 Spring Framework 6 / Boot 3 대상이므로 **그대로 넣으면 빌드나 런타임에서 깨질 수 있다.** Boot 4 호환 버전을 확인하고 넣을 것. 호환 버전이 없으면 ShedLock을 미루고 인스턴스 1대 전제로 진행해도 된다.

추가로 필요한 것:

- `LockProvider` 빈 (`JdbcTemplateLockProvider`)
- `@EnableSchedulerLock(defaultLockAtMostFor = "10m")`
- **락 테이블 DDL** (`shedlock` 테이블 — 자동 생성되지 않는다)

---

### 10단계 — 로컬 검증 후 배포

**라운드/시즌 동작**

1. `ended_at` 경계를 넘겼을 때 게시판 목록이 자동으로 바뀌는가 (과거/미래 라운드 데이터를 넣고 조회)
2. 종료된 라운드의 게시판 ID로 글을 쓰면 거부되는가
3. 다른 동네 게시판에 글을 쓰면 403이 나는가
4. `EnsureFutureStagesUseCase`를 두 번 실행해도 라운드가 중복 생성되지 않는가
5. `EnsureFutureStagesUseCase`가 여러 라운드 밀린 상태에서 밀린 것을 모두 채우는가
6. `OpenRoundUseCase`를 두 번 실행해도 매치가 중복 생성되지 않는가
7. `OpenRoundUseCase`가 한 동네를 두 경기에 넣지 않는가
7-1. ACTIVE 0명 동네가 매칭에서 제외되는가 (매치·게시판이 생기지 않는가)
7-2. 동네 수가 홀수일 때 BYE 셀프 매치(team2=team1)가 생기고, 그 게시판에서 점수가 정상 누적되는가
7-3. 다음 홀수 라운드에서 직전 BYE 동네가 아닌 다른 동네가 부전승으로 선정되는가 (로테이션)
7-4. 연속 두 라운드에서 같은 두 동네가 다시 붙지 않는가 (직전 상대 회피 — 동네 2개뿐인 극단 상황 제외)

**점수**

8. 게시물을 올렸을 때 해당 라운드 행에 점수가 누적되는가
9. 라운드가 바뀐 뒤 게시물을 올리면 새 라운드 행이 생기고 이전 행은 그대로 남는가
10. 점수 누적이 게시물 저장과 같은 트랜잭션인가 — 중간에 예외를 던져 롤백되는지 확인
11. **인구 규모가 다른 동네의 총점이 대등한가** — 30명 동네와 200명 동네가 같은 참여율·같은 AI 평균일 때 (이 설계의 핵심 목표)
12. `minTeamSize` 하한이 실제로 걸리는가 (1~2명짜리 동네)
13. 라운드 중 유저가 가입해도 먼저 올린 게시물과 나중에 올린 게시물의 점수가 같은가
13-1. 게시물 작성 시 `user_ranking`의 해당 라운드 행에도 개인 점수(`round(AI × K)`, 조정치 미적용)가 누적되는가

**유저 상태**

14. 휴면 유저가 인원수 집계에서 제외되는가 *(휴면 배치를 등록하지 않았으므로 `status`를 수동으로 `DORMANT`로 바꿔 검증할 것)*
15. 라운드 중 지역을 스위칭해도 그 라운드 점수는 기존 동네에 들어가고, 다음 라운드부터 새 동네로 바뀌는가
16. `sub_location`이 없는 유저의 스위칭 요청이 거부되는가

**시즌 확정**

17. `FinalizeSeasonUseCase`를 두 번 실행해도 결과가 중복 생성되지 않는가
18. 라운드가 4개 미만인 시즌은 확정되지 않는가
19. 점수가 없는 동네도 `final_score = 0`으로 기록되는가
20. 동네 프로필 조회 시 시즌별 등수 히스토리가 나오는가

통과하면 EC2 배포:

```bash
# 로컬
git add . && git commit -m "..." && git push

# EC2 (Instance Connect로 접속)
cd ~/TikiTaka-server
git pull
docker compose up -d --build
docker compose logs app --tail 60
```

#### 배포 후 필수 확인 2가지

**① 컬럼이 실제로 생겼는지** — `ddl-auto: update`는 DDL이 실패해도 경고만 남기고 기동한다(§8 참조).

```bash
docker run --rm -it postgres:16-bookworm psql \
  -h <RDS 엔드포인트> -U raon -d tikitaka \
  -c '\d stage' -c '\d match' -c '\d users' -c '\d location_ranking'
```

**② 현재 라운드의 `swap_applied` 선세팅** — 안 하면 배포 다음 자정에 진행 중인 라운드에서 스왑이 발동한다(4단계 경고 참조).

```sql
update stage set swap_applied = true
 where started_at <= (now() at time zone 'Asia/Seoul')
   and (now() at time zone 'Asia/Seoul') < ended_at;
```

---

## 8. 작업 시 주의사항

### ⚠️ `ddl-auto: update`의 한계 — 가장 중요

`application.yaml`에 `spring.jpa.hibernate.ddl-auto: update`가 설정되어 있다. 이 모드는 **컬럼/테이블 추가만 수행하고, 삭제·타입 변경·PK 변경·제약 추가는 하지 않는다.**

| 변경 | 자동 반영 | 대응 |
|---|---|---|
| `Stage.ended_at` (not null) | ⚠️ | 기존 행이 있으면 실패 → 데이터 정리 또는 임시 nullable → 백필 → not null |
| `Stage.is_active` 제거 | ❌ | `ALTER TABLE stage DROP COLUMN is_active` |
| `stage` `(season, round)` 유니크 | ❌ | `ALTER TABLE stage ADD CONSTRAINT ... UNIQUE (season, round)` |
| `Stage.avg_location_member_count` (nullable) | ✅ | — |
| `Stage.swap_applied` (not null, default false) | ✅ | `@ColumnDefault("false")`로 기존 행 자동 채움. 단 **시드 라운드는 수동으로 true 처리**(2단계 참조) |
| `Match.team1/2_member_count` (not null) | ⚠️ | 기존 행이 있으면 실패 |
| `match` 유니크 제약 2개 | ❌ | 수동 `ALTER TABLE` |
| `Match.match_type` ORDINAL(int) → STRING(varchar) | ❌ | 타입 변경 불가. 재생성 필요 — **아래 경고 참조** |
| `Match.match_type` not null + DEFAULT | ❌ | 위 재생성에 포함됨 |
| `Post.location_id` (teamLocation FK, not null) | ⚠️ | 기존 행이 있으면 실패 |
| `Users.last_active_at` (not null) | ⚠️ | 기존 유저가 있으면 **컬럼이 안 생김** (아래 경고) |
| `Users.status` (not null) | ⚠️ | 기존 유저가 있으면 **컬럼이 안 생김** (아래 경고) |
| `Users.pending_location_swap` (not null) | ✅ | `@ColumnDefault("false")`가 DDL에 반영되어 PostgreSQL이 기존 행을 자동으로 채운다 |
| `Users.main_location_id` nullable → **not null** | ❌ | 기존 행 중 null이 있으면 실패. 정리 후 `ALTER TABLE users ALTER COLUMN main_location_id SET NOT NULL` |
| `LocationRanking` PK 변경 | ❌ | **`DROP TABLE location_ranking` 후 재생성**이 가장 간단 (타입·인덱스 변경도 함께 해결됨) |
| `UserRanking` PK 변경 | ❌ | 동일 — **`DROP TABLE user_ranking` 후 재생성** (개인 랭킹 함께 구현 확정에 따라 필요) |
| `season_location_result` 신규 | ✅ | — |

#### ⚠️ `match` 테이블 재생성 시 주의

`match`만 `DROP TABLE ... CASCADE` 하면 안 된다. `board.match_id`가 `nullable = false, unique = true` FK이므로 CASCADE가 **그 FK 제약을 조용히 삭제하고** `board`·`post` 행을 고아로 남긴다. 이후 Hibernate가 `match`를 재생성해도 존재하지 않는 매치를 참조하는 `board` 행 때문에 FK 재생성이 실패하거나 8-1 fetch join이 0건이 된다.

**`post` → `board` → `match` 순으로 함께 비울 것.**

```sql
DROP TABLE post, board, match CASCADE;
-- 앱 재기동 → Hibernate가 세 테이블을 새 스키마로 재생성
```

**`match`만 DROP하면 안 된다.** `ddl-auto: update`는 제약을 추가하지 않으므로(위 표 참조), CASCADE로 지워진 `board.match_id` FK를 Hibernate가 **다시 만들어주지 않는다.** 세 테이블을 함께 떨어뜨려야 FK까지 온전히 재생성된다.

`location_ranking`·`user_ranking`은 참조하는 테이블이 없어 단독 DROP이 안전하다.

**같은 논리가 `stage`에도 적용된다** — `match.stage_id`가 `stage`를 참조하므로 `stage`를 재생성해야 한다면 `post`, `board`, `match`도 함께 떨어뜨려야 한다.

**개발 단계 최단 경로 (권장)**: 어차피 테스트 데이터뿐이라면 관련 테이블을 한 번에 비우는 것이 가장 안전하고 빠르다. FK 사슬과 PK 변경 문제가 전부 한 번에 해결된다.

```sql
DROP TABLE post, board, match, stage, location_ranking, user_ranking CASCADE;
-- 앱 재기동 → Hibernate가 새 스키마로 재생성 → 첫 라운드 시드 + swap_applied 선세팅 + 키워드 시드 확인
```

**작업 전 확인**: `stage`, `match`, `board`, `post`, `users`, `location_ranking` 테이블에 데이터가 있는지 먼저 볼 것. 테스트 데이터뿐이라면 `DROP TABLE ... CASCADE` 후 Hibernate가 재생성하게 하는 것이 가장 빠르다.

```sql
select 'stage', count(*) from stage
union all select 'match', count(*) from match
union all select 'post', count(*) from post
union all select 'users', count(*) from users
union all select 'location_ranking', count(*) from location_ranking;
```

#### ⚠️ RDS는 UTC다 — 수동 SQL의 `now()` 주의

앱 컨테이너는 `TZ: Asia/Seoul`이지만 **RDS PostgreSQL의 기본 `timezone`은 UTC**다. 로컬 `docker-compose.local.yml`의 db 컨테이너만 `TZ`/`PGTZ`가 설정되어 있어 로컬에서는 문제가 드러나지 않는다.

`started_at`·`ended_at`·`last_active_at`은 Java `LocalDateTime`(시간대 없는 KST 값)으로 저장되므로, RDS에서 `now()`와 직접 비교하면 **9시간 어긋난다.**

**수동 SQL에서는 반드시 변환할 것.**

```sql
-- ✗ RDS에서 9시간 어긋남
where started_at <= now()

-- ✓
where started_at <= (now() at time zone 'Asia/Seoul')
```

영향받는 곳: 라운드 경계 조회, `swap_applied` 선세팅, `last_active_at` 백필, UPSERT의 `updated_at`.

애플리케이션 코드는 `LocalDateTime.now()`가 컨테이너 TZ(KST)를 따르므로 영향이 없다. **수동으로 psql을 칠 때만** 신경 쓰면 된다.

> 근본 해결을 원하면 RDS 파라미터 그룹에서 `timezone = Asia/Seoul`로 바꿔도 된다. 다만 기본 파라미터 그룹은 수정할 수 없어 새 그룹을 만들어 적용하고 재부팅해야 한다.

#### ⚠️ DDL 실패는 부팅을 막지 않는다

`ddl-auto: update`는 DDL이 실패해도 **`GenerationTarget encountered exception` 경고만 남기고 앱이 정상 기동한다.** "떴으니 성공"이라고 판단하면 컬럼이 없는 채로 런타임에 SQL 오류가 산발한다.

**각 단계 후 컬럼이 실제로 생겼는지 확인할 것.**

```sql
\d stage
\d match
\d users
\d location_ranking
```

**권장**: 이 시점에 **Flyway 도입**을 검토할 것. 지금은 실사용 데이터가 없어 `DROP TABLE` 후 재생성이 가능하지만, 실사용자가 붙은 뒤에는 불가능해진다. 마이그레이션 도구를 넣기에 지금이 가장 싼 시점이다.

### 그 외

- **프로젝트 규칙**: 사용자 허락 없이 설정·코드를 임의로 수정하지 말 것
- **로컬 DB와 RDS는 완전히 별개다.** 로컬에서 만든 데이터는 배포 서버에 없다
- EC2는 arm64(Graviton)다. 도커 이미지 추가 시 arm64 지원 여부 확인
- EC2 RAM이 1GB뿐이라 빌드가 10분 이상 걸린다. **개발 중 검증은 반드시 로컬에서 할 것**
- `.env`는 gitignore 대상이라 EC2에 자동 전달되지 않는다. 환경변수를 추가하면 EC2 `.env`도 수동 갱신
- `Dockerfile`의 `EXPOSE 8080`과 `server.port: 8090`이 불일치한다. 동작에는 지장 없으나(`EXPOSE`는 문서화용) 배포 트러블슈팅 시 혼란 요인이다

---

## 9. 진행 상황 요약

| 단계 | 내용 | 상태 |
|---|---|---|
| 1 | 도메인 규칙 확정 (점수 공식, K=67.76, 라운드 7일/시즌 4라운드, 휴면·스위칭 정책) | ✅ |
| 2 | `Stage`에 `ended_at` + `(season, round)` 유니크, `is_active` 제거, **첫 라운드 시드** | ✅ 2026-08-11 (로컬 DB 재생성·시드·유니크 검증 완료. 시드: 시즌1 라운드1 = 8/11~8/18) |
| 3 | `Match`·`Stage`·`Post`에 스냅샷 컬럼 + `match` 유니크 제약 + `keyword` 시드 확인 | ✅ 2026-08-11 (로컬 검증 완료: match STRING+BYE+체크제약+FK4개+유니크2개, post.location_id FK, stage C·swap_applied 추가·시드 유지, swap_applied 선세팅, yaml 설정+Properties 2개+@ConfigurationPropertiesScan. **keyword 시드는 7단계 전 별도 확인 필요**) |
| 4 | `Users`에 휴면 + 지역 스위칭 예약 필드 및 도메인 메서드 | ✅ 2026-08-11 (로컬: 수동 ALTER+백필로 status·last_active_at·pending_location_swap 추가, main_location_id NOT NULL 전환, @PrePersist·도메인 메서드 4개·UserStatus·SubLocationNotSetException+핸들러. 휴면 배치는 로그인 병합 전까지 미등록 유지) |
| 5 | 랭킹 테이블 재설계 (`location_ranking`·`user_ranking` 라운드 단위 + `season_location_result` 신규) | ✅ 2026-08-11 (로컬: 랭킹 2종 DROP 재생성 — 대리키 PK·stage FK·bigint·유니크·rank 제거·@PrePersist, season_location_result 신규+create() 팩토리. **스키마 단계 전체 완료 — DB가 최종 ERD와 일치**) |
| 6 | 라운드 자동 생성 (`EnsureFutureStagesUseCase`) | ✅ 2026-08-11 (신규 5파일: 유스케이스·StageService·포트·JPA리포지토리·어댑터. 컴파일·기동 검증 통과. 동작 검증은 7단계 어드민 트리거로 수행 예정) |
| 7 | **매치·게시판 자동 생성 (`OpenRoundUseCase`)** | ✅ (신규 15+수정 10 파일. 로컬 동작 검증: 라운드 30일치 생성, BYE 셀프 매치·로테이션, 0명 제외, 인원·C 스냅샷, Gemini 미션 생성 확인. 구현 중 재결정 2건 반영 — 매칭은 랜덤+직전 상대 회피, 미션은 매치별 각각 생성) |
| 8 | 게시판 필터 + 점수 누적 + 스위칭 적용 + 시즌 확정 + 휴면 전환 | ⬜ |
| 9 | `@EnableScheduling` + 스케줄러 + ShedLock | ⬜ |
| 10 | 로컬 검증 후 배포 | ⬜ |

**다음에 할 일: 8단계.** (미결 5번·6번을 8단계 진입 시 결정할 것) (2~5단계는 2026-08-11 로컬 완료 — 스키마 공사 끝. 배포(10단계) 때 RDS에도 동일 절차 필요: post/board/match/stage + location_ranking/user_ranking DROP → 앱 기동 → 시드 → swap_applied 선세팅 + users 수동 ALTER·백필. RDS는 UTC이므로 §8 시간대 주의)

| 미결 사항 | 상태 (2026-08-11 결정) | 마감 |
|---|---|---|
| 1번 — `UserRanking` 사용 여부 | ✅ **함께 구현** — `(stage_id, user_id)` 재설계 + 8-2 개인 점수 누적 | — |
| 3번 — 미션 조달 | ✅ **키워드 조합 + AI 다듬기** — 기존 `keyword` 테이블 재사용, 실패 시 템플릿 폴백 (`mission_pool` 안 폐기) | — |
| 2번 — 매칭 알고리즘 | ✅ **랜덤 + 직전 라운드 상대 회피** + ACTIVE 0명 동네 제외 (7단계 구현 중 재결정 — 인원수 근접 안은 매주 같은 상대 반복 부작용으로 폐기) | — |
| 4번 — 홀수 처리 | ✅ **셀프 미션 매치(BYE)** — team2=team1, BYE 이력 로테이션, 점수 정상 누적 | — |
| 5번 — 게시물 삭제 시 점수 처리 | ✅ **뺀다** — 스냅샷으로 재계산해 음수 UPSERT, 8-6은 활성 게시물만 재계산 | — |
| 6번 — 시즌 확정 후 원본 변경 정책 | ✅ **번복하지 않는다** — 확정 결과 불변, 부정행위는 운영 제재로 | — |

### 아직 계획에 없는 것 (별도 작업)

이 문서의 2~10단계는 **데이터가 올바르게 쌓이게 만드는 것**까지만 다룬다. 다음은 포함되지 않았다.

- **지역 랭킹 조회 API** — `location_ranking`, `season_location_result`를 읽는 유스케이스·컨트롤러. §6에 JPQL만 제시했다
- **개인 랭킹 조회 API** — `user_ranking`을 읽는 유스케이스·컨트롤러 (누적까지는 8-2에 포함, 조회는 별도)
- **동네 프로필 API** — 시즌별 등수 히스토리 조회
- **지역 스위칭 요청 API** — 4단계는 `requestLocationSwap()` 도메인 메서드만 추가한다. 이를 호출하는 유스케이스·컨트롤러가 필요하다
- **키워드 관리 어드민 API** — 미션 생성에 쓰는 `keyword` 테이블은 당분간 SQL INSERT로 관리한다

10단계 검증 항목 15·16·20번은 위 API가 있어야 수행할 수 있다. 자동화 자체와는 분리된 작업이므로 순서는 자유롭게 정하면 된다.
