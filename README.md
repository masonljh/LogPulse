# LogPulse

LogPulse는 Android 로그를 효율적으로 분석하고 시각화하기 위한 고성능 데스크톱 애플리케이션입니다. Kotlin Multiplatform(KMP)과 Compose Multiplatform을 기반으로 구축되었습니다.

## 주요 기능

- **강력한 로그 파싱**: Android Logcat 형식을 지원하며, 대용량 로그 파일(15만 줄 이상)도 매끄럽게 처리합니다.
- **실시간 필터링**: 로그 레벨(Verbose, Debug, Info, Warn, Error) 및 텍스트 기반 필터링 기능을 제공합니다.
- **Flow Dashboard**: 정의된 패턴에 따라 로그 시퀀스를 추적하고 진행 상황을 대시보드 형태로 시각화합니다.
- **패턴 매칭**: 정규표현식을 사용하여 복잡한 로그 흐름을 감지하고 알림을 표시합니다.
- **인터랙티브 UI**: 미니맵을 통한 빠른 탐색, 로그 상세 정보 보기, 점프 탐색(Jump to Log) 기능을 지원합니다.

## 프로젝트 구조

- `shared`: 비즈니스 로직, 로그 파서, 데이터 모델 및 UseCase를 포함하는 공통 모듈입니다.
- `composeApp`: Compose Multiplatform을 사용한 데스크톱(JVM) UI 모듈입니다.

## 구동 방법

### 요구 사항

- JDK 17 이상

### 실행 방법

터미널에서 아래 명령어를 실행하여 데스크톱 애플리케이션을 구동할 수 있습니다.

```bash
./gradlew :composeApp:desktopRun
```

## 라이선스 (License)

이 프로젝트는 **Apache License 2.0**을 따릅니다.

```text
Copyright 2026 masonljh

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

상세한 라이선스 내용은 프로젝트 루트의 [LICENSE](./LICENSE) 파일을 참조하세요.
