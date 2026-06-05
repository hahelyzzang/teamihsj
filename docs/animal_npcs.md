# RescueCraft – 대화형 동물 NPC (cow / chicken / rabbit / horse)

기존 돼지(Bori, `/rcpig`) 시스템은 그대로 두고, 맵에 추가된 다른 동물들을
대화 가능한 구조(救助, rescue) NPC로 만드는 범용 프레임워크입니다.

## 맵에 있는 동물 (확인됨)
월드 템플릿의 entities region 데이터를 분석한 결과:
`pig, cow, chicken, rabbit, axolotl, turtle, cat, horse` + 마을 주민(villager).

이 중 "농작물을 먹는" 대표 동물 4종을 대화형 NPC로 구현했습니다.
(axolotl / turtle / cat 은 물고기·해초 등 "재배"와 어울리지 않아 제외 — 필요 시
`AnimalSpecies` enum에 항목만 추가하면 손쉽게 확장 가능합니다.)

## 상자(chest) 확인
월드 block region 데이터에 `minecraft:chest` 블록이 10개 존재하며,
주변에 farmland·composter·밀/감자/당근/비트 작물이 심어져 있습니다.
플레이어는 이 상자에서 동물이 요구하는 아이템을 찾아 가져옵니다.

## 동물별 요구 아이템 / 특성 (`AnimalSpecies.java`)

| 동물    | 기본 이름 | 요구 아이템   | 마리당 필요 | 특성                       |
|--------|----------|--------------|-----------|---------------------------|
| COW    | Daisy    | 밀(wheat)     | 2         | 신선한 밀을 그리워하는 젖소     |
| CHICKEN| Coco     | 밀 씨앗(seeds)| 1         | 씨앗을 쪼아먹는 작고 겁많은 닭   |
| RABBIT | Mochi    | 당근(carrot)  | 1         | 아삭한 당근을 갉아먹는 토끼     |
| HORSE  | Comet    | 사과(apple)   | 3         | 달콤한 사과로 위로받는 말       |

> 돼지 Bori는 기존대로 당근/감자/비트 + 물 + 축사(habitat) 흐름(`/rcpig`)을 사용합니다.

## 플레이 흐름

1. `/rcanimal adopt` — 6블록 이내 가장 가까운 지원 동물과 친구가 됩니다.
   이름이 붙고, 굶주린 동물이 자기소개를 하며 자기 먹이를 달라고 합니다(LLM 대화).
2. `/rcanimal talk <message>` — 동물과 영어로 대화합니다.
3. `/rcanimal give` — 인벤토리에서 요구 아이템 1개를 줍니다.
   - **처음 줄 때**: 동물이 그 아이템을 **재배/구하는 방법**을 알려주고,
     주변 같은 종 동물 수(무리 크기)를 세어
     `무리 수 × 마리당 필요 = 총 필요량` 을 알려줍니다.
   - 이후: 진행도(`X / 총량, 남은 수`)를 알려줍니다.
   - 총량을 채우면: 무리 구조 완료(trust → COMPANION).
4. `/rcanimal status` — 신뢰 단계 / 요구 아이템 / 무리 필요량·진행도 확인.

머리 위 말풍선(`HungryAnimalParticles`)은 무리에게 줄 먹이가 충분해질 때까지
음식 아이콘을 표시합니다.

## 영어 실력 연동
기존 CEFR 평가(`OllamaEnglishEvaluator` → `PlayerEnglishProfile`)와
밴드 구분(`PigPrompt.currentBand()`: BEGINNER/INTERMEDIATE/ADVANCED)을 그대로 재사용합니다.
- BEGINNER: 매우 짧고 쉬운 영어 + 항상 한국어 번역 동반
- INTERMEDIATE: 자연스러운 영어, 헷갈려할 때만 한국어 힌트
- ADVANCED: 유창한 영어, 도움을 요청할 때만 한국어
재배 방법/무리 안내도 ADVANCED가 아니면 한국어를 함께 표시합니다.

## 대화 백엔드
`AnimalDialogueService` 가 로컬 Ollama(`localhost:11434`)를 호출합니다.
모델은 `RESCUECRAFT_ANIMAL_MODEL`(없으면 `RESCUECRAFT_PIG_MODEL`, 기본 `qwen2.5:3b-instruct`)
환경변수로 지정합니다. 외부 유료 API는 사용하지 않습니다.

## 새 동물 추가 방법
`AnimalSpecies` enum 에 항목 하나만 추가하면 됩니다:
`EntityType`, 기본 이름, 요구 `Item`, 표시 이름, 마리당 필요 개수, 성격 설명,
재배 팁(영/한)을 채우면 자동으로 adopt/talk/give/status 및 말풍선이 동작합니다.
