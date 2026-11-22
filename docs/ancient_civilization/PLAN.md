# Ancient Civilization — план механик спавна (ФИНАЛ v3, утверждён)

> Мод: `ancient_civilization`. Платформа: **Minecraft 1.21.1, NeoForge**.
> InControl-конфиг: **`config/incontrol/spawn.json`** (написан, см. §10).
> Все подтверждения §11 получены — блокеров нет.

## Решённые вопросы

- ✅ ID: эхо = `create_deep_dark:echo_armor_helmet/chestplate/leggings/boots` (подтверждено по исходникам `robinfrt/create_deep_dark`); сифтит = `the_sift:siftite_helmet/chestplate/leggings/boots` (по исходникам `byMr712/MielonsTheSift-*`); heavy = `armory_bridge:heavy_echo_*` / `heavy_siftite_*`. Медь: базовая без приписки + `_exposed/_weathered/_oxidized`.
- ✅ Дип дарк — биом (Create: Deep Dark); отряд города спавнится **только внутри `minecraft:ancient_city`** (DnT город модифицирует, но ID ванильный — `data/minecraft/...` в пакете нет).
- ✅ V&V: **gelid, thicket — зомби-тип**; **verdant, murk, parched — скелет-тип**.
- ✅ Структуры: DnT = `nova_structures:*` (153 структуры), Terralith = `terralith:*`, ванильные ID не переопределены.
- ✅ Подтверждения (ask_user): варианты **×0.5**; бафф ХП **10%, и на отряды**; буст у структур **×2, радиус 64, деревня НЕ бустит** (только точка марша §6.3); `mindifficulty` **3.0**; реролл-маппинг **ок** (husk→parched, drowned→murk, gelid→stray, thicket→verdant, zombie→skeleton).
- ✅ **F3 «Марш к деревне»**: наземный отряд в ~128 блоках от деревни спавнится в отдалении (64–96 от границы) и идёт к деревне.

## ⚠ Находка: мёртвые правила в старом конфиге

По исходникам InControl (`1.21_neo`, `ForgeEventHandlers.onEntitySpawnEvent`): **первое совпавшее правило без `"continue": true` прерывает обработку**. В старом конфиге:

- Стол скелетов (без `random`) перехватывал **всех** скелетов → все правила брони/тяжёлых сетов/diamond/netherite для скелетов **никогда не срабатывали**.
- То же для пиглинов и зомбифицированных: стол оружия без `random` убивал все их правила брони.
- У зомби каскад работал (правила с `random` при неудачном ролле «пропускают» дальше), но ~15% зомби с оружием из первого правила никогда не доходили до брони.

**В новом конфиге все правила экипировки помечены `"continue": true`** — пиглинские сеты тяжёлой брони и лёгкая броня скелетов теперь реально работают (это изменение поведения — если хотите как раньше, уберите `continue` у стола оружия пиглинов/скелетов).

Дополнительно: NBT-формат предметов в 1.21.1 — `{"id": ..., "count": 1}` (lowercase); в старом конфиге был `"Count"` (формат до 1.20.5). Исправлено.

---

## 1. Архитектура

| Класс | Примеры | Куда |
|---|---|---|
| **Статика** — «шанс предмета X» | касты, веса оружия/брони, weighted-списки | **InControl** (JSON) |
| **Логика/состояние** | отряды, варден-приоритет, странники, бафф ХП, окисление, реролл, марш | **мод** |

Аргументы: правка весов без пересборки (`/incontrol reload`), `finalize`-хук обкатан, мод остаётся маленьким; при поломке InControl перенос правил в мод механический.

Проверено по исходникам 1.21_neo: `random` — условие match (не сработал ролл → правило пропущено, цепочка продолжается); `continue` — флаг; `result: allow` отменяет ванильный finalize (скелет не получит лук ваниллы — даём свой); `mindifficulty` = regional difficulty (`getEffectiveDifficulty()`); ключи `armorhelmet/armorchest/armorlegs/armorboots/helditem/nbt/structure/structuretags/hasstructure` подтверждены.

**Функции мода `ancient_civilization`:**
- **F1.** Ядро: `EntityJoinLevelEvent` (после InControl, чтобы видеть финальную экипировку), контекст биома/структуры, конфиг JSON/TOML.
- **F2.** Реролл «ближник → скелет-тип» (25%): zombie→skeleton, husk→parched, drowned→murk, gelid→stray, thicket→verdant; wither_skeleton — без реролла. Замена сущности с переносом экипировки (защита от повторного срабатывания — маркер NBT).
- **F3.** Ремап heavy_iron → heavy_copper/heavy_iron/heavy_golden по температуре биома (веса §4) + стадия окисления меди по влажности.Heavy diamond/netherite/echo/siftite — не трогает.
- **F4.** Бафф ХП: 10% (и на отряды), равномерно 1..+50% maxHP, attribute modifier.
- **F5.** ИИ стрелков: арбалет у скелетов, лук у утопленников/мурков — **самостоятельный goal** `AncientRangedGoal` (зарядка + выстрел по vanilla-паттерну `AbstractSkeleton.performRangedAttack`: `ProjectileUtil.getMobArrow` + `Projectile.spawnProjectileUsingShoot`). Миксин-интерфейсы отменены (см. §5).
- **F6.** Движок событийных отрядов (таймер→спавн→сброс) + 4 отряда (§6).
- **F7.** Варден-приоритет для отряда Древнего города (§7).
- **F8.** Заблудшие странники на руинах порталов (один ролл на структуру, SavedData) (§8).
- **F9.** Радиус-буст «рядом со структурой»: ×2 к шансу каст, радиус 64, разблокировка SS diamond + heavy diamond частей (списки §6.4). Деревня НЕ бустит.
- **F10.** Марш отряда к деревне (§6.3).

Интеграция с Hostile Tactics: роли назначаются явно при спавне отряда; стрелки — ренж-голы; «держаться лидера» — низкоприоритетная цель; таргет-селекторы не перебивают Hostile Tactics, кроме варден-правила города.

---

## 2. Касты (утверждено)

Роли: **горожанин / лучник / солдат / герой**. Илладжеры, криперы и прочие — вне механики.

- Вариантные мобы (V&V): ×0.5 к базовым.
- Эффективные вероятности (base → variant):
  - Горожанин: **9.84%** → 4.92% — без брони; оружие: 40% ванильное простое (дерев/камень/железо меч-топор) / 60% SS iron+gold. У утопленника в столе горожанина **250‰ трезубец** (лор рыбаков-стражей затонувших поселений).
  - Солдат (2 части heavy: шлем+нагрудник/поножи/ботинки): **1.29%** → 0.65% — оружие SS iron+gold / ванильное железо.
  - Герой (полный heavy-сет): **0.30%** → 0.15% — оружие SS iron+gold.
  - Голая нежить (ванильный finalize): **88.57%** → 94.28%.
- Скелеты: лучник **всегда вооружён** (500 bow / 30 crossbow / 30 musket / ~2.8% редкий ближний SS iron+gold); лёгкая броня (chained/studded сеты 3%, per-slot leather/golden/chainmail 5/4.5/4/4%) ×0.5 для вариантов.
- Реролл «ближник → скелет-тип» — 25% ближних каст (F2), снаряжение переносится.
- В **InControl** heavy-броня пишется нейтрально `luckysarmory:heavy_iron_*`; материал (copper/iron/golden) и окисление назначает мод (F3) по биому.

### Тиры оружия (утверждено)

| Тир | Где |
|---|---|
| Дерево/камень/железо/золото + SS iron/gold | амбиент |
| SS diamond + heavy diamond части | только F9 (у структур) и отряды |
| Незерит/эхо/сифтит (обычные) + SS netherite | только `mindifficulty` ≥ 3.0 (правила в JSON), **дроп разрешён** |
| heavy незерит/эхо/сифтит | **никогда не дропаются**; надеваются только отрядом Древнего города |

## 3. ИИ стрелков (F5)

- Реализовано **без миксинов**: самостоятельный goal `com.ancientcivilization.spawn.AncientRangedGoal`, добавляемый `RangedAIHandler` при появлении мобов (скелет-семья `AbstractSkeleton`, утопленник-семья `Drowned`).
- Зарядка weapon: `mob.startUsingItem(MAIN_HAND)`, на выходе `releaseUsingItem()`; выстрел по vanilla-паттерну `AbstractSkeleton.performRangedAttack`: `ProjectileUtil.getMobArrow(mob, проекция, 1.6F, weapon)` + `Projectile.spawnProjectileUsingShoot(arrow, ServerLevel, ItemStack, dx, dy+d*0.2, dz, 1.6F, inaccuracy)`.
- Goal самоотключается, если в руке нет `Items.BOW`/`Items.CROSSBOW`; приоритет 4.
- Поскольку `goalSelector`/`targetSelector` — `protected` на `Mob`, добавление идёт через reflection-bridge `MobGoals`.
- Мушкет — флейвор (ИИ под мобов у Iron's Artifice нет).
- Откат, если арбалетная реализация кривая: убрать арбалет из столов.

## 4. Материал heavy-брони и окисление меди (F3) — утверждено

| Зона | copper | iron | golden |
|---|---|---|---|
| Холодные | 30% | **60%** | 10% |
| Умеренные | 45% | 40% | 15% |
| Жаркие | 30% | 15% | **55%** |
| Меса | 25% | 10% | **65%** |
| Незер | 20% | 15% | **65%** |

Окисление меди (стадия = условия залегания, не сила):

| Условие | oxidized | weathered | exposed | обычная |
|---|---|---|---|---|
| Всегда oxidized: утопленник/мурк, Древний город, болото/мангровые, подводные | 100 | 0 | 0 | 0 |
| Влажные (джунгли, пышные пещеры, грибные поля) | 45 | 30 | 15 | 10 |
| Умеренные (равнины, леса, тайга, горы) | 10 | 25 | 30 | **35** |
| Сухие/холодные (пустыня, бэдленды, саванна, снежные) | 0 | 10 | 20 | **70** |

## 5. Бафф ХП (F4) — утверждено

10% на спавн (и на отряды), равномерно 1..+50% maxHP, attribute modifier, задел на будущие статы. Кому: зомби/скелет-семейство включая утопленника и V&V. Не: илладжеры, криперы, варден, боссы.

## 6. Отряды (F6) — утверждено

### 6.1 Движок
- Per-dimension (SavedData) «таймер напряжения» на каждый тип отряда; шанс растёт с накопленным временем → спавн → **сброс**.
- Ожидания: базовая зона ~90 мин; усиленная ×3 (~30 мин); Древний город ~45–60 мин. Глобальный кулдаун между отрядами в измерении — 10 мин.
- Спавн: кольцо 32–48 блоков, вне видимости; утопленники — в толще воды; `persistenceRequired`.
- Связка «держаться лидера» (~16 блоков вне боя). Зачистка: 15 мин без игроков в 128 блоках.

### 6.2 Составы и зоны

| Отряд | База (×1) | Усилено (×3, ≤64 до структуры) | Состав 4–6 |
|---|---|---|---|
| Утопленники | океан/глубокий океан/река вдали от структур | `minecraft:shipwreck`, `minecraft:ocean_ruin`, `nova_structures:conduit_ruin` | 1 герой (полный heavy_copper **oxidized**) + 2 солдата (1–2 части copper oxidized) + 1–2 стрелка (луки, chained leather) |
| Воины пустыни | пустыня/бэдленды вдали от структур | `minecraft:desert_pyramid`, `minecraft:ruined_portal_desert`, `nova_structures:desert_ruins`, `nova_structures:parched_camp`, `nova_structures:badlands_miner_outpost`, `nova_structures:well_desert`, `nova_structures:tavern_desert`, `terralith:desert_outpost`, `terralith:fortified_desert_village`, `terralith:rubble_desert` | 1 герой (полный heavy_golden) + 2 солдата (хаски) + 1–2 стрелка (parched с луками) |
| Отряд равнин | plains/sunflower/meadow вдали от структур | `minecraft:ruined_portal` (+варианты), `nova_structures:hamlet`, `nova_structures:wild_ruin`, `nova_structures:remnant_*`, `nova_structures:well_oak`, `terralith:fortified_village` | 1 герой (полный heavy_iron) + 2 солдата + 1–2 стрелка + горожанин |
| Древний город | — | только **внутри `minecraft:ancient_city`** | 1 герой (heavy_echo 65% / heavy_siftite 35%) + 2–3 солдата + 1–2 стрелка (§7) |

Оружие — из столов каст; герои могут нести SS diamond. ХП-бафф применяется (10%).

### 6.3 Марш к деревне (F10) — утверждено
Наземный отряд сработал в ~128 блоках от деревни (`#minecraft:village`, включая деревни DnT/Terralith в теге): спавн **в отдалении** (64–96 от границы, вне видимости) → групповая цель — POI деревни (колокол/кровати) → движение строем → в деревне обычное поведение. Деревня при этом **не бустит** шанс каст (подтверждено).

### 6.4 F9-буст списки структур
- Океан: `shipwreck`, `ocean_ruin`, `nova_structures:conduit_ruin`
- Пустыня: `desert_pyramid`, `ruined_portal_desert`, `nova_structures:desert_ruins/parched_camp/badlands_miner_outpost/well_desert/tavern_desert`, `terralith:desert_outpost/fortified_desert_village/rubble_desert`
- Равнины: `ruined_portal`+варианты, `nova_structures:hamlet/wild_ruin/remnant_*/well_oak`, `terralith:fortified_village` (**без деревень** — не буст)
- Древний город: `ancient_city`

## 7. Отряд Древнего города — утверждено
- Только событием, только внутри `minecraft:ancient_city` (биом не проверяем).
- Герой 100% (heavy_echo 65% / heavy_siftite 35%), солдаты 70%, стрелки 40% (иначе chained leather). **Дроп heavy эхо/сифтит/незерит = 0** (ArmorDropChances/HandDropChances NBT).
- **Варден = приоритет игрока**: селектор «ближайший из (игрок, варден)»; скалк-шум отряда сам может поднять вардена.

## 8. Заблудшие странники (F8) — утверждено
- `ruined portal` (оба измерения), **один ролл на инстанс** (SavedData, навсегда).
- 40% «здесь» (оверворлд) / 60% для незерских порталов → в незере моб на портале чаще.
- Появление при подходе игрока ~32 блока. Снаряжение: горожанин 55 / лучник 25 / солдат 15 / герой 5 (тиры амбиента).

## 9. Дроп — утверждено
| Сет | Дроп |
|---|---|
| heavy copper/iron/golden | ванильный (~8.5%/слот) |
| heavy diamond | ванильный |
| **heavy netherite/echo/siftite** | **0 — никогда** |
| обычные echo/siftite/netherite (+SS netherite) | только `mindifficulty` ≥ 3.0, дроп разрешён |

Local difficulty (minecraft.wiki): Easy 0.75–1.5, Normal 1.5–4.0, Hard 2.25–6.75 → **порог 3.0**: на Normal почти максимум (старые чанки + полнолуние), на Hard достижим регулярно. Если сборка на Hard — топ-тиры будут заметно чаще; можно поднять порог до 3.5–4.0 правкой одной цифры.

## 10. InControl-конфиг (написан): `config/incontrol/spawn.json`

41 правило (топ-тиры по `mindifficulty` **перенесены в мод** — см. §12.1: теперь порог и срабатывание настраиваются по уровням сложности в `config/ancient_civilization.json`). Порядок и семантика:
1. **Касты зомби-семейства** (горожанин 0.10/0.05 → солдат 0.005/0.004/0.004 → герой 0.003/0.0015; варианты ×0.5) — все `continue: true`: правила складываются, позднее перекрывает раннее (герой поверх солдата). Утопленник-горожанин — трезубец в столе.
2. **Скелеты**: стол лучника (всегда) → chained/studded сеты → per-slot лёгкая броня; варианты ×0.5.
3. **Пиглины**: столы оружия (как в старом конфиге) → heavy golden partials → chained/studded → per-slot. Теперь реально работают (было мёртвым кодом).
4. **Dual-wield флейвор** 0.01 (iron_cutlass / golden_sword, NBT `count` lowercase).

Убрано против старого конфига: SS diamond/netherite из амбиента (→ F9/топ-тиры), heavy diamond singles (→ F9), heavy netherite 0.0001 (→ отряд города), «drop_chances» NBT (не ванильный тег — не работал), лёгкая броня у зомби-семейства (касты её заменяют), правила топ-тиров (→ мод, §12.1).

Мод (F3) ремапит `heavy_iron_*` → copper/iron/golden; InControl остаётся нейтральным.

## 11. Порядок работ
1. ~~План~~ ✅ утверждён.
2. ~~InControl JSON~~ ✅ написан (правки весов — по ощущениям в игре).
3. ~~Мод: ядро + local difficulty + отряды (F1, F6-ядро, анимация, поверхностный спавн)~~ ✅ см. §12.
4. ~~Мод F2–F5, F7–F10 + бафф в руинах (численность) + эффекты + конфигурируемость~~ ✅ см. §12.5.
5. **Тест на сборке, подгонка чисел в игре.**

## 12. Реализация мода `ancient_civilization/` (1.21.1 NeoForge)

Исходники: skeleton Gradle (`net.neoforged.moddev` 2.0.148, NeoForge `21.1.267`, JDK 21), `neoforge.mods.toml`, Gson-конфиг `config/ancient_civilization.json`. Все API проверены по vanilla-исходникам 1.21.1 (`DevPaimonSoft/RelakeReborn`, branch `main`): `Registry` = `net.minecraft.core`, `lookupOrThrow` (не `registryOrThrow`), `SavedData.Factory` (не `SavedDataType`), `getItemBySlot` (не `getItemSlot`), `Registry.getKey` → `ResourceLocation` напрямую, `AttributeModifier` — record, `canSeeSky`, `getRegisteredName`, `setPersistenceRequired()` без аргумента, `BlockParticleOption`, непрефиксные `SoundEvents.*_BREAK`, `InteractionHand` = `net.minecraft.world`, `EntityJoinLevelEvent` — InControl экипирует на LOWEST, поэтому наш хук на NORMAL.

### 12.1 Local difficulty по уровням сложности (новое)
- `localDifficulty.enabled`, `levels.{easy,normal,hard}.{enabled,threshold}` — порог сверживается с `getEffectiveDifficulty()` (raw regional difficulty, как и InControl).
- По умолчанию: easy — выключен (3.5 никогда не достигается), normal — 3.0 (почти максимум), hard — 2.5 (регулярно).
- Топ-тиры (netherite/diamond/echo/siftite броня + SS netherite оружие) спавнятся **модом** на `EntityJoinLevelEvent` (NORMAL, после InControl) для 12 мобов нежити включая V&V: per-slot `chancePerSlot` + `chanceWeapon`, weighted-списки в конфиге.
- InControl-конфиг очищен от правил топ-тиров (41 правило).

### 12.2 Анимация спавна отряда — выкапывание (новое)
- Без сторонних библиотек: частицы (`BlockParticleOptions(ParticleTypes.BLOCK, блок по биому)` + `FALLING_DUST`) и звуки (`BLOCK_*_BREAK`) через ванильные системы — конфликтов нет.
- Фазы: 0–55% — земля трещит (расширяющееся кольцо блоковых частиц); 55–100% — подъём пыли (парящий столбец); финал — всплеск + звук; мобы появляются в конце (2.25 с по умолчанию, `durationTicks`).
- Блок «копки» выбирается по биому (песок/красный песок/гравий/земля/лёд).
- Звук играет в радиусе, частицы отправляются клиентам сами.

### 12.3 Спавн отрядов только на поверхности (новое)
- `squads.surfaceOnly = true`: проверка позиция — блок-свет = 15 (полный дневной свет, т.е. на поверхности, не в пещере), снизу твёрдая земля, сверху воздух (для океанских — вода), не лава.
- Кольцо спавна 32–48 блоков от игрока, вне видимости; биомы-предпочтения из конфига (fallback — любая поверхность).
- Отряд Древнего города:例外 — `undergroundAllowed = true`, поиск `minecraft:ancient_city` от спавна мира (позиция кэшируется в SavedData).

### 12.4 Движок отрядов (F6-ядро)
- Per-dimension `SavedData`: таймеры по типам, глобальный кулдаун 5 мин, интервалы base 90 мин / boosted 30 мин (boosted — рядом со структурой из списка типа, проверка сеткой 64 блока).
- Композиция и экипировка из конфига (4 отряда §6.2): hero/soldier/archer/peasant; теги `ancient_civilization:squad[_type][_role]`, `persistenceRequired`; герои — дроп брони/оружия = 0.

### 12.5 Завершённые функции (F2–F10 + бафф в руинах)
- **F2 реролл** (`reroll`): ближник → скелет-тип 25%, экипировка переносится, замена + cancel join (замена — скелет-тип, не рероллится).
- **F3 ремап/окисление** (`armorRemap`): `heavy_iron_*` → copper/iron/golden по температуре биома (холод/умер/жар/меса/незер, веса §4); меди — стадия по влажности (always oxidized: утопленник/мурк, Древний город, болото; wet/temperate/dry веса base/exposed/weathered/oxidized). Трогает только heavy-tier; diamond/netherite/echo/siftite — не трогает.
- **F4 бафф ХП** (`hpBuff`): 10% maxHP на 12 мобов нежити включая V&V и отряды; илладжеры/криперы/варден — не трогает.
- **F5 стрелки** (без миксинов): `AncientRangedGoal` (приоритет 4, от `RangedAIHandler` при появлении) — скелеты с арбалетом и утопленники/мурки с луком стреляют по vanilla-паттерну; goal самоотключается по держанному оружию. Миксин-файлы и `ancientcivilization.mixins.json` удалены.
- **F7 варден** (`warden`): члены отряда Древнего города — приоритет 1 по вардену (равно приоритету игрока, ближайший выигрывает).
- **F8 странники** (`wanderers`): руины порталов (4 ID), один ролл на инстанс (SavedData, навсегда), **10% на каждый портал**, каст-ролл только soldier/hero (60/40), спавн у портала.
- **F9 буст**: timer boosted рядом со структурой + тег `boosted`.
- **F10 марш** (`march`): равнинные/пустынные отряды в 128 блоках от деревни (`minecraft:village`) спавнятся на кольце 64–96 и маршируют к деревне (`SquadMarchGoal`, priority 1, отключается по прибытию).
- **Бафф в руинах** (`ruinBuff`): **только численность** — отряды, спавнувшиеся у руин (`ruinStructures`), спавнятся ×`countMultiplier` (1.5) мобо (ростер крутится, тег `ruin_buff`).
- **Эффекты на мобов** (`effects`): произвольные эффекты (любой ID эффекта) с фильтрами entities/roles/squads, шансом, длительностью и амплифайером.
- **Конфигурируемость**: все секции мода (localDifficulty, squads, reroll, armorRemap, hpBuff, wanderers, ruinBuff, effects, march, warden, animation) — в `config/ancient_civilization.json`; ID предметов/мобов/структур/биомов задаются пользователем, отсутствующие ID пропускаются со warn (мод работает без зависимостей).

Остались только доработки: тесты в игре, подгонка чисел.

*Рабочая копия мода в этой сессии: `ancient_civilization/`; финальная ветка `ancient_civilization` — при переносе в репозиторий сборки.*
