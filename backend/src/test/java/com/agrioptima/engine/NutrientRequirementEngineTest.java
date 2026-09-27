package com.agrioptima.engine;

import com.agrioptima.engine.NutrientRequirement.NutrientLine;
import com.agrioptima.engine.NutrientRequirement.ScheduleEntry;
import com.agrioptima.engine.NutrientRequirement.StagePosition;
import com.agrioptima.engine.RequirementInput.AppliedFertilizer;
import com.agrioptima.engine.RequirementInput.SoilTestInput;
import com.agrioptima.engine.RequirementInput.StageRef;
import com.agrioptima.engine.knowledge.KnowledgeBase;
import com.agrioptima.engine.knowledge.KnowledgeBaseLoaderTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/** Pure unit tests of the deterministic engine against the shipped knowledge base (no Spring, no DB). */
class NutrientRequirementEngineTest {

    static final KnowledgeBase KB = KnowledgeBaseLoaderTest.shipped();
    static final NutrientRequirementEngine ENGINE = new NutrientRequirementEngine(KB);

    // Stage lists exactly as seeded by Flyway V2.
    static final List<StageRef> WHEAT = List.of(new StageRef("SOWING", "Sowing", 1),
            new StageRef("CRI", "Crown root initiation", 2), new StageRef("TILLERING", "Tillering", 3),
            new StageRef("JOINTING", "Jointing", 4), new StageRef("FLOWERING", "Heading / flowering", 5),
            new StageRef("MATURITY", "Grain filling / maturity", 6));
    static final List<StageRef> RICE = List.of(new StageRef("ESTABLISHMENT", "Sowing / transplanting", 1),
            new StageRef("TILLERING", "Active tillering", 2), new StageRef("PANICLE_INITIATION", "Panicle initiation", 3),
            new StageRef("FLOWERING", "Heading / flowering", 4), new StageRef("MATURITY", "Grain filling / maturity", 5));
    static final List<StageRef> MAIZE = List.of(new StageRef("SOWING", "Sowing / emergence", 1),
            new StageRef("KNEE_HIGH", "Knee-high", 2), new StageRef("TASSELING", "Tasseling", 3),
            new StageRef("SILKING", "Silking", 4), new StageRef("MATURITY", "Grain filling / maturity", 5));

    static final LocalDate SOWN = LocalDate.of(2026, 11, 10);
    static final LocalDate TODAY = LocalDate.of(2026, 11, 12);
    /** Medium in N (280-560), P (10-25) and K (118-280). */
    static final SoilTestInput MEDIUM_SOIL = soil(300, 15, 200, 7.2);

    static SoilTestInput soil(double n, double p, double k, double ph) {
        return new SoilTestInput(1L, LocalDate.of(2026, 10, 1), n, p, k, ph, 0.6);
    }

    static RequirementInput wheat(String stage, double areaHa, SoilTestInput soil, List<AppliedFertilizer> apps) {
        return new RequirementInput("WHEAT", stage, null, areaHa, false, SOWN, TODAY, soil, apps, WHEAT);
    }

    static AppliedFertilizer applied(LocalDate on, double n, double p2o5, double k2o) {
        return new AppliedFertilizer(1L, "TEST", on, new NutrientAmounts(n, p2o5, k2o));
    }

    static void assertAmounts(NutrientAmounts actual, double n, double p2o5, double k2o) {
        assertThat(actual.n()).as("N").isCloseTo(n, within(1e-9));
        assertThat(actual.p2o5()).as("P2O5").isCloseTo(p2o5, within(1e-9));
        assertThat(actual.k2o()).as("K2O").isCloseTo(k2o, within(1e-9));
    }

    static NutrientLine line(NutrientRequirement r, Nutrient n) {
        return r.nutrients().stream().filter(l -> l.nutrient() == n).findFirst().orElseThrow();
    }

    @Nested
    class Crops {

        @Test
        void wheatAtSowingOnMediumSoilGetsBasalThirdOfNAndAllPK() {
            NutrientRequirement r = ENGINE.calculate(wheat("SOWING", 1, MEDIUM_SOIL, List.of()));
            assertThat(r.profile().code()).isEqualTo("IRRIGATED_TIMELY_SOWN");
            assertAmounts(r.dueNowKgHa(), 40, 60, 40);           // 120 x 1/3, 60 x 1, 40 x 1
            assertAmounts(r.remainingSeasonKgHa(), 120, 60, 40);
            assertThat(r.nutrients()).allSatisfy(l -> assertThat(l.soilClass()).isEqualTo(SoilFertilityClass.MEDIUM));
        }

        @Test
        void riceAtPanicleInitiationGetsThreeQuartersOfNAndKCumulatively() {
            var in = new RequirementInput("RICE", "PANICLE_INITIATION", null, 1, false, SOWN, TODAY, MEDIUM_SOIL,
                    List.of(), RICE);
            NutrientRequirement r = ENGINE.calculate(in);
            assertThat(r.profile().code()).isEqualTo("NRRI_GENERAL");
            assertAmounts(r.dueNowKgHa(), 90, 60, 30);           // 120 x 3/4, 60 x 1, 40 x 3/4
        }

        @Test
        void maizeAtKneeHighUsesTamilNaduVarietyProfile() {
            var in = new RequirementInput("MAIZE", "KNEE_HIGH", null, 1, false, SOWN, TODAY, MEDIUM_SOIL,
                    List.of(), MAIZE);
            NutrientRequirement r = ENGINE.calculate(in);
            assertThat(r.profile().code()).isEqualTo("TN_IRRIGATED_VARIETY");
            assertAmounts(r.dueNowKgHa(), 101.25, 62.5, 50);    // 135 x (1/4 + 1/2)
        }

        @Test
        void unknownCropIsRejected() {
            var in = new RequirementInput("SUGARCANE", "SOWING", null, 1, false, SOWN, TODAY, null, List.of(), WHEAT);
            assertThatThrownBy(() -> ENGINE.calculate(in)).isInstanceOf(EngineInputException.class)
                    .hasMessageContaining("No nutrient knowledge for crop SUGARCANE");
        }
    }

    @Nested
    class Stages {

        @Test
        void wheatCriAfterBasalWasAppliedNeedsOnlyTheSecondNSplit() {
            var apps = List.of(applied(SOWN, 40, 60, 40));
            NutrientRequirement r = ENGINE.calculate(wheat("CRI", 1, MEDIUM_SOIL, apps));
            assertAmounts(r.dueNowKgHa(), 40, 0, 0);
            assertAmounts(r.remainingSeasonKgHa(), 80, 0, 0);
            assertThat(r.warnings()).noneMatch(w -> w.contains("window has passed"));
        }

        @Test
        void missedBasalIsCaughtUpWithAWarningThatTheWindowPassed() {
            NutrientRequirement r = ENGINE.calculate(wheat("CRI", 1, MEDIUM_SOIL, List.of()));
            assertAmounts(r.dueNowKgHa(), 80, 60, 40);
            assertThat(r.warnings()).anyMatch(w -> w.contains("of P2O5") && w.contains("window has passed"))
                    .anyMatch(w -> w.contains("of K2O") && w.contains("window has passed"))
                    .noneMatch(w -> w.contains("of N scheduled"));
        }

        @Test
        void jointingAfterAllSplitsWereAppliedNeedsNothing() {
            var apps = List.of(applied(SOWN, 40, 60, 40), applied(SOWN.plusDays(21), 40, 0, 0),
                    applied(SOWN.plusDays(45), 40, 0, 0));
            var in = new RequirementInput("WHEAT", "JOINTING", null, 1, false, SOWN, SOWN.plusDays(60), MEDIUM_SOIL,
                    apps, WHEAT);
            NutrientRequirement r = ENGINE.calculate(in);
            assertAmounts(r.dueNowKgHa(), 0, 0, 0);
            assertAmounts(r.remainingSeasonKgHa(), 0, 0, 0);
        }

        @Test
        void schedulePositionsAndPlannedAmountsAddUpToTheSeasonTarget() {
            NutrientRequirement r = ENGINE.calculate(wheat("CRI", 1, MEDIUM_SOIL, List.of()));
            assertThat(r.schedule()).extracting(ScheduleEntry::stageCode).containsExactly("SOWING", "CRI", "TILLERING");
            assertThat(r.schedule()).extracting(ScheduleEntry::position)
                    .containsExactly(StagePosition.PAST, StagePosition.CURRENT, StagePosition.UPCOMING);
            NutrientAmounts total = r.schedule().stream().map(ScheduleEntry::plannedKgHa)
                    .reduce(NutrientAmounts.ZERO, NutrientAmounts::plus);
            assertAmounts(total, 120, 60, 40);
        }

        @Test
        void stageOfAnotherCropIsRejected() {
            assertThatThrownBy(() -> ENGINE.calculate(wheat("PANICLE_INITIATION", 1, MEDIUM_SOIL, List.of())))
                    .isInstanceOf(EngineInputException.class).hasMessageContaining("not a stage of crop WHEAT");
        }
    }

    @Nested
    class SoilValues {

        @Test
        void lowSoilIncreasesAndHighSoilDecreasesEachNutrientBy25Percent() {
            NutrientRequirement low = ENGINE.calculate(wheat("SOWING", 1, soil(200, 5, 100, 7), List.of()));
            assertAmounts(low.dueNowKgHa(), 50, 75, 50);
            assertThat(line(low, Nutrient.N).adjustedSeasonTargetKgHa()).isEqualTo(150);

            NutrientRequirement high = ENGINE.calculate(wheat("SOWING", 1, soil(600, 30, 300, 7), List.of()));
            assertAmounts(high.dueNowKgHa(), 30, 45, 30);
        }

        @Test
        void eachNutrientIsRatedIndependently() {
            NutrientRequirement r = ENGINE.calculate(wheat("SOWING", 1, soil(200, 15, 300, 7), List.of()));
            assertThat(line(r, Nutrient.N).soilClass()).isEqualTo(SoilFertilityClass.LOW);
            assertThat(line(r, Nutrient.P2O5).soilClass()).isEqualTo(SoilFertilityClass.MEDIUM);
            assertThat(line(r, Nutrient.K2O).soilClass()).isEqualTo(SoilFertilityClass.HIGH);
            assertAmounts(r.dueNowKgHa(), 50, 60, 30);
        }

        @ParameterizedTest(name = "{0} = {1} -> {2}")
        @CsvSource({
                "N, 279.99, LOW", "N, 280, MEDIUM", "N, 560, MEDIUM", "N, 560.01, HIGH", "N, 0, LOW",
                "P, 9.99, LOW", "P, 10, MEDIUM", "P, 25, MEDIUM", "P, 25.01, HIGH",
                "K, 117.99, LOW", "K, 118, MEDIUM", "K, 280, MEDIUM", "K, 280.01, HIGH"
        })
        void ratingBoundariesBelongToMedium(String element, double value, SoilFertilityClass expected) {
            assertThat(NutrientRequirementEngine.classify(value, KB.soilTest().ratings().get(element)))
                    .isEqualTo(expected);
        }

        @Test
        void missingSoilTestUsesTheGeneralRecommendationAndSaysSo() {
            NutrientRequirement r = ENGINE.calculate(wheat("SOWING", 1, null, List.of()));
            assertThat(r.soil().soilTestUsed()).isFalse();
            assertThat(r.nutrients()).allSatisfy(l -> {
                assertThat(l.soilClass()).isEqualTo(SoilFertilityClass.ASSUMED_MEDIUM);
                assertThat(l.soilAdjustmentFactor()).isEqualTo(1.0);
            });
            assertAmounts(r.dueNowKgHa(), 40, 60, 40);
            assertThat(r.warnings()).anyMatch(w -> w.startsWith("No soil test on record"));
        }

        @Test
        void soilTestIsReportedInOxideEquivalentsToo() {
            NutrientRequirement r = ENGINE.calculate(wheat("SOWING", 1, soil(300, 10, 100, 7), List.of()));
            assertThat(r.soil().availableP2o5EquivalentKgHa()).isCloseTo(22.914, within(1e-3));
            assertThat(r.soil().availableK2oEquivalentKgHa()).isCloseTo(120.46, within(1e-2));
        }

        @Test
        void phExtremesAndStaleTestsWarnButDoNotChangeTheNumbers() {
            NutrientRequirement acid = ENGINE.calculate(wheat("SOWING", 1, soil(300, 15, 200, 5.0), List.of()));
            NutrientRequirement alkaline = ENGINE.calculate(wheat("SOWING", 1, soil(300, 15, 200, 9.0), List.of()));
            NutrientRequirement neutral = ENGINE.calculate(wheat("SOWING", 1, soil(300, 15, 200, 7.0), List.of()));
            assertThat(acid.warnings()).anyMatch(w -> w.contains("strongly acidic"));
            assertThat(alkaline.warnings()).anyMatch(w -> w.contains("strongly alkaline"));
            assertThat(neutral.warnings()).noneMatch(w -> w.contains("pH"));
            assertThat(acid.dueNowKgHa()).isEqualTo(neutral.dueNowKgHa());

            var old = new SoilTestInput(1L, TODAY.minusDays(1096), 300, 15, 200, 7, null);
            assertThat(ENGINE.calculate(wheat("SOWING", 1, old, List.of())).warnings())
                    .anyMatch(w -> w.contains("1096 days old"));
            var edge = new SoilTestInput(1L, TODAY.minusDays(1095), 300, 15, 200, 7, null);
            assertThat(ENGINE.calculate(wheat("SOWING", 1, edge, List.of())).warnings())
                    .noneMatch(w -> w.contains("days old"));
        }
    }

    @Nested
    class FieldSizes {

        @ParameterizedTest
        @ValueSource(doubles = {0.01, 0.5, 2.5, 100})
        void perHectareRequirementIsIndependentOfAreaAndFieldTotalsScale(double area) {
            NutrientRequirement r = ENGINE.calculate(wheat("SOWING", area, MEDIUM_SOIL, List.of()));
            assertAmounts(r.dueNowKgHa(), 40, 60, 40);
            assertThat(r.dueNowFieldKg().n()).isCloseTo(40 * area, within(1e-9));
            assertThat(r.dueNowFieldKg().p2o5()).isCloseTo(60 * area, within(1e-9));
            assertThat(line(r, Nutrient.K2O).dueNowFieldKg()).isCloseTo(40 * area, within(1e-9));
        }

        @Test
        void previousApplicationIsConvertedPerHectareUsingTheFieldArea() {
            // 80 kg N on a 2 ha field = 40 kg N/ha, exactly the basal third.
            var apps = List.of(applied(SOWN, 80, 120, 80));
            NutrientRequirement r = ENGINE.calculate(wheat("SOWING", 2, MEDIUM_SOIL, apps));
            assertAmounts(r.previousUsage().appliedKgHa(), 40, 60, 40);
            assertAmounts(r.dueNowKgHa(), 0, 0, 0);
            // Same product on a 4 ha field covers only half.
            NutrientRequirement bigger = ENGINE.calculate(wheat("SOWING", 4, MEDIUM_SOIL, apps));
            assertAmounts(bigger.dueNowKgHa(), 20, 30, 20);
        }

        @ParameterizedTest
        @ValueSource(doubles = {0, -1, Double.NaN})
        void nonPositiveAreaIsRejected(double area) {
            assertThatThrownBy(() -> ENGINE.calculate(wheat("SOWING", area, MEDIUM_SOIL, List.of())))
                    .isInstanceOf(EngineInputException.class).hasMessageContaining("positive number of hectares");
        }
    }

    @Nested
    class PreviousApplications {

        @Test
        void onlyApplicationsInsideTheSeasonWindowCount() {
            var apps = List.of(
                    applied(SOWN.minusDays(31), 100, 0, 0),   // before window (sowing - 30 d)
                    applied(SOWN.minusDays(30), 10, 0, 0),    // first day of window
                    applied(SOWN, 5, 0, 0),
                    applied(TODAY.plusDays(1), 100, 0, 0));   // future
            NutrientRequirement r = ENGINE.calculate(wheat("SOWING", 1, MEDIUM_SOIL, apps));
            assertThat(r.previousUsage().applicationsCounted()).isEqualTo(2);
            assertThat(r.previousUsage().applicationsOutsideWindow()).isEqualTo(2);
            assertAmounts(r.previousUsage().appliedKgHa(), 15, 0, 0);
            assertAmounts(r.dueNowKgHa(), 25, 60, 40);
        }

        @Test
        void withoutSowingDateTheFallbackLookbackIsUsedAndFlagged() {
            var apps = List.of(applied(TODAY.minusDays(150), 10, 0, 0), applied(TODAY.minusDays(151), 10, 0, 0));
            var in = new RequirementInput("WHEAT", "SOWING", null, 1, false, null, TODAY, MEDIUM_SOIL, apps, WHEAT);
            NutrientRequirement r = ENGINE.calculate(in);
            assertThat(r.previousUsage().windowStart()).isEqualTo(TODAY.minusDays(150));
            assertThat(r.previousUsage().applicationsCounted()).isEqualTo(1);
            assertThat(r.warnings()).anyMatch(w -> w.contains("no sowing date"));
        }

        @Test
        void overApplicationIsReportedAsExcessAndNothingMoreIsDue() {
            var apps = List.of(applied(SOWN, 200, 60, 40));
            NutrientRequirement r = ENGINE.calculate(wheat("CRI", 1, MEDIUM_SOIL, apps));
            assertThat(line(r, Nutrient.N).excessAppliedKgHa()).isCloseTo(80, within(1e-9));
            assertAmounts(r.dueNowKgHa(), 0, 0, 0);
            assertThat(r.warnings()).anyMatch(w -> w.startsWith("N already applied")
                    && w.contains("(120.0 kg/ha) by 80.0 kg/ha"));
        }

        @Test
        void negativeAppliedNutrientsAreRejected() {
            var apps = List.of(applied(SOWN, -1, 0, 0));
            assertThatThrownBy(() -> ENGINE.calculate(wheat("SOWING", 1, MEDIUM_SOIL, apps)))
                    .isInstanceOf(EngineInputException.class);
        }
    }

    @Nested
    class Profiles {

        @Test
        void rainfedFieldUsesRainfedProfileWithEverythingAtSowing() {
            var in = new RequirementInput("WHEAT", "SOWING", null, 1, true, SOWN, TODAY, MEDIUM_SOIL, List.of(), WHEAT);
            NutrientRequirement r = ENGINE.calculate(in);
            assertThat(r.profile().code()).isEqualTo("RAINFED");
            assertThat(r.profile().selectedBecause()).isEqualTo("field is rainfed");
            assertAmounts(r.dueNowKgHa(), 60, 30, 20);
        }

        @ParameterizedTest(name = "sown {0} -> {1}")
        @CsvSource({"2026-11-25, IRRIGATED_TIMELY_SOWN", "2026-11-26, IRRIGATED_LATE_SOWN",
                "2026-12-10, IRRIGATED_LATE_SOWN", "2027-01-05, IRRIGATED_LATE_SOWN", "2026-10-30, IRRIGATED_TIMELY_SOWN"})
        void wheatSownAfter25NovemberUsesTheLateSownProfile(LocalDate sown, String expected) {
            var in = new RequirementInput("WHEAT", "SOWING", null, 1, false, sown, sown.plusDays(1), MEDIUM_SOIL,
                    List.of(), WHEAT);
            assertThat(ENGINE.calculate(in).profile().code()).isEqualTo(expected);
        }

        @Test
        void explicitProfileOverridesTheDefault() {
            var in = new RequirementInput("WHEAT", "SOWING", "IRRIGATED_TIMELY_SOWN_NWPZ_NEPZ", 1, false, SOWN, TODAY,
                    MEDIUM_SOIL, List.of(), WHEAT);
            NutrientRequirement r = ENGINE.calculate(in);
            assertAmounts(r.dueNowKgHa(), 50, 60, 40);          // 150 x 1/3
            assertThat(r.profile().sourceId()).isEqualTo("IIWBR_EB52");
            assertThat(r.availableProfiles()).contains("RAINFED", "IRRIGATED_LATE_SOWN");
        }

        @Test
        void unknownProfileIsRejectedWithTheAvailableOnes() {
            var in = new RequirementInput("RICE", "TILLERING", "NRRI_TURBO", 1, false, SOWN, TODAY, MEDIUM_SOIL,
                    List.of(), RICE);
            assertThatThrownBy(() -> ENGINE.calculate(in)).isInstanceOf(EngineInputException.class)
                    .hasMessageContaining("Unknown profile NRRI_TURBO").hasMessageContaining("NRRI_GENERAL");
        }
    }

    @Nested
    class InvalidInputs {

        @Test
        void missingCropStageOrDateIsRejected() {
            assertThatThrownBy(() -> ENGINE.calculate(new RequirementInput(null, "SOWING", null, 1, false, SOWN, TODAY,
                    null, List.of(), WHEAT))).isInstanceOf(EngineInputException.class).hasMessageContaining("no crop");
            assertThatThrownBy(() -> ENGINE.calculate(new RequirementInput("WHEAT", " ", null, 1, false, SOWN, TODAY,
                    null, List.of(), WHEAT))).isInstanceOf(EngineInputException.class).hasMessageContaining("no growth stage");
            assertThatThrownBy(() -> ENGINE.calculate(new RequirementInput("WHEAT", "SOWING", null, 1, false, SOWN, null,
                    null, List.of(), WHEAT))).isInstanceOf(EngineInputException.class);
        }

        @Test
        void invalidSoilValuesAreRejected() {
            for (SoilTestInput bad : List.of(soil(-1, 15, 200, 7), soil(300, Double.NaN, 200, 7), soil(300, 15, 200, 15),
                    new SoilTestInput(1L, TODAY.plusDays(1), 300, 15, 200, 7, null),
                    new SoilTestInput(1L, TODAY, 300, 15, 200, 7, 120.0))) {
                assertThatThrownBy(() -> ENGINE.calculate(wheat("SOWING", 1, bad, List.of())))
                        .isInstanceOf(EngineInputException.class);
            }
        }
    }

    @Test
    void sameInputAlwaysGivesTheSameOutput() {
        var in = wheat("CRI", 3.7, soil(250, 30, 150, 6.8), List.of(applied(SOWN, 50, 40, 0)));
        assertThat(ENGINE.calculate(in)).isEqualTo(ENGINE.calculate(in));
    }

    @Test
    void outputCarriesKnowledgeBaseVersionAndAssumptions() {
        NutrientRequirement r = ENGINE.calculate(wheat("CRI", 1, MEDIUM_SOIL, List.of()));
        assertThat(r.knowledgeBaseVersion()).isEqualTo("1.0.0");
        assertThat(r.assumptions()).anyMatch(a -> a.contains("PROTOTYPE_ASSUMPTION"))
                .anyMatch(a -> a.contains("Stage mapping for TILLERING"));
    }
}
