# RescueCraft – 대화형 동물 NPC (cow / chicken / rabbit / horse / axolotl / turtle / cat)

기존 돼지(Bori, `/rcpig`) 시스템은 그대로 두고, 맵에 추가된 다른 동물들을
대화 가능한 구조(rescue) NPC로 만드는 범용 프레임워크입니다.

## 맵에 있는 동물 (확인됨)
월드 템플릿 entities 데이터 분석 결과:
`pig, cow, chicken, rabbit, axolotl, turtle, cat, horse` + 마을 주민(villager).
이 중 대표 8종(pig 포함)을 하나의 시스템으로 통합해 대화형 NPC로 구현했습니다.
(pig도 이제 `/rcanimal`·우클릭으로 동작하며, `/rcpig`는 레거시로 남아 있습니다.)

## 상자(chest)
월드 block region 데이터에 `minecraft:chest` 10개 + farmland·composter·
밀/감자/당근/비트 작물 확인. 플레이어는 상자에서 씨앗·작물·양동이를 찾습니다.

## 상호작용 방식 (명령어 없이)
- **우클릭**: 동물을 우클릭하면 친구 맺기 → 이후 손에 든 것에 따라
  먹이 주기 / 물 주기(양동이) 자동 처리.
- **근처 채팅**: 친구가 된(이름 붙은) 동물 옆에서 그냥 채팅을 치면
  그 동물에게 말 거는 talk로 전달됨. `!`로 시작하면 일반 채팅.
- 내부적으로 클라이언트(`RescueCraftClient`)가 우클릭/채팅을 기존
  `/rcanimal`·`/rcpig` 명령으로 변환해 보내므로, 서버 로직 중복이 없고
  명령어는 안내책(가이드북)의 백업 수단으로 그대로 남습니다.

## 동물별 요구 / 특성 (`AnimalSpecies.java`)

### 육상 동물 — 물 + 여러 작물
| 동물 | 이름 | 요구 먹이 | 마리당 | 물 |
|---|---|---|---|---|
| PIG | Bori | 당근 / 감자 / 비트 | 2 | 필요 |
| COW | Daisy | 밀 / 비트 | 2 | 필요 |
| CHICKEN | Coco | 밀 씨앗 / 비트 씨앗 | 1 | 필요 |
| RABBIT | Mochi | 당근 / 민들레 | 1 | 필요 |
| HORSE | Comet | 사과 / 밀 / 설탕 | 3 | 필요 |

### 수중 동물 & 고양이 — 낚시로 잡은 물고기
| 동물 | 이름 | 요구 먹이 | 마리당 | 물 |
|---|---|---|---|---|
| AXOLOTL | Bubbles | 물고기(대구·연어·열대어·복어) | 2 | 불필요 |
| TURTLE | Shelly | 해초 / 물고기 | 1 | 불필요 |
| CAT | Whiskers | 물고기(대구·연어) | 2 | 불필요 |

> 물은 무리 공용 1회(양동이 한 번)로 처리. 먹이는 무리 마리 수만큼 모아야 함.

## 플레이 흐름
1. 동물 우클릭 → 친구 맺기. 굶주린 동물이 자기소개+먹이 요청(LLM).
2. 옆에서 채팅 → 영어로 자유 대화.
3. 손에 먹이를 들고 우클릭 → 1개 줌.
   - **처음 줄 때**: 그 먹이를 **재배/낚시하는 방법**(영/한)을 알려주고,
     주변 같은 종 마리 수를 세어 `무리 수 × 마리당 = 총 필요량` 공지.
   - 이후: 진행도(`X / 총량`, 남은 수) 안내.
4. 육상 동물은 물 양동이를 들고 우클릭 → 무리 물 확보(1회).
5. 먹이 총량 + (육상은)물까지 채우면 무리 구조 완료(trust → COMPANION).

머리 위 말풍선(`HungryAnimalParticles`): 먹이+물 모두 필요→종이,
먹이만→막대, 물만→실, 충분하면 사라짐.

## 영어 실력 연동
기존 CEFR 평가(`OllamaEnglishEvaluator` → `PlayerEnglishProfile`)와
밴드(`PigPrompt.currentBand()`: BEGINNER/INTERMEDIATE/ADVANCED)를 재사용.
- BEGINNER: 매우 쉬운 영어 + 항상 한국어 번역
- INTERMEDIATE: 자연스러운 영어, 헷갈릴 때만 한국어 힌트
- ADVANCED: 유창한 영어, 요청 시에만 한국어
재배/낚시 방법·무리 안내도 ADVANCED가 아니면 한국어를 함께 표시.

## 대화 백엔드
`AnimalDialogueService` → 로컬 Ollama(`localhost:11434`).
모델: `RESCUECRAFT_ANIMAL_MODEL`(없으면 `RESCUECRAFT_PIG_MODEL`,
기본 `qwen2.5:3b-instruct`). 외부 유료 API 미사용.

## 새 동물 추가 방법
`AnimalSpecies` enum에 항목 하나(EntityType, 이름, 물필요 여부,
허용 먹이 List, 표시명, 마리당 개수, 성격, 재배/낚시 팁 영·한)만 추가하면
우클릭·채팅·먹이/물·말풍선이 모두 자동 동작합니다.

## 빌드 참고
이 프로젝트는 **Java 25 / Minecraft 26.1.2** 대상입니다. (개발 샌드박스에
JDK 21만 있으면 gradle 컴파일이 불가하니, JDK 25 환경에서 `./gradlew build`로
최종 확인하세요.)

## 업데이트: 3일 돌봄 → 해방 / 종별 1마리 / 빠른 재배

- **종별 대표 1마리만 입양**: 같은 종의 다른 개체를 우클릭해도 새 친구가 되지
  않고 기존 대표를 돕도록 안내합니다(실수로 돼지 여러 마리가 모두 Bori 되는 문제 해결).
  `AnimalCompanion.getBySpecies()`로 종 중복을 막습니다.
- **3일 돌봄 후 해방**: 무리에게 먹이+물을 모두 주면 회복(care)이 시작되고,
  **3일(게임 내, 잠자기로 단축 가능)** 이 지나면 동물이 다음 대사를 합니다 —
  "Thank you for saving us, we are all well now. Please, bring down the iron bars
  and help us be free. We will restore the wildlife." (영/한 동시 출력)
  타이머는 `getGameTime()`(총 게임 틱) 기준이라 실제 플레이 3일(약 60분)이 지나면
  진행됩니다. `AnimalCompanion.CARE_TICKS` 로 길이를 조절할 수 있어요.
- **재배 시간 ~5분**: `CropGrowthAccelerator`가 플레이어 주변 작물
  (밀/감자/당근/비트, `CropBlock`)을 매초 확률적으로 한 단계씩 키워 약 5분 만에
  다 자라게 합니다. `TARGET_SECONDS` 상수로 조절 가능.
- **재배 설명 강화**: 각 동물의 `tipEnglish/tipKorean`을 단계별(괭이 제작 →
  경작지 → 심기 → 성장/뼛가루 → 수확/재심기)로 더 자세히 보강.
