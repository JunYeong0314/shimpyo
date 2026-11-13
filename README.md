# 쉼표 (Shimpyo)

장거리 운전자에게 졸린 정도를 먼저 묻고 가까운 휴게소로 안내한 뒤, 주차하면 조명과 공조, 시트, 창문, 도어를 조절해 차 안에서 15분 동안 쉬고 개운하게 깨어날 수 있게 돕는 Pleos Connect 앱입니다.

현대자동차그룹 차량용 앱 개발 공모전(Code your App into Pleos App Market) 출품작입니다.

## 주요 기능

1. 쉬지 않고 1시간 30분을 운전하면 졸린 정도를 1부터 9까지 숫자로 묻습니다. 졸음이 몰리는 새벽 2시에서 6시, 오후 2시에서 4시에는 1시간이 지나면 묻습니다. 회전 지점이나 단속카메라 앞, 방향지시등을 켜거나 브레이크를 밟는 중에는 말을 걸지 않고 조용한 구간을 기다립니다.
2. 졸린 정도가 7 이상이거나 2시간 넘게 운전했으면 휴게소 한 곳을 추천합니다. 후보마다 실제 도로 거리와 도착 시간을 받고 카페와 충전소, 연료나 배터리 잔량을 함께 따집니다. "응"이라고 답하면 Pleos 내비가 그곳으로 안내합니다.
3. 휴게소에 도착하면 주차하라고 알려 주고, 기어를 P로 바꾸면 15분 카냅과 커피냅, 5분 스트레칭 가운데 하나를 고르게 합니다.
4. 쉬는 동안 엠비언트 조명을 낮추고 실내등을 끄며, 공조를 23°C 외기 유입과 약한 바람으로 맞춥니다. 계절에 따라 시트 열선이나 통풍을 켜고, 창문을 5% 열어 환기하고, 도어를 잠급니다. 동승자가 있으면 운전석 쪽만 바꿉니다.
5. 끝나기 2분 전부터 조명을 해 뜨는 색으로 밝히고 시원한 바람과 시트 통풍으로 깨운 뒤, 숫자를 거꾸로 말하게 해서 반응을 확인합니다.
6. 기어를 D로 바꾸면 조명과 공조, 시트, 창문, 도어를 쉬기 전 상태로 되돌립니다. 쉬는 도중 기어가 P에서 벗어나도 바로 멈추고 원래대로 돌려놓습니다.

이 밖에 연속 운전 시간과 쉬기 버튼이 있는 홈 위젯, 내비 옆에 나란히 띄우는 1:1 분할 화면, 한국어와 영어(화면과 음성 모두)를 지원합니다.

## 사용한 Pleos Connect SDK

| SDK | 버전 | 쓰는 곳 |
|---|---|---|
| Vehicle | 2.3.2 | 기어, 좌석 탑승, 방향지시등, 브레이크, 조향각, 와이퍼, 연료와 배터리 잔량 읽기. 엠비언트 조명, 실내등, 공조, 시트 열선과 통풍, 창문, 도어 잠금 제어 |
| NaviHelper | 3.0.5 | 회전 안내와 단속카메라, 주의 구간(말을 걸 시점), 휴게소까지 도로 거리, 경로 안내, 도착 이벤트, 즐겨찾기 |
| TextToSpeech | 3.3.1 | 모든 음성 안내 (온디바이스) |
| SpeechToText | 3.3.1 | 졸린 정도, 예와 아니오, 휴식 방식, 컨디션 확인 답변 (온디바이스) |
| FusedLocation | 1.1.3 | 현재 위치로 가까운 휴게소 후보 고르기 |

## 구조

Kotlin과 Jetpack Compose로 만든 단일 모듈 Android 앱입니다.

- **화면**: 홈, 주행, 휴식 화면마다 MVI 계약(State, Intent, Effect)과 `MviViewModel`을 둡니다. 주행 중 화면에는 큰 숫자 하나와 큰 버튼 하나만 보입니다.
- **엔진**: `engine/ShimpyoEngine`은 포그라운드 서비스에서 도는 상태 머신입니다. 운전 감시, 질문, 휴게소 추천, 안내, 주차 대기, 휴식, 기상, 컨디션 확인, 복원 단계를 차례로 오가며, 화면과 위젯은 엔진의 상태만 구독합니다.
- **도메인 규칙**: 언제 묻고 언제 쉬라고 할지(`FatigueRules`), 지금 말해도 되는지(`SpeakGuard`), 음성 답을 어떻게 해석할지(`UtteranceParser`), 실내를 어떻게 바꾸고 되돌릴지(`CabinOrchestrator`)를 SDK와 분리해 두었습니다.
- **데이터**: `data/*Gateway`가 Pleos SDK 콜백을 Kotlin Flow와 suspend 함수로 감쌉니다. 차량 제어 전에는 capability API로 차종이 지원하는 구역을 확인합니다.
- **의존성 주입**: Hilt
- **디자인 시스템**: `core/designsystem`에 의미 기반 색 역할(`ShimpyoColors`, 낮과 밤), 차량용 글자 크기, 넓은 창과 분할 창 레이아웃이 있습니다.

```
app/src/main/java/com/shimpyo/app
├── core/        mvi, designsystem, time
├── data/        vehicle, navi, voice, location, restarea, settings
├── domain/      model, logic (FatigueRules, SpeakGuard, UtteranceParser, CabinOrchestrator)
├── engine/      ShimpyoEngine, EngineContract
├── service/     ShimpyoMonitorService (포그라운드 서비스)
├── ui/          onboarding, main (home, driving, rest), previews
└── widget/      Glance 위젯
app/src/debug/   DemoCommandReceiver (debug 빌드에서 adb로 데모 조작)
```

## 테스트 환경과 필요한 설정

- 개발 환경은 macOS(Apple Silicon)와 Android Studio, JDK 17, Gradle 8.14(wrapper 포함)입니다. compileSdk 36, minSdk 28, targetSdk 34로 빌드합니다.
- **개발에 사용한 에뮬레이터**: Pleos Connect Emulator v2.1.0 (Android 14, API 34, arm64)
- **화면 규격**: 2560×1440 px, 160 dpi. 앱 영역은 1682×1210 dp이고, 1:1 분할 화면에서는 841 dp 폭입니다.
- 에뮬레이터 위치를 경부고속도로 근처로 옮겨야 휴게소 샘플 데이터가 잡힙니다(아래 4번 참고).

## 실행 방법

### 1. 에뮬레이터 준비
1. Android Studio의 Tools, SDK Manager, SDK Update Sites에서 `+`를 눌러 아래 주소를 추가합니다. 이름은 Pleos Connect System Image로 둡니다.
   - Mac: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
   - Windows, Linux: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`
2. SDK Platforms의 API 34에서 **Pleos Connect v2.1.0 APIs**를 설치합니다(Pleos Connect SDK 라이선스 동의 필요). 설치 후 Studio를 다시 시작합니다.
3. Device Manager에서 Automotive 기기 **Pleos Connect v2.1.0**을 만듭니다. 부팅 옵션은 반드시 **Cold boot**로 하고 RAM은 4GB로 둡니다.
4. 음성 인식을 시험하려면 에뮬레이터 Extended Controls의 Microphone에서 호스트 오디오 입력을 허용합니다.

### 2. APK 설치
제출용 release APK는 `apk/` 폴더에 있습니다.
```bash
adb install -r apk/TeamShimpyo_쉼표_release.apk
```
처음 실행하면 주차 상태에서 온보딩이 나오고 위치와 알림 권한, 차량 제어 항목별 동의를 받습니다.

### 3. 소스에서 빌드
Android Studio에서 프로젝트를 열고 Gradle 동기화를 한 뒤, 실행 대상으로 Pleos Connect 에뮬레이터를 고르고 Run을 누릅니다. 터미널에서는 Studio에 들어 있는 JDK를 씁니다.
```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug
```
결과물은 `app/build/outputs/apk/debug/app-debug.apk`입니다.

### 4. Virtual Console로 기어와 위치 바꾸기
```bash
adb forward tcp:8088 tcp:8088
```
브라우저에서 `http://127.0.0.1:8088/`을 엽니다. 도로 화면 오른쪽 Drive status 상자 아래 줄의 P, R, N, D 버튼으로 기어를 바꿉니다. D가 주행, P가 주차입니다. 쉼표의 주행 흐름은 ACCEL 없이 0km/h에서 시험합니다.

에뮬레이터 기본 위치는 미국 마운틴뷰라서 휴게소 샘플(경부고속도로)이 60km 밖으로 잡힙니다. 시연 전에 위치를 옮겨 주세요.
```bash
adb emu geo fix 127.0947 37.4127
```
adb로 설치한 앱은 주행 중 사용 허용 목록에 들지 않지만, 에뮬레이터 v2.1.0에서는 기어 D, 0km/h 상태에서 주행 화면이 막히지 않는 것을 확인했습니다.

### 5. 데모 컨트롤
실제로 1시간 30분을 기다리지 않도록 화면 아래 데모 줄에서 시간 배속(×1, ×60, ×300), 30분 주행 추가, 체크인, 도착, 음성 답변 대신 누르는 버튼("7", "응", "카냅", 컨디션 확인 정답 등)을 쓸 수 있습니다. 마이크 없이도 전체 흐름을 시연할 수 있고, 분할 화면에서는 조작 줄과 답변 줄 두 줄로 접힙니다.

앱 영역 오른쪽 끝의 핸들을 화면 가운데로 끌면 쉼표와 내비가 1:1로 나뉩니다. 내비가 전체 화면일 때 앱을 열면 Pleos 창 정책에 따라 앱도 전체 화면으로 열립니다.
```bash
adb shell input swipe 2547 719 1694 719 700   # 2560x1440 에뮬레이터 기준
```
debug 빌드에서는 같은 조작을 adb 브로드캐스트로도 보낼 수 있습니다. AAOS 사용자는 10번이므로 `--user current`를 붙입니다.
```bash
adb shell am broadcast --user current -n com.shimpyo.app/.demo.DemoCommandReceiver --es cmd answer --es text 7
```

## 디자인

- 낮에는 Pleos 화면과 같은 밝은 회색(#F6F8FC) 바탕에 흰 면을, 밤에는 거의 검은 바탕을 씁니다. 에뮬레이터의 Pleos 화면에서 잰 값이라 내비 옆에 띄워도 어색하지 않습니다.
- 브랜드 색은 현대 블루(#002C5F) 하나이며 주요 버튼과 선택 상태에만 씁니다. 그라데이션이나 빛 번짐 효과는 쓰지 않습니다.
- 화면마다 숫자 하나가 중심입니다. 판단에 쓰는 글자는 32dp, 보조 글자는 24dp 이상이고, 버튼 높이는 104dp(가이드 최소 76dp), 모든 글자 대비는 4.5:1 이상입니다.
- 꾸밈용 문구나 요소는 넣지 않았습니다. 연결 안내도 연결이 끊겼을 때만 나옵니다.
- 휴식 화면만 예외로, 잘 때는 거의 검은 화면이고 깰 때는 Light Sand 색으로 바뀌어 실내 조명의 해돋이 연출과 맞춥니다.
- 창 너비가 1200dp보다 좁으면 세로로 쌓는 레이아웃으로 바뀝니다.
- 참고 자료: [Pleos Connect Design Guide: Layout](https://document.pleos.ai/en/docs/connect/guide/docs-design/getting-started/layout), [Window Policy](https://document.pleos.ai/en/docs/connect/guide/docs-design/getting-started/window), [Android for Cars 주행 UX 원칙](https://docs.partner.android.com/drivingux/foundations/design-principles), [WWDC24: Say hello to the next generation of CarPlay design system](https://developer.apple.com/videos/play/wwdc2024/10112/)

## 에뮬레이터 검증 결과 (Pleos Connect v2.1.0)

- 주행 흐름: 기어 D에서 체크인과 졸림 답변을 거쳐 `requestTrip` 도로 거리로 휴게소를 추천하고, 수락하면 Pleos 내비가 휴게소로 경로를 안내합니다. 도착하면 도착 화면이 나오고, P단으로 바꾸면 휴식 선택으로 넘어갑니다.
- 휴식 흐름: 휴식 선택, 호흡 안내, 실내 연출, 해돋이 기상, 컨디션 확인, 복원까지 오류 없이 동작합니다. 휴식 한 번을 녹화하는 동안 Vehicle SDK 제어 호출 83건이 모두 성공했습니다.
- 차량 영역 ID는 capability API로 조회해서 씁니다. 에뮬레이터는 운전석 단일 영역을 거부합니다.
- `onTripInfo`의 단위는 거리 m, 도착 예정 시간 초, 도착 시 배터리 %입니다.
- 경로 안내 중에는 Pleos가 내비 화면을 앞에 두므로 쉼표는 도착 전까지 음성으로만 진행합니다. 1:1 분할 화면에서는 쉼표가 내비 옆에 계속 보입니다.
- 넓은 창과 분할 창, 한국어와 영어, 낮과 밤 모드에서 모든 화면이 잘리지 않고 동작합니다.

## 남은 과제

코드에 `TODO(...)` 주석으로 표시해 두었습니다.

- `TODO(policy)`: 차량 제어 권한의 승인 범위, 백그라운드 실행과 자동 시작 정책, 주행 규제(CarUxRestrictions) 연동
- `TODO(spec)`: 안내 중 경유지 추가(좌표만으로 경로 요청은 확인), 오디오 존, 차속, 위젯 호스팅, 위치 권한 설명 문구, 안내 취소 시 처리
- `TODO(data)`: 휴게소와 졸음쉼터 공공데이터 연동(현재 경부고속도로 샘플)
- `TODO(safety)`: 지하나 밀폐된 주차장 감지와 파워트레인별 공회전 휴식 차단
- `TODO(release)`: 앱마켓 배포용 서명 키
