# 차량 신호 테스트 방법 (Virtual Console)

에뮬레이터에서 차량 신호(기어, 문, 열선, 온도)를 바꿔 보면서 앱이 잘 받는지 확인하는 방법이에요.
담당: A (#34)

## 1. Virtual Console 열기

에뮬레이터를 켠 다음, 안드로이드 스튜디오 아래 Terminal(Alt+F12)에서:

    adb forward tcp:8088 tcp:8088

adb를 못 찾는다고 나오면 SDK 경로로 실행해요. (경로는 프로젝트의 local.properties → sdk.dir 에 있어요)

    & "C:\Android\Sdk\platform-tools\adb.exe" forward tcp:8088 tcp:8088

8088이 나오면 성공. 크롬에서 http://127.0.0.1:8088/ 을 열어요.

> 에뮬레이터를 껐다 켜면 adb forward를 다시 해야 해요.

## 2. 신호별로 바꾸는 곳

| 신호 (VehicleSignals.Listener) | Virtual Console에서 바꾸는 곳 | 앱이 받는 값 |
| --- | --- | --- |
| P단 onParkChanged | 오른쪽 위 기어 P R N D | P = true, 그 외 = false |
| 주행 제한 onDrivingRestrictionChanged | 오른쪽 위 기어 P R N D | P = false, 그 외 = true |
| 조수석 문 onPassengerDoorChanged | BODY → DOORS → FR door | 열림 = true, 닫힘 = false |
| 조수석 열선 onPassengerHeatChanged | CLIMATE → PASSENGER → V3~Off~H3 | V3=-3 … Off=0 … H3=3 |
| 조수석 온도 onPassengerTemperatureChanged | CLIMATE → PASSENGER → − / + | ℃ 숫자 (예: 22.0) |

- 조수석 = FR(앞 오른쪽) / PASSENGER / ROW_1_RIGHT. 운전석(FL, DRIVER)을 바꾸면 반응하지 않는 게 정상이에요.
- CLIMATE의 Sync는 꺼 두세요. 켜면 운전석·조수석이 같이 바뀌어요.
- 열선·온도는 왼쪽 위 Emulation Controls → 검색 seat → Seat Temperature(Passenger 선택)에서도 바꿀 수 있어요.

## 3. 로그로 확인하기

Logcat(고양이 아이콘)에서 tag:MajiSignal 로 검색하면 받은 신호가 보여요.

    기어 신호: GEAR_DRIVE
    → onParkChanged(false), onDrivingRestrictionChanged(true)
    조수석 문 신호: 1
    → onPassengerDoorChanged(true)
    조수석 열선 신호(원래 값): 2
    → onPassengerHeatChanged(2)  (열선=2, 통풍=0)
    → onPassengerTemperatureChanged(23.0)

## 4. 확인된 것 / 안 되는 것

| 항목 | 결과 |
| --- | --- |
| 기어 P↔D | 됨 |
| 조수석 문 열림/닫힘 | 됨 (운전석 문은 무시) |
| 조수석 열선·통풍 | 됨 (-3..3, 음수=통풍) |
| 조수석 온도 | 됨. 처음 값도 오고, 바꾸면 따라와요 |
| 주행 중 앱 가려짐 | 안 가려짐. 매니페스트가 distractionOptimized라서 D단에서도 앱이 보여요. 버튼은 onDrivingRestrictionChanged로 직접 막아야 해요 |
| 주차 브레이크 | Pleos SDK 2.3.2에 없음 (Brake에는 페달만). 팀 결정으로 제외 |
| 착석(탑승 감지) | SDK에는 있지만(Seat.registerSeatOccupancy) 에뮬에서 값이 반대로 와요 (Occupied → false). 팀 결정으로 쓰지 않고 탑승·하차는 문 기준 |

## 5. 되는 기능 (capabilities) 에뮬레이터 결과

signals.capabilities()는 start() 때 아래 함수로 미리 확인해요. "된다" = 지원 영역이 있고 + 제어 권한(WRITE/READ_WRITE)이 있음.

| 기능 | B가 움직일 때 쓰는 함수 | A가 확인하는 함수 | 권한 | 범위 |
| --- | --- | --- | --- | --- |
| 시트 이동 | Seat.moveSeatForeAft | checkMoveSeatForeAftCapabilities | READ_WRITE | - |
| 열선 | Seat.setHvacSeatTemperature | checkSeatTemperatureCapabilities | READ_WRITE | 앞좌석 -3..3, 뒷좌석 0..3 (추정) |
| 온도 | HVAC.setHvacTemperature | checkHvacTemperatureCapabilities | READ_WRITE | 15.0..30.0℃ |
| 창문 | Window.setWindowPosition | checkWindowPositionCapabilities | READ_WRITE | 0..10 |
| 무드등 | Light.setAmbientLightBrightnessColor | checkAmbientLightBrightnessColorCapabilities | READ_WRITE | - |

→ 에뮬레이터에서는 5개 모두 true. Logcat tag:MajiSignal 의 [기능] 줄로 다시 확인할 수 있어요.

## 6. 앱 이벤트는 바뀔 때만 와요

PleosVehicleSignals는 같은 값이 반복되면 다시 알리지 않아요. 예를 들어 D단 주행 중 1단→2단으로 바뀌어도 onParkChanged(false)는 한 번만 와요.
앱이 직접 바꾼 값(예: B가 열선을 켬)도 신호로 와요. 탑승자가 바꾼 것과의 구분은 받는 쪽(C, D)에서 해요.