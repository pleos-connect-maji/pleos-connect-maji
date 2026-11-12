# NaviHelper SDK 조사 (#27 맞이 장소 내비 목적지)

> 상태: **조사 중간 결과** · 담당: C · 관련: #27
> 문서 본문을 직접 열지 못해서, 검색 결과의 요약만 확인했어요. 아래 "확인 필요"를 먼저 해야 해요.

## 1. 지금 프로젝트에 있는 것

- `AndroidManifest.xml`에 `pleos.car.permission.NAVI_ROUTE` 권한이 이미 있어요(#8).
- 프로필의 `place` 값(최근 맞이 장소)은 이미 저장돼요.
- NaviHelper SDK 의존성은 아직 없어요. 공통 빌드 파일을 바꾸는 일이라 팀 확인이 필요해요.

## 2. 조사로 확인한 것 (검색 결과 기준)

공식 문서 페이지:
- [NaviHelper 소개(영문)](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/NaviHelper/intro)
- [목적지 설정](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/NaviHelper/destinations)
- [경로](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/NaviHelper/routes)
- [경유지 설정](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/NaviHelper/waypoint-settings)
- [첫 API 호출](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/NaviHelper/make-the-first-api-call)

검색 결과에 나온 내용:
- **목적지 설정 API**: `requestRoute()`로 목적지를 설정하고, 다시 부르면 목적지가 바뀝니다.
- **확인 API**: `getDestinationInfo()`는 값을 바로 돌려주지 않고, 결과를 `onDestinationInfo` 콜백으로 줍니다.
- **취소 API**: `cancelRoute()`로 목적지를 취소합니다.
- **결과 확인**: `requestRoute()`가 성공했다고 경로 계산이 끝난 것은 아닙니다. `onRouteStarted`, `onRoutingCompleted`, `onDestinationChanged`, `onError` 콜백으로 최종 결과를 확인하라고 안내합니다.
- **리스너 경로**: `ai.pleos.playground.navi.helper.listener.NaviHelperEventListener`
- **개발 환경**: 개발은 Pleos Connect 에뮬레이터에서 합니다.
- **패키지 이름 변경**: 새 이름 체계는 이전 버전과 호환되지 않아서, 빌드 파일과 import 문을 바꿔야 한다는 안내가 있습니다.

## 3. 확인 필요 (아직 모름)

1. **버전**: 이슈(#27)에는 `ai.pleos.playground:NaviHelper:3.0.5`로 적혀 있는데, 검색에 나온 설정 페이지는 `2.0.3`을 보여 줬어요. **어느 버전이 맞는지**, 그리고 3.0.5의 메서드 이름이 위 문서와 같은지 확인해야 해요.
2. **본문 내용**: 설정 방법(Maven 주소, 의존성 한 줄, 권한 목록)과 목적지 API의 인자 형식은 페이지를 직접 열어야 확인할 수 있어요. 현재 자동 열기로는 빈 페이지가 나와요.
3. **주행 중 제한**: 주행 중에 목적지를 설정할 수 있는지, 제한이 있는지 확인이 필요해요. 맞이 중 주행 제한(화면 버튼 막기)과 맞물려요.
4. **에뮬레이터 동작**: 에뮬레이터에서 경로 안내가 실제로 되는지 확인이 필요해요.

## 4. 맞이 흐름과 연결하는 방법 (제안)

- 맞이를 시작해서 준비 단계가 될 때, 프로필의 `place` 값을 목적지로 설정해요.
- 목적지 설정 결과(`onError` 포함)는 맞이 진행 화면에 한 줄로 알려 줘요.
- 주행 중에 목적지를 바꿔야 하면, 주행 제한과 충돌하는지 먼저 확인해요.
- 흐름(`MajiFlowImpl`)에 내비 호출을 넣을지, 화면에서 따로 호출할지 정해야 해요. 흐름 쪽에 넣으면 흐름 테스트에 가짜 내비 인터페이스가 필요해요.

## 5. 다음 단계

1. 페이지 본문을 직접 확인해서 **버전과 설정 방법**을 확정합니다. 사용자님이 Pleos 문서 페이지를 직접 열어 주시면 내용을 정리할 수 있어요.
2. 확정되면 빌드 파일 변경을 팀과 상의해서 의존성을 추가합니다.
3. 가짜 내비 인터페이스로 흐름과 화면 연결을 먼저 만들고, SDK는 나중에 붙입니다.
