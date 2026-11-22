# Отчёт: ТЗ и механики спавна — `ancient_civilization` (Minecraft 1.21.1, NeoForge)

> Документ-спецификация: **изначальное ТЗ, все начальные требования, присланная
> версия `spawn.json`, механики спавна после итоговых правок**.
> Это **не отчёт по работе над модом** — описание желаемого исходного состояния.
>
> **Статус:** по указанию пользователя вся проделанная работа считается **не
> выполненной**. Этот документ — единый источник истины по ТЗ и механикам;
> любые изменения проверяются заново сборкой и игрой.

---

## Лора и принципы мода — объяснение пользователя

> Раздел добавлен по требованию пользователя: **объяснение лора, принципы**
> работы мода «как он задуман». Описание желаемого исходного состояния, не
> отчёт о реализации.

### Лора

- В мире существовала **древняя цивилизация**, чьи воины — **легионы** — до
  сих пор пробуждаются и охраняют свои руины. Все нежить, спавняющаяся под
  модом, — это **воины этой цивилизации**, а не обычные зомби/скелеты.
- Воины сгруппированы в **отряды (легионы)** с кастовой структурой. Названия
  легионов в конфиге: **Raiders of the plains** (отряд равнин), **Warriors of
  the desert** (воины пустыни), **Legion of the drowned** (легион утопленников),
  **Watch of the ancient city** (стража Древнего города).
- **Касты (роли):** горожанин (peasant) — простой житель/ополченец; лучник
  (archer) — дальний стрелок; солдат (soldier) — тяжёлая пехота; герой (hero) —
  старшина/вождь, носит полный сет и лучшее оружие. Илладжеры, криперы и прочие
  мобы — вне механики (не воины цивилизации).
- Отряды **выкапываются из земли** (анимация спавна — выкапывание), держат
  связь «держаться лидера», чистятся при отсутствии игроков в радиусе; земные
  легионы **маршируют к деревне** (деревня при этом не бустит).
- **Лорные детали снаряжения:**
  - Утопленники — **рыбаки-стражи затонувших поселений**: трезубец в столе
    горожанина (250‰) — рыбацкий инструмент-оружие;
  - Воины пустыни — золотое снаряжение (золото пустыни); отряд равнин —
    железное снаряжение;
  - Стража Древнего города — броня из эха (echo) и сифита (siftite),
    варден — их старшина, приоритетный противник;
  - Заблудшие странники — одиночные воины, застрявшие у руин порталов.

### Принципы

1. **Сепарация статики и логики.** Шанс/веса оружия и брони — в **InControl**
   (`spawn.json`, правка без пересборки, `/incontrol reload`); логика и состояние
   (отряды, странники, HP-бафф, ремап брони, стрелковый AI, марш) — в **моде**.
2. **Касты — основа снаряжения.** Роль определяет экипировку: горожанин —
   лёгкое/простое оружие без брони; солдат — тяжёлый сет по частям; герой —
   полный heavy-сет и лучшее оружие; лучник — лук/арбалет. **Вариантные мобы
   (V&V) — ×0.5 к базовым шансам каст.**
3. **Легионы, не разрозненные мобы.** Отряды — сплочённые formations с
   лидером, таймером, анимацией выкапывания и сбросом; не просто спавн мобов.
4. **Климат-зависимая броня (F3).** `heavy_iron` ремапится под климат:
   холод — железо, жар — золото, медь — окисляется по влажности биома.
5. **Топ-tier только по сложности.** Незерит/диамант/эхо/сифтит (и SS-незерит) —
   только при regional difficulty ≥ порога (`mindifficulty` 3.0, §4.1); дроп
   разрешён. **heavy-сет незерит/эхо/сифтит никогда не дропается** — надевается
   только отрядом Древнего города.
6. **Герои и варден.** Герои **не дропают** броню и оружие; варден —
   приоритетный противник для стражи Древнего города (равно приоритету игрока,
   ближайший выигрывает).
7. **Не трогаются.** Илладжеры, криперы, варден, боссы — вне механики мода.
8. **Независимость от optional dependencies.** Отсутствующие модные ID
   предметов/мобов/структур пропускаются со warn — мод работает без кастома,
   только neoforge + minecraft required.
9. **Без миксинов.** Стрелковый AI — самостоятельный goal (`AncientRangedGoal`),
   миксины исключены (см. §1.1).
10. **Трансформация исключена (F2).** Скелеты как ближние воины в отрядах —
    просто другой entity в слоте, **без конвертации/трансформации сущностей**.

---

## 1. Изначальное ТЗ (technical specification)

Мод **`ancient_civilization`** (`com.ancientcivilization`) для Minecraft
**1.21.1 / NeoForge**: переработка спавна вражеской нежити, не добавляющая ресурсный
пакет — всё задаётся конфигом и Java-логикой. В задаче (F1–F10):

- **F1 — ядро.** Центр на `EntityJoinLevelEvent` (NORMAL, после InControl):
  контекст биома/структуры, конфиг JSON (Gson, читается без пересборки).
- **F2 — вооружение скелета.** Вооружать ближних бойцов **вооружением скелета**
  по оригинальному конфигу, с **приоритетом двуручному оружию**, шансы на оружие
  как в оригинале; **скелеты как ближние мобы** (с теми же шансами на оружие и
  броню, что у зомби) могут появляться **вместо зомби в составе отрядов**.
- **F3 — ремап брони.** По температуре биома ремап `heavy_iron` →
  copper/iron/golden + окисление меди по влажности.
- **F4 — HP-бафф.** +10% maxHP нежити (включая отряды), через `AttributeModifier`.
- **F5 — стрелковый AI.** Без миксинов: самостоятельный goal (зарядка + выстрел).
- **F6 — отряды.** Событиеный движок: таймер → спавн → анимация выкапывания → сброс.
- **F7 — варден.** Приоритет targeting для отряда Древнего города.
- **F8 — заблудшие странники.** На руинах порталов: один ролл на структуру, SavedData.
- **F9 — буст рядом со структурой.** Таймер + тег.
- **F10 — марш.** Отряд движется к деревне.

**Статика (каст, столы оружия/брони)** задана **InControl** в `spawn.json` —
это авторитетный источник для шансов оружия и брони. **Логика/состояние** — в
моде.

### 1.1 Технические ограничения (из ТЗ)
- **Нельзя** использовать миксины soft-interface (`@Implements`) — удалить, не
  чинить. Оба миксин-файла удалены; ranged AI заменён на самостоятельный goal.
- `goalSelector`/`targetSelector` — **protected**: добавлять goals **только через
  рефлексию**, никогда напрямую.
- Изменения **на `main`** (не на отдельном branch), commit должен быть виден на
  `main`.
- Разрешаются только `neoforge` + `minecraft`; остальное — **optional deps**
  (`incontrol`, `simplyswords`, `irons_artifice`, `the_sift`, `create_deep_dark`,
  `variantsandventures`, `luckysarmory`, `armory_bridge`, `chained_leather_armor`).
  Модовые ID предметов/мобов разрешаются только при наличии соответствующего мода.

---

## 2. Все начальные требования пользователя

**Классические требования:**
1. **HP-бафф 10%** для нежити, включая отряды. Применяется **только в 64 блоках
   от деревни** — не boost-all.
2. **`mindifficulty` 3.0**; reroll-схема включена (см. §4.2).
3. **Отряды:** странники (wanderers) soldier/hero с вероятностью; RuinBuff —
   **только `countMultiplier`** (численность, cap 32); **герой не дропает** ничего.
4. **Удалить миксины** (`@Implements` soft-interface) — не чинить.
5. **Goals — protected** → рефлексия (`MobGoals`), без прямого обращения.
6. **Удалить оба миксин-файла**, не чинить.
7. **Не создавать отдельный branch** — работа на `main`.

**Ф2 — окончательная корректка (основная, ядро спавна):**
8. **Удалить логика конвертации** для соседних мобов в скелетов **совершенно**
   (не entity-replacement/transformation; не «скелет → зомби» и не обратно).
   Трансформация сущностей исключена.
9. Ближнего бойца вооружать **вооружением скелета**, как в **оригинальном
   конфиге**, с **приоритетом двуручному оружию**.
10. **Шансы на оружие** должны **точно соответствовать оригинальному конфигу**.
11. Скелеты как **ближние мобы** (те же шансы на оружие и броню, что у зомби)
    могут spawnиться **вместо зомби в составе отрядов**.

**Требования по тестированию и исправлению:**
12. **Команды тестирования:** команды для вызова **всех типов отрядов** и
    **всех типов мобов**, которые может породить мод.
13. **7 багов из игре** (все требуют исправления, см. §5):
    - мобы, вызываемые `spawn mob`, появляются **без брони и оружия**;
    - скелеты с арбалетами **заряжают, но не стреляют**;
    - утопленники (уплотнители) **не стреляют из лука**;
    - пустынные отряды спавнятся **без скелетов / без дальних юнитов**;
    - отряды древнего города **не имеют защиты**;
    - алмазные и выше доспехи **никогда не появляются**;
    - нужна **команда для тестирования портальных странников**.

---

## 3. Присланная в первом сообщении версия `spawn.json`

**`config/incontrol/spawn.json`** — конфиг **InControl**, fourni в первом сообщении,
**41 правило**. Авторитетный источник шансов оружия/брони. Все правила:
`"when": "finalize"` (экипировка на финализации спавна), `"result": "allow"`,
`"continue": true`. Формат — массив объектов: `mob` (целевые ID), `random`
(вероятность), `helditem` / `armor*` / `nbt`.

**Ключевые правила:**

- **Правило 12 (кредо F2 — стол оружия скелета):**
  целевые — `minecraft:skeleton`, `minecraft:stray`, `minecraft:bogged`,
  `variantsandventures:verdant`, `variantsandventures:murk`,
  `variantsandventures:parched`. `helditem`:
  `500=minecraft:bow`, `30=minecraft:crossbow`, `30=irons_artifice:musket`,
  и по `1` (единичный вес) каждое из ближних SS:
  claymore, cutlass, glaive, greataxe, greathammer, katana, rapier, scythe
  (iron + gold, 16 штук). **Без `random` — вооружает всегда** (лук/арбалет/мукет
  исключены из ближнего ролла).
- **Правило 1** — zombie/husk: swords/axes (wooden/stone/iron, вес 50–80) +
  все SS-ближние (вес 30). `random: 0.1`.
- **Правило 2** — drowned: trident 250 + swords/axes + SS-ближние. `random: 0.1`.
- **Правило 3** — gelid/thicket: аналог правила 1. `random: 0.05`.
- **Правила 4–6** — zombie/husk/drowned: полный set `luckysarmory:heavy_iron_*`
  по частям (helmet+chest 0.005; helmet+legs 0.004; helmet+boots 0.004) + SS-ближние.
- **Правила 7–9** — gelid/thicket: heavy_iron по частям (0.0025/0.002/0.002).
- **Правило 10** — zombie/husk/drowned: full heavy_iron set + SS-ближние (вес 50).
  `random: 0.003`.
- **Правило 11** — gelid/thicket: full heavy_iron set + SS (вес 50). `random: 0.0015`.
- **Правила 13–14** — skeleton/stray/bogged: full `chainedleather` и full
  `studdedleather` (chained_leather_armor). `random: 0.03`.
- **Правила 15–18** — skeleton/stray/bogged: armor по слотам (helmet 0.05, chest
  0.045, legs 0.04, boots 0.04) с весовым столом:
  leather 370, **golden 487**, chainmail 129, iron 13, **diamond 1**,
  chainedleather 10, studdedleather 10.
- **Правила 19–22** — verdant/murk/parched: full chainedleather и studdedleather
  (0.015); armor по слотам (0.025/0.0225/0.02/0.02) с тем же столом.
- **Правила 23–26** — piglin: crossbow 100, golden sword/axe 100, gold SS 10;
  zombified_piglin: golden sword 100, gold SS 10.
- **Правила 27–33** — piglin/zombified: heavy_golden set по частям
  (helmet+chest 0.008; helmet+legs 0.006; helmet+boots 0.006; три комбинации
  3 частей 0.003; full set 0.001).
- **Правила 34–35** — piglin/zombified: full chainedleather и studdedleather (0.03).
- **Правила 36–39** — piglin/zombified: armor по слотам (0.05/0.045/0.04/0.04) с
  столом: golden 600, leather 200, chainmail 100, chainedleather 50, studdedleather 50.
- **Правило 40** — zombie/husk/drowned/wither_skeleton/gelid/thicket: NBT
  `HandItems` — два iron cutlass. `random: 0.01`.
- **Правило 41** — piglin/zombified: NBT `HandItems` — два golden sword. `random: 0.01`.

**Важные выводы из spawn.json:**
- Двуручное оружие (claymore, glaive, greataxe, greathammer, scythe) и одноручное
  (cutlass, katana, rapier) присутствуют в ближнем столе скелета единичным весом.
- Лук/арбалет/мукет — **только дальний стол скелета**.
- Алмазный доспех в столах брони — **вес 1** (ванильные minecraft:diamond_*),
  т.е. достижим, но редко.

---

## 4. Механики спавна после итоговых правок

Спецификация спавна, как она описана в **`config/ancient_civilization.json`**
после итоговых правок (это механики, которые должны работать; подтвердить в игре).

### 4.1 Локальная сложность и топ-tier — `localDifficulty`
- Уровни: `normal` (threshold **3.0**, enabled), `hard` (threshold 2.5, enabled),
  `easy` (threshold 3.5, disabled).
- **`topTier`** — спавн высших tier по эффективной сложности:
  `chancePerSlot = 0.004`, `chanceWeapon = 0.004`.
  - Armor (вес): `netherite 500`, `diamond 200`, `create_deep_dark:echo_* 200`,
    `the_sift:siftite_* 100` — по 4 слота (helmet/chestplate/leggings/boots).
  - Weapon: `netherite_sword 60`, `netherite_axe 60`, SS-netherite (chakram,
    claymore, cutlass, glaive, greataxe, greathammer, katana, rapier, sai, scythe)
    — 10.
- **Механика:** алмазные и выше доспехи/оружие спавнятся при достижении порога
  сложности с шансом 0.004 на слот (diamond 200/500 от netherite).

### 4.2 Отряды — `squads` (F6, F2, F10)
Четыре типа. Всего 5 ролей: **hero / soldier / archer / peasant**. Timer:
base 5400 ticks (ancient_city 2700), boosted 1800, global cooldown 600,
`surfaceOnly: true` (ancient_city — underground).

- **`plains`** (биомы: plains, sunflower_plains, meadow, flower_plains,
  windswept_hills, savanna; структуры: ruined_portal, nova_structures:*,
  terralith:fortified_village):
  - hero — zombie, **iron katana**, full heavy_iron armor.
  - soldier ×2 — zombie, **iron glaive** и **iron claymore**, heavy_iron по частям.
  - archer — skeleton, **bow**, full chainedleather.
  - peasant — zombie, iron sword, без брони.
- **`desert`** (desert, badlands, windswept_hills, savanna; desert_pyramid,
  ruined_portal_desert, nova_structures desert, terralith desert):
  - hero — husk, **gold katana**, full heavy_golden.
  - soldier ×2 — husk, **gold glaive** и **gold claymore**, heavy_golden по частям.
  - archer — **`variantsandventures:parched`**, **bow**, full chainedleather.
  - peasant — husk, iron sword.
- **`ocean`** (ocean/warm/lukewarm/deep, river, beach; shipwreck, ocean_ruin,
  nova_structures:conduit_ruin):
  - hero — drowned, **trident**, full `armory_bridge:heavy_copper_*`.
  - soldier ×2 — drowned, iron sword/axe, heavy_copper по частям.
  - archer — drowned, **bow**. peasant — drowned, iron sword.
- **`ancient_city`** (underground only, warden targeting):
  - hero — zombie, **diamond katana**, full `create_deep_dark:echo_armor_*`.
  - soldier ×2 — zombie, **diamond glaive** и **diamond claymore**, echo по частям.
  - archer ×2 — skeleton, **crossbow** и **bow** (без брони).

### 4.3 Скелеты-ближники в отрядах — `skeletonMelee` (F2, итоги правок)
- `enabled: true`, `chance: 0.25`,
  `replaceEntities: ["minecraft:zombie"]`,
  `skeletonEntity: "minecraft:skeleton"`,
  `applyTo: ["hero", "soldier", "peasant"]`.
- **Механика (после итоговых правок):**
  1. Ближние роли отряда (hero/soldier/peasant) вооружаются **по столу оружия
     скелета** из §3 (правило 12). **Приоритет двуручному оружию** — вес
     двуручного в `weapons` = **2**, одноручного = **1**.
  2. Лук/арбалет/мукет исключены из ближнего ролла.
  3. Слоты зомби в составе отряда могут заменяться на **скелетов как ближних
     мобов** с вероятностью 0.25 — **без трансформации сущностей** (просто другой
     entity в слоте). Броня и оружие — те же, что у зомби-рола.
  4. Археры (archer role) из ролла вооружения исключены.
- `weapons` (итоговый стол): двуручные (вес 2) — claymore, glaive, greataxe,
  greathammer, scythe (iron + gold); одноручные (вес 1) — cutlass, katana, rapier
  (iron + gold). Шансы соответствуют оригиналу: для равных шансов все веса
  можно поставить в 1.

### 4.4 Ремап брони — `armorRemap` (F3)
- По температуре биома: cold / temperate / hot / mesa / nether —
  `heavy_iron` → copper/iron/golden с шансовыми распределениями.
- Окисление меди: wet/dry/alwaysOxidizedBiomes — вероятность oxidation
  base/exposed/weathered/oxidized.

### 4.5 HP-бафф — `hpBuff` (F4)
- `enabled: true`, `percent: 0.1`, `min: 1.0`. +10% maxHP нежити, только в
  64 блоках от деревни (не boost-all).

### 4.6 Странники — `wanderers` (F8)
- `enabled: true`, `scanIntervalTicks: 200`, `detectionRadius: 64`,
  `chance: 0.1`, `biomes: []`.
- Castes: soldier (вес 60) — zombie, iron glaive, heavy_iron helmet+chest;
  hero (вес 40) — zombie, iron katana, full heavy_iron.

### 4.7 Остальные механики
- **`ruinBuff`** (F9): `countMultiplier: 1.5` (cap 32), структуры-руины
  (ruined_portal, nova_structures hamlet/wild_ruin/well_oak,
  terralith:fortified_village).
- **`effects`**: сила (strength) amp 0, chance 0.1 на zombie/skeleton/husk/drowned.
- **`march`** (F10): `triggerRadius: 128`, spawnMin/Max 64/96,
  `appliesTo: ["plains", "desert"]`.
- **`warden`** (F7): enabled — targeting для древнего города.
- **`animation`** (F6): durationTicks 45, particleCount 6, sound.

---

## 5. Задачи на подтвержение (баги из игре — все невыполненные)

1. **Команда тестирования портальных странников** — нужна
   (`wanderers` castes из §4.6); на данный момент есть только отряды и мобы.
2. **`spawn mob` вызывает мобов без брони и оружия** — нужна экипировка
   (InControl экипирует на `finalize`, что не срабатывает при `addFreshEntity`).
3. **Скелеты с арбалетами заряжают, но не стреляют.**
4. **Утопленники не стреляют из лука.**
5. **Пустынные отряды спавнятся без дальних юнитов** (archer =
   `variantsandventures:parched` — optional dependency).
6. **Отряды древнего города без защиты** (echo-броня — optional dependency).
7. **Алмазные и выше доспехи никогда не появляются** — проверить достижимость
   порога сложности (§4.1) и шансы 0.004.

---

## 6. Ключевые файлы (справочно)

- `config/incontrol/spawn.json` — присланная в первом сообщении версия (§3).
- `config/ancient_civilization.json` — конфиг мода: механики спавна (§4).
- `ancient_civilization/src/main/java/com/ancientcivilization/`:
  - `command/SpawnTestCommands.java` — команды тестирования (§2.12).
  - `spawn/AncientRangedGoal.java` + `RangedAIHandler.java` — стрелковый AI (§2.13:3–4).
  - `spawn/EquipmentApplier.java`, `spawn/ArmorRemapper.java`,
    `spawn/LocalDifficultySpawner.java` — экипировка/топ-tier (§2.13:2,6,7).
  - `squad/SquadService.java` — движок отрядов + скелеты-ближники (§4.2–4.3).
  - `wanderer/WandererService.java` — странники (§2.13:1).
