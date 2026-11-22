# SESSION HANDOFF — ancient_civilization (Minecraft 1.21.1 NeoForge)

> Инструкция для новой сессии: **всю проделанную работу считать НЕ выполненной** —
> ни один баг не признан подтверждённым в игре. Документ описывает проект,
> все требования пользователя, весь попытканный этап и оставшийся список задач.
> Проходим всё с чистого листа, перепроверяя каждое изменение сборкой и игрой.

---

## 1. Суть проекта

Мод **`ancient_civilization`** (`com.ancientcivilization`) — переработка спавна
вражеских мобов в Minecraft **1.21.1 / NeoForge**. Не добавляет новый ресурсный
пакет; поведение задаётся конфигом и Java-логикой.

**Что мод делает (F1–F10):**
- **F1** Ядро: `EntityJoinLevelEvent` (NORMAL, после InControl) — контекст биома/структуры, конфиг JSON.
- **F2** Вооружение скелета у ближнего бойца + скелеты как ближние мобы в отрядах (см. §2).
- **F3** Ремап `heavy_iron` → copper/iron/golden по температуре биома + окисление меди по влажности.
- **F4** HP-бафф: +10% maxHP нежити (в т.ч. отряды), через `AttributeModifier`.
- **F5** Стрелковый AI без миксинов: самостоятельный `AncientRangedGoal` (зарядка + выстрел).
- **F6** Движок событийных отрядов (таймер → спавн → анимация выкапывания → сброс).
- **F7** Варден-приоритет для отряда Древнего города.
- **F8** Заблудшие странники на руинах порталов (один ролл на структуру, SavedData).
- **F9** Буст «рядом со структурой» (таймер + тег).
- **F10** Марш отряда к деревне.

**Статическая экипировка (касты, столы оружия/брони)** живёт в **InControl**
(`config/incontrol/spawn.json`). **Логика/состояние** — в моде. Это важно:
InControl экипирует на `finalize` (только при естественном спавне), а мод — на
`EntityJoinLevelEvent`.

**Зависимости:** только `neoforge` + `minecraft` required. Всё остальное
**optional**: `incontrol`, `simplyswords`, `irons_artifice`, `the_sift`,
`create_deep_dark`, `variantsandventures`, `luckysarmory`, `armory_bridge`,
`chained_leather_armor`. Модовые ID предметов/мобов разрешаются только если
мод установлен — иначе пропускается со warn (см. §6, баги из-за этого).

---

## 2. Все требования пользователя (накопленные в сессии)

1. **HP-бафф 10%**, в т.ч. на отряды; буст применяется **только в 64 блоках от
   деревни** (НЕ boost-all).
2. **`mindifficulty` 3.0**; map reroll enabled (напр. значение, см. п. 8).
3. **Отряды:** странники (wanderers) soldier/hero с вероятностью; RuinBuff —
   только `countMultiplier` (численность, cap 32); **герой не дропает** ничего.
4. **Избегать `@Implements` soft-interface миксинов** — удалить, не чинить.
5. **`goalSelector`/`targetSelector` — protected**: добавлять goals через рефлексию
   (`MobGoals`), никогда напрямую.
6. **Удалить оба миксин-файла**, не чинить.
7. **Создавать отдельный branch нежелательно** — commit должен быть виден на `main`.
8. **F2 (последняя корректка пользователя — считать главыственным):**
   - **НЕ трансформация сущностей** (не «скелет → зомби», не «зомби → скелет»).
   - Ближнего бойца вооружать **вооружением скелета** (как в оригинальном конфиге),
     с **приоритетом двуручному оружию**; шансы на оружие — как в оригинале.
   - Скелеты **как ближние мобы** (с теми же шансами на оружие и броню, что у зомби)
     могут появляться **вместо зомби в составе отрядов**.
9. **Команды тестирования:** нужны команды для призыва всех типов отрядов и всех
   типов мобов, которые может породить мод.
10. **7 багов из игре** (см. §5) — все требуют исправления.

---

## 3. Структура репозитория

- `ancient_civilization/src/main/java/com/ancientcivilization/` — исходники мода.
  - `AncientCivilization.java` — точка входа; читает конфиг, регистрит события и команды.
  - `config/ModConfig.java` — весь конфиг (Gson, публичные поля). Секция `SkeletonMelee`
    заменила старую `Reroll`.
  - `config/ConfigLoader.java` — читает `config/ancient_civilization.json`, пишет дефолт.
  - `event/CommonEvents.java` — `onEntityJoin` (ArmorRemapper, HpBuffHandler,
    LocalDifficultySpawner, EffectsHandler, RangedAIHandler) + `onLevelTickPre`
    (SquadService.tick, WandererService.tick).
  - `spawn/` — `AncientRangedGoal`, `RangedAIHandler`, `MobGoals` (рефлексия),
    `ArmorRemapper`, `EffectsHandler`, `EquipmentApplier`, `LocalDifficultySpawner`,
    `HpBuffHandler`, `StructureLookup`, `WardenTargeting`.
  - `squad/` — `SquadService` (движок отрядов + `summon` хук), `SquadMarchGoal`,
    `SquadSaveData`, `SquadAnimation`.
  - `wanderer/` — `WandererService`, `WandererSaveData`.
  - `command/SpawnTestCommands.java` — `/ancientcivilization spawn ...` (см. §5, баги).
- `config/ancient_civilization.json` — конфиг мода (референс; редактируется пользователь).
- `config/incontrol/spawn.json` — конфиг InControl (столы каст/оружия/брони).
- `docs/ancient_civilization/PLAN.md` — утверждённый план механик (русский).
- `docs/ancient_civilization/SESSION_HANDOFF.md` — этот файл.

---

## 4. Проделанный этап (СЧИТАТЬ НЕ ВЫПОЛНЕННЫМ — всё перепроверить)

Цель этапа: исправить `:compileJava`, переписать исходники под ванильный API **1.21.1**,
удалить миксины, заменить ranged AI на самостоятельный goal, добавить команды
тестирования и исправить краши/баги в игре. **Все изменения отправлены на `main`,
но пользователь просит считать их неподтверждёнными.**

### 4.1 Build / API / миксины
- Переписано всё Java под **1.21.1**. Удалены 2 миксина и `ancientcivilization.mixins.json`;
  `[[mixins]]` удалён из `neoforge.mods.toml`.
- Ranged AI заменён на самостоятельный `AncientRangedGoal` (зарядка + `arrow.shoot`).
- Ванильный API 1.21.1: `ResourceKey` в `net.minecraft.resources`,
  `RegistryLookup.get(ResourceKey)→Optional<Holder<Reference<T>>`,
  `ResourceKey.create` 2-arg, `Goal.canContinueToUse()`, `mob.level()`,
  `getItemBySlot`, `setPersistenceRequired()` без аргумента, `computeIfAbsent` 2-arg,
  `NearestAttackableTargetGoal` 3-arg, `ChunkAccess.getAllStarts()`,
  `ParticleType` не extends `ParticleOptions` → `BlockParticleOption`,
  `Holder.direct`/`getRegisteredName`, `getBaseTemperature()`.

### 4.2 Runtime-краши (в игре)
- **`HpBuffHandler.buff()`**: `addPermanentModifier` → **`addOrReplacePermanentModifier`**
  (дублирующий модификатор `ancient_civilization:hp_buff` кинул
  `IllegalArgumentException: Modifier is already applied` → краш при повторном join).
- **`neoforge.mods.toml`**: `versionRange` `[21.11,)` → **`[21.1,)`** (старый парсился
  как `[21,11]` и блокировал старт «requires neoforge 21.1.11 or above»).
- **`SquadService`**: lambda-capture — финальные копии `marchingFinal`/`marchTargetFinal`.
- **`SquadAnimation`**: `FALLING_DUST` → `new BlockParticleOption(...)`.
- **`SquadMarchGoal`**: `canContinue()` → **`canContinueToUse()`**.
- **`SquadService`/`WandererService`**: `import net.minecraft.resources.ResourceKey`.

### 4.3 F2 — реролл → вооружение скелета / скелет-ближник
- **Удалён `RerollHandler`** и его вызов в `CommonEvents` (трансформация сущностей
  убрана полностью — ни та, ни обратная).
- Секция `reroll` в конфиге заменена на **`skeletonMelee`**:
  - `chance = 0.25` (из оригинала), `replaceEntities = ["minecraft:zombie"]`,
    `skeletonEntity = "minecraft:skeleton"`, `applyTo = ["hero","soldier","peasant"]`.
  - `weapons` — стол оружия скелета из оригинала (`incontrol/spawn.json`, правила
    скелета), ближние оружия: claymore/glaive/greataxe/greathammer/scythe (двуручные,
    вес 2) и cutlass/katana/rapier (одноручные, вес 1). Лук/арбалет/мукет исключены.
- `SquadService.spawnSquad`: ближние роли вооружаются по столу скелета; скелеты как
  ближние мобы появляются вместо зомби (25%); броня берётся из рола (та же, что у зомби).
- `LocalDifficultySpawner.pickWeighted` сделан `public` для переиспользования.

### 4.4 Команды тестирования `/ancientcivilization spawn ...`
- `spawn squad <type> [count]` / `spawn squad all [count]`
- `spawn mob <entity> [count]` / `spawn mob all [count]`
- Мобов проходят `EntityJoinLevelEvent` → к ним применяются handlers мода.
- **Много итераций исправления ошибок компиляции** (см. §6.2).

### 4.5 Git-состояние (на конец сессии)
- Branch: `arena/c98356ce-praaaaaaaaaaaaaaaaaaarararara`, fast-forward на `main`.
- `origin/main` = **`06a1a03`**. Цепочка (по возрастанию):
  `707e876` (Initial) → `7387d84` (мод F1-F10) → `bd0eabc` (build) → `6c48a64` (neo 21.1.267)
  → `335a294` (API rewrite + миксины удалены) → `fc42f8e` (PLAN) → `0384f71`/`8d456fc`
  (ResourceKey) → `9591628` (SquadAnimation) → `9804e9b` (lambda) → `a97b5f0` (neo range)
  → `f8af5e4` (HpBuff) → `0ab2565` (reroll reverse) → `825d7d0` (test commands)
  → `0a4a054` (F2 skeletonMelee) → `7d48072`/`e58f88d`/`1ef1dee`/`06a1a03` (compile-fix).
- **Все commit — на `main`** (не на отдельном branch), как требовал пользователь.

---

## 5. Текущий список задач / баги (все НЕ подтверждены — перепроверить)

Пользователь выдал 7 пунктов. **Рассматривать как невыполненные задачи.**

1. **Нужна команда для тестирования портальных странников** (WandererService, F8).
   Команды `spawn squad`/`spawn mob` есть, а странников — нет. Добавить, например,
   `/ancientcivilization spawn wanderer [count]` (сплечение по конфигу `wanderers.castes`).

2. **Все мобы, вызываемые командой `spawn mob`, появляются без брони и оружия.**
   Причина (предположительная): InControl экипирует на `finalize`, который **не
   срабатывает** на `addFreshEntity` (командный спавн). Может потребоваться задать
   базовую экипировку прямо в `spawnMobs` (по типу мобa) или заменить спавн на
   способ, проходящий через InControl.

3. **Скелеты с арбалетами заряжают арбалет, но не стреляют.**
   Баг в `AncientRangedGoal` (стрельба из арбалета). Зарядка работает
   (`startUsingItem`), стрельба — нет.

4. **Утопленники не умеют стрелять из лука.**
   Баг в `AncientRangedGoal`/`RangedAIHandler` (стрельба из лука у дрowned).

5. **Отряды в пустыне спавняются без скелетов (без дальних юнитов).**
   Archer пустыни = `variantsandventures:parched` (optional dependency). Если V&V
   не установлен — `parched` не разрешается → дальний юнит не спавнится.
   Нужно сделать дальний юнит гарантированным (fallback на скелет) или проверить
   наличие V&V.

6. **Отряды в древнем городе не имеют никакой защиты.**
   Броня hero/soldier Древнего города = `create_deep_dark:echo_armor_*` (optional
   dependency). Если Create: Deep Dark не установлен — echo-броня не разрешается →
   защита нулевая. Нужно fallback на ванильную diamond/netherite или проверить dep.

7. **Алмазные и выше доспехи в принципе не появляются никогда.**
   Топ-тиры (`LocalDifficultySpawner`) спавнят netherite/diamond/echo/siftite при
   эффективной сложности ≥ порога (`mindifficulty 3.0`), chancePerSlot = 0.004.
   Возможно: мир лёгкий/новый (порог не достигается), шансы слишком низкие, или
   баг. Проверить логику и достижимость порога.

---

## 6. Ключевой технический контекст

### 6.1 1.21.1 API (проверенные решения — не перепроверять с нуля, но учитывать)
- `ResourceKey` — **`net.minecraft.resources`** (не `core`).
- `level.registryAccess().lookupOrThrow(Registries.STRUCTURE)` →
  `HolderLookup.RegistryLookup<T>`; `registry.get(ResourceKey.create(...))` →
  `Optional<Holder<Reference<T>>` → `.map(Holder::value)`.
- `ResourceKey.create` — **2-arg**.
- `Goal`: `canUse()`, `canContinueToUse()`, `tick()`, `stop()`, `requiresUpdateEveryTick()`.
- `mob.level()` (не `getLevel()`); `getItemBySlot`/`setItemSlot`; `setPersistenceRequired()`.
- Пarticles: `net.minecraft.core.particles.*`; `ParticleType` не `ParticleOptions`.
- `computeIfAbsent` 2-arg; `NearestAttackableTargetGoal` 3-arg; `ChunkAccess.getAllStarts()`.
- `Registry.getKey(T)` → `ResourceLocation` напрямую (не Optional).

### 6.2 Brigadier (команды) — из几次 compile-fix
- Считывать аргументы: **`ctx.getArgument(name, Class)`** (НЕ
  `StringArgumentType.getArgument` — такого статического метода нет).
- `RequiredArgumentBuilder.defaultValue(T)` **недоступен** в этой версии — реализовать
  ветку без count + ветку с count.
- `CommandSourceStack.sendSuccess(Supplier<Component>, boolean)` — заворачивать в лямбду.
- `sendFailure(Component)` — напрямую.
- Уровень: **`source.getLevel()`** (ServerLevel); игрок — **`source.getPlayer()`**
  (nullable, не бросает). `CommandSyntaxException` **не разрешается** в этой
  сборке Brigadier — избегать явного импорта/`throws`.
- Уровень разрешения команд: 2 (OP).

### 6.3 Рефлексия goals
- `goalSelector`/`targetSelector` — protected. Использовать `MobGoals.addGoal`
  (рефлексия). Не вызывать напрямую.

### 6.4 Компиляция
- **В песочнице нет JDK** — локальная сборка невозможна. Пользователь компилирует
  на своей машине (`.\gradlew.bat build`) и присылает тексты ошибок. Все compile-fix
  делались по присланным сообщениям. **Перед любым commit — проверять, что ошибки
  сборки устранены.**

### 6.5 Convention
- Изменения — **на `main`**, не на отдельном branch.
- Commit-ить и push (`git push origin HEAD:main`).
- Конфиг редактируется без пересборки (Gson).

---

## 7. Что делать следующей сессии (порядок)

1. Перечитать этот handoff + `PLAN.md`.
2. Перепроверить current state: `git log origin/main` (tip `06a1a03`), файлы на месте.
3. Взять задачи из §5 по приоритету и исправить:
   - Сначала **баги стрелкового AI (3, 4)** — ядро `AncientRangedGoal`/`RangedAIHandler`.
   - Затем **гарантированная экипировка/дальние юниты (2, 5, 6)** — fallback при
     отсутствии optional-dependency.
   - **Топ-тиры (7)** — проверить достижимость порога и шансы.
   - **Команда странников (1)** — добавить.
4. При каждом изменении — убедиться в compile (по присланному логу) и commit/push на `main`.
5. Сообщить пользователю, что всё считается неподтверждённым до его проверки в игре.
