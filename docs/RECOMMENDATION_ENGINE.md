# Nutrient requirement engine and knowledge base (Milestone 4)

> **Prototype.** The engine applies general (blanket) fertilizer recommendations from the public documents
> cited below, plus explicitly labelled assumptions. Its output is not a site-specific prescription and must be
> checked against the state package of practices, a soil-testing laboratory and a qualified agronomist.

The engine is **deterministic Java** in the Spring Boot backend (`com.agrioptima.engine`). It has no ML, no
optimisation, no weather and no LLM, and does no I/O: the same input always gives the same output. Its result,
`requirementForOptimizer`, is the input the SciPy optimizer will receive in Milestone 5.

```
Field (crop, stage, area, irrigation, sowing date) + latest soil test + recorded applications
   │
   ├─ 1. profile      knowledge base -> general dose N / P2O5 / K2O (kg/ha) for crop + condition
   ├─ 2. soil test    available N, P, K -> LOW / MEDIUM / HIGH -> factor x1.25 / x1.00 / x0.75
   ├─ 3. schedule     share of each nutrient due up to and including the current stage
   ├─ 4. previous use kg product x grade % -> kg nutrient ÷ field ha, applications in the season window
   └─ 5. requirement  dueNow = max(0, adjusted x shareDue − applied)          -> requirementForOptimizer
                      remaining = max(0, adjusted − applied); excess = max(0, applied − adjusted)
```

## 1. Knowledge base

File: `backend/src/main/resources/knowledge/nutrient-kb-v1.json` (id `agrioptima-nutrient-kb`, **version 1.0.0**,
status `PROTOTYPE`). It is loaded once at startup by `KnowledgeBaseLoader`. The application **refuses to start**
if the file is invalid (unknown or missing keys, splits not summing to exactly 1, unknown source ids, schedules
or statuses, unordered thresholds, negative doses) or if a crop or stage code it uses is missing from the database
(`KnowledgeBaseConsistencyCheck`). The file is served read-only at `GET /api/knowledge-base`.

Structure:

```
id, version, releasedOn, status, disclaimer, statusLegend, units
sources[]            id, citation, url, accessed, usedFor
soilTest             basis (ELEMENTAL), ratings{N,P,K}, adjustmentFactors, organicCarbonPercent, ph, maxAgeDays
previousApplications windowDaysBeforeSowing, fallbackLookbackDays, carryOver
crops[]              code, defaultProfile, rainfedProfile, lateSownProfile, lateSowingAfter
  profiles[]         code, description, target{n,p2o5,k2o}, schedule, status, sourceId
  schedules[]        code, splits[] {stage, n, p2o5, k2o (exact fractions "1/3"), timing, status}, sourceId, note
```

Every value has a **status**:

| Status | Meaning |
|---|---|
| `REFERENCED` | Copied from the cited source (text retrieved and read on 2026-09-27) |
| `MAPPING_ASSUMPTION` | From the source, but its timing was mapped by us onto this app's stage list |
| `PROTOTYPE_ASSUMPTION` | Not from a verified source; chosen for the prototype |
| `DERIVED` | Computed from physical constants |

**Units:** requirements are in kg/ha of **N, P₂O₅, K₂O** (fertilizer-bag convention). Soil tests are available
N, P, K in kg/ha on the **elemental** basis. Previous applications are kg of **product** for the whole field.
Conversions: P→P₂O₅ ×2.2914, K→K₂O ×1.2046, computed from IUPAC atomic weights (`NutrientUnits`).

## 2. Sources actually used

| Id | Document | Used for |
|---|---|---|
| `IIWBR_EB52` | ICAR-DWR (now IIWBR) *Wheat Cultivation in India – Pocket Guide*, Extension Bulletin 52 ([PDF](https://iiwbr.org.in/wp-content/uploads/2023/08/EB-52-Wheat-Cultivation-in-India-Pocket-Guide.pdf)) | All wheat doses and the wheat split |
| `NRRI_CRP_PAGE` | ICAR-NRRI article on the ICAR-IIRR CRP site ([link](https://icar-iirr.org/CRP/index.php/crparticles?view=article&id=24%3Arice-nrri&catid=9&showall=1)) | Rice default dose 120:60:40 ("100 % of recommended inorganic fertilizer"; context not stated) |
| `CRRI_FAQ` | ICAR-CRRI FAQ ([link](https://icar-crri.in/faq/)) | Rice doses for upland, lowland kharif, rabi, hybrid |
| `TNAU_RICE_TRANSPLANTED` | TNAU Agritech, transplanted rice nutrient management ([link](https://agritech.tnau.ac.in/agriculture/agri_cropproduction_cereals_rice_tranpudlow_mainfield_nutrient_mgmt_inorganic.html)) | Rice split: N and K in four equal splits, P basal; Tamil Nadu doses |
| `TN_CPG_MAIZE` | Tamil Nadu Crop Production Guide, Maize ([PDF](https://tnagriculture.in/dashboard/CPG/02_%20Maize.pdf)) | All maize doses and splits |
| `DESHMUKH_2022_RATINGS` | Deshmukh et al. 2022, *Pharma Innovation J.* 11(12):1987-1990, Table 2 (citing Muhr et al. 1965 and Arora 2002) ([PDF](https://www.thepharmajournal.com/archives/2022/vol11issue12/PartY/11-12-312-270.pdf)) | Soil fertility class limits, OC classes, pH classes |
| `IUPAC_ATOMIC_WEIGHTS` | IUPAC standard atomic weights | P/K oxide conversion factors |

Searched but **not used**: the ICAR-IIMR maize page (the host was unreachable) and a source for the exact
±25 % soil-test adjustment (the page returned HTTP 403). Muhr et al. (1965) itself was not accessible; its
limits are taken from the secondary table above.

## 3. Values in version 1.0.0

### Crop profiles (general dose, kg/ha N : P₂O₅ : K₂O) — all `REFERENCED`

| Crop | Profile | Dose | Condition / source | Selected automatically when |
|---|---|---|---|---|
| Wheat | `IRRIGATED_TIMELY_SOWN` | 120 : 60 : 40 | NHZ, CZ, PZ, SHZ (IIWBR) | default |
| Wheat | `IRRIGATED_TIMELY_SOWN_NWPZ_NEPZ` | 150 : 60 : 40 | NWPZ / NEPZ incl. Punjab, Haryana, UP, Bihar (IIWBR) | only on request |
| Wheat | `IRRIGATED_LATE_SOWN` | 90 : 60 : 40 | sown after 25 Nov, NHZ/CZ/PZ/SHZ (IIWBR) | irrigated + sown after 25 Nov (or Jan–Jun) |
| Wheat | `IRRIGATED_LATE_SOWN_NWPZ_NEPZ` | 120 : 60 : 40 | late sown NWPZ/NEPZ (IIWBR) | only on request |
| Wheat | `RAINFED` | 60 : 30 : 20 | all zones (IIWBR) | field irrigation = RAINFED |
| Rice | `NRRI_GENERAL` | 120 : 60 : 40 | ICAR-NRRI | default |
| Rice | `CRRI_LOWLAND_KHARIF_IRRIGATED` | 60 : 30 : 30 | ICAR-CRRI FAQ | only on request |
| Rice | `CRRI_RABI` | 80 : 40 : 40 | ICAR-CRRI FAQ | only on request |
| Rice | `CRRI_HYBRID` | 100 : 60 : 60 | ICAR-CRRI FAQ | only on request |
| Rice | `CRRI_RAINFED_UPLAND` | 40 : 20 : 20 | ICAR-CRRI FAQ | field irrigation = RAINFED |
| Rice | `TNAU_TRANSPLANTED_TN` | 150 : 50 : 50 | Tamil Nadu (TNAU) | only on request |
| Rice | `TNAU_HYBRID_TN` | 175 : 60 : 60 | Tamil Nadu (TNAU) | only on request |
| Maize | `TN_IRRIGATED_VARIETY` | 135 : 62.5 : 50 | Tamil Nadu CPG | default |
| Maize | `TN_IRRIGATED_HYBRID` | 250 : 75 : 75 | Tamil Nadu CPG (high vs other states) | only on request |
| Maize | `TN_RAINFED_ALFISOL` | 60 : 30 : 30 | Tamil Nadu CPG | field irrigation = RAINFED |
| Maize | `TN_RAINFED_VERTISOL` | 40 : 20 : 0 | Tamil Nadu CPG | only on request |

The sources disagree widely: rice ranges from 40 to 175 kg N/ha depending on ecosystem, variety and state.
Defaults are one documented choice, not "the" answer, so the API returns `availableProfiles` and accepts
`?profile=`.

### Split schedules (share of the season dose per stage)

| Schedule | Stage → N / P₂O₅ / K₂O | Status |
|---|---|---|
| `WHEAT_IRRIGATED` | SOWING 1/3 / 1 / 1 · CRI 1/3 / 0 / 0 · TILLERING 1/3 / 0 / 0 | IIWBR: "1/3 N and full P&K as basal … remaining N in two equal splits at first and second irrigation". The first irrigation is CRI (21 DAS) per the source; mapping the **second irrigation → TILLERING** (first node ≈ 45 DAS) is a `MAPPING_ASSUMPTION` |
| `WHEAT_RAINFED` | SOWING 1 / 1 / 1 | `REFERENCED` |
| `RICE_FOUR_SPLITS` | ESTABLISHMENT 1/4 / 1 / 1/4 · TILLERING 1/4 / 0 / 1/4 · PANICLE_INITIATION 1/4 / 0 / 1/4 · FLOWERING (heading) 1/4 / 0 / 1/4 | TNAU pattern `REFERENCED`. Applying it to NRRI/CRRI doses and to upland rice is a `PROTOTYPE_ASSUMPTION` |
| `MAIZE_IRRIGATED` | SOWING 1/4 / 1 / 1 · KNEE_HIGH 1/2 / 0 / 0 · TASSELING 1/4 / 0 / 0 | Days 25 and 45 after sowing mapped to KNEE_HIGH and TASSELING: `MAPPING_ASSUMPTION` |
| `MAIZE_RAINFED` | SOWING 1/2 / 1 / 1 · TASSELING 1/2 / 0 / 0 | `REFERENCED` |

### Soil test

| Item | Value | Status |
|---|---|---|
| Available N class | LOW < 280 ≤ MEDIUM ≤ 560 < HIGH (kg/ha) | `REFERENCED` |
| Available P class | LOW < 10 ≤ MEDIUM ≤ 25 < HIGH (kg/ha, read as elemental P) | `REFERENCED` (basis: `PROTOTYPE_ASSUMPTION`) |
| Available K class | LOW < 118 ≤ MEDIUM ≤ 280 < HIGH (kg/ha, read as elemental K) | `REFERENCED` (other tables use 108) |
| Organic carbon class | < 0.5 / 0.5–1.0 / > 1.0 % (reported only) | `REFERENCED` |
| Dose factor by class | LOW ×1.25, MEDIUM ×1.00, HIGH ×0.75 | **`PROTOTYPE_ASSUMPTION`** |
| No soil test | class `ASSUMED_MEDIUM`, factor 1.00, warning | `PROTOTYPE_ASSUMPTION` |
| pH | warning if < 5.5 or > 8.5; does not change numbers | `REFERENCED` classes |
| Stale test | warning if older than 1095 days | `PROTOTYPE_ASSUMPTION` |

The ±25 % factor is described in secondary literature on Indian soil-testing practice (reported as 25–30 %),
but no primary source could be retrieved. It is **not** a soil-test crop response (STCR) equation. STCR
coefficients are location-specific and are not used.

### Previous applications

Counted if applied between (sowing date − 30 days) and today, or within the last 150 days when the field has no
sowing date (`PROTOTYPE_ASSUMPTION`). Carry-over from earlier crops, manures and residues is **not** credited.

## 4. API (all JWT, owner-scoped; foreign fields → 404)

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/fields/{fieldId}/nutrient-requirement[?profile=CODE]` | Calculate (not persisted) |
| GET | `/api/knowledge-base` | The knowledge base in use |
| GET, POST | `/api/fields/{fieldId}/applications` | Previous fertilizer usage (kg product per field) |
| DELETE | `/api/applications/{applicationId}` | Remove an application |

Errors are RFC 7807: 400 when the field has no crop or stage, the profile is unknown, the stage belongs to another
crop, or the quantity/date is invalid. Response: `nutrients[]` (general dose, class, factor, adjusted,
`cumulativeShareDue`, applied, remaining, `dueNow`, excess, field totals), `requirementForOptimizer`
{`kgPerHa`, `fieldKg`, `areaHa`, `stageCode`}, `schedule[]` (PAST/CURRENT/UPCOMING), `warnings[]`,
`assumptions[]`, `knowledgeBase` {id, version, status}, `disclaimer`.

## 5. Calculation examples (actual API output, dev profile, 2026-09-27)

| # | Scenario | Profile | Classes | Applied (kg/ha) | **Due now** N / P₂O₅ / K₂O (kg/ha) | Field kg |
|---|---|---|---|---|---|---|
| 1 | Wheat, CRI, 2 ha, soil N300 P15 K200, basal applied (260.87 kg DAP + 71.83 urea + 133.33 MOP) | IRRIGATED_TIMELY_SOWN | M/M/M | 40 / 60 / 40 | **40 / 0 / 0** | 80 / 0 / 0 |
| 2 | Same, `?profile=IRRIGATED_TIMELY_SOWN_NWPZ_NEPZ` | NWPZ_NEPZ (150) | M/M/M | 40 / 60 / 40 | **60 / 0 / 0** | 120 / 0 / 0 |
| 3 | Wheat, CRI, 2 ha, soil N200 P5 K100, pH 5.2, nothing applied | IRRIGATED_TIMELY_SOWN | L/L/L (×1.25) | 0 | **100 / 75 / 50** + acid-pH and "P/K window passed" warnings | 200 / 150 / 100 |
| 4 | Rice, panicle initiation, 0.5 ha, soil N600 P30 K300 | NRRI_GENERAL | H/H/H (×0.75) | 0 | **67.5 / 45 / 22.5** | 33.75 / 22.5 / 11.25 |
| 5 | Maize, sowing, 1.2 ha, rainfed, no soil test | TN_RAINFED_ALFISOL | assumed medium | 0 | **30 / 30 / 30** + "no soil test" warning | 36 / 36 / 36 |
| 6 | Wheat, tillering, 1 ha, 400 kg urea applied | IRRIGATED_TIMELY_SOWN | M/M/M | 184 / 0 / 0 | **0 / 60 / 40** + "N exceeds target by 64" warning | 0 / 60 / 40 |

Worked arithmetic for #4: adjusted N = 120 × 0.75 = 90; share due by panicle initiation = 1/4 + 1/4 + 1/4 = 0.75;
due = 90 × 0.75 − 0 = 67.5 kg/ha; field = 67.5 × 0.5 = 33.75 kg.

## 6. Limitations

- Blanket recommendations with a coarse ±25 % soil adjustment, not STCR, targeted-yield or site-specific
  nutrient management. Zone and ecosystem are not inferred (the farm has no state/zone field), so the defaults
  may be wrong for a location. For example, Bihar/UP wheat should use the NWPZ/NEPZ profile (150 kg N).
- Maize values come from a Tamil Nadu guide; national ICAR-IIMR values could not be retrieved.
- Late catch-up: nutrients missed at earlier stages are still counted as due, with a warning. The engine does not
  decide whether a late application is agronomically worthwhile.
- No credit for residual soil nutrients, FYM, legumes or irrigation water. No S, Zn or other micronutrients.
  pH and organic carbon do not change the numbers.
- Stage granularity is the seeded 5–6 stages per crop, and some source timings are mapped onto them.
