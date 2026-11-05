# pleos-connect-maji · 맞이

기다리는 동안 그 사람의 자리를 미리 맞춰 두고, 문이 열리는 순간 먼저 인사를 건네는 차량용(Android Automotive) 앱이에요.

> 지금은 빈 프로젝트예요. 실행하면 화면 가운데에 "맞이 빈 프로젝트 실행 성공"만 보여요.

## 개발 환경

| 항목 | 버전 |
| --- | --- |
| JDK | 17 |
| Gradle Wrapper | 8.9 |
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.0.21 (Jetpack Compose) |
| minSdk / compileSdk / targetSdk | 28 / 34 / 34 |
| Pleos Vehicle SDK | `ai.pleos.playground:Vehicle:2.3.2` |
| 에뮬레이터 | Pleos Connect Emulator 2.1 (Android API 34) |

라이브러리 버전은 `gradle/libs.versions.toml`에서 관리해요.

## 실행 방법

1. Android Studio에서 이 폴더를 열어요. **Settings > Build Tools > Gradle > Gradle JDK**를 17로 맞춰요.
2. Pleos Connect 에뮬레이터를 켜요. AVD는 **Cold boot**로 설정해야 VHAL이 동작해요.
3. 빌드하고 설치해요.

   ```bash
   ./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk 생성
   ./gradlew installDebug         # 켜져 있는 에뮬레이터에 설치
   adb shell am start -n com.maji/.MainActivity
   ```

4. Virtual Console로 차량 신호를 바꿔 볼 수 있어요.

   ```bash
   adb forward tcp:8088 tcp:8088
   open http://127.0.0.1:8088/
   ```

> 클라이언트 시크릿 같은 인증 정보는 저장소에 커밋하지 마세요.

## 패키지 구조와 담당

모든 코드는 `app/src/main/java/com/maji/` 아래에 있어요. 팀이 함께 쓰는 함수와 데이터 모양은 `contract/Contract.kt`에 정해 두었어요.

| 담당 | 패키지 | 역할 | 주요 인터페이스 |
| --- | --- | --- | --- |
| 공통 | `contract` | 팀 함수 약속과 공통 데이터 (`Profile`, `Climate`, `PassengerState`, `Capabilities`) | — |
| A | `signal` | 차량 신호: P단·문·주차 브레이크 감지, 주행 중 화면 잠금 | `VehicleSignals` |
| B | `control` | 차량 제어: 시트·열선·온도·창문·조명 제어, 원래 상태 저장·복구, 대체 모드 | `VehicleControl` |
| C | `flow` | 맞이 흐름: 대기 → 준비 → 탑승 → 귀갓길 → 하차 단계 관리, 내비 목적지 설정 | `MajiFlow` |
| D | `profile` | 프로필: 프로필 저장, 계절 프리셋, 오늘만의 인사, 선호 학습 | `ProfileRepository` |
| E | `voice` | 음성: 인사 음성 파일과 노래 재생, 오디오 포커스 | `Greeter` |
| C·D | `ui` | 공통 화면: 맞이 시작·진행 화면, 프로필 화면, 공통 디자인 부품 | — |

호출 방향: C(`flow`)가 A(`signal`)의 신호를 받아서 B·D·E 함수를 불러요. B·D·E끼리는 서로 직접 부르지 않아요.

## 브랜치

`main`(제출용) ← `develop`(통합) ← `종류/모듈-할일` 형식의 작업 브랜치 (예: `feat/signal-park`, `chore/build-setup`)
