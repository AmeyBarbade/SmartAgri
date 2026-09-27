package com.agrioptima.service.recommendation;

import com.agrioptima.service.recommendation.PlanScoringService.Candidate;
import com.agrioptima.service.recommendation.PlanScoringService.Mode;
import com.agrioptima.service.recommendation.PlanScoringService.Result;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PlanScoringServiceTest {

    final PlanScoringService scoring = new PlanScoringService();
    static final BigDecimal PRICE = new BigDecimal("24250");

    @Test
    void scoreIsRevenueMinusCostMinusExcessPenalty() {
        Result r = scoring.score(List.of(
                new Candidate("LOWEST_COST", 5697.03, 23.21, 197.3, 3.317),
                new Candidate("MIN_EXCESS", 6391.68, 0.0, 441.7, 3.234),
                new Candidate("BALANCED", 6044.37, 10.83, 327.1, 3.292)), PRICE, 20);

        assertThat(r.mode()).isEqualTo(Mode.REVENUE_MINUS_COST_AND_EXCESS);
        var lowest = r.scores().stream().filter(s -> s.strategy().equals("LOWEST_COST")).findFirst().orElseThrow();
        assertThat(lowest.expectedRevenuePerHa()).isCloseTo(3.317 * 24250, within(1e-9));
        assertThat(lowest.excessPenaltyPerHa()).isCloseTo(20 * 23.21, within(1e-9));
        assertThat(lowest.scorePerHa()).isCloseTo(3.317 * 24250 - 5697.03 - 464.2, within(1e-6));
        assertThat(r.selectedStrategy()).isEqualTo("LOWEST_COST");
        assertThat(r.scores()).extracting(PlanScoringService.Score::rank).containsExactly(1, 2, 3);
        assertThat(r.selectionReason()).contains("LOWEST_COST has the highest score").doesNotContain("Tied");
    }

    @Test
    void higherPredictedYieldCanOutweighHigherCost() {
        Result r = scoring.score(List.of(
                new Candidate("LOWEST_COST", 1000, 0, 100, 3.0),
                new Candidate("MIN_EXCESS", 1500, 0, 100, 3.1)), PRICE, 20);
        assertThat(r.selectedStrategy()).isEqualTo("MIN_EXCESS");  // +0.1 t = +2425 INR > +500 INR cost
    }

    @Test
    void withoutAnyYieldScoringFallsBackToCostAndExcessForEveryPlan() {
        Result r = scoring.score(List.of(
                new Candidate("LOWEST_COST", 1000, 30, 100, 3.5),   // one plan with, one without a prediction
                new Candidate("MIN_EXCESS", 1200, 0, 100, null)), PRICE, 20);
        assertThat(r.mode()).isEqualTo(Mode.COST_AND_EXCESS_ONLY);
        assertThat(r.scores()).allSatisfy(s -> assertThat(s.expectedRevenuePerHa()).isNull());
        assertThat(r.selectedStrategy()).isEqualTo("MIN_EXCESS");  // -1200 > -1000 - 600

        Result noPrice = scoring.score(List.of(new Candidate("LOWEST_COST", 1000, 0, 100, 3.5)), null, 20);
        assertThat(noPrice.mode()).isEqualTo(Mode.COST_AND_EXCESS_ONLY);
    }

    @Test
    void tiesAreBrokenDeterministicallyRegardlessOfInputOrder() {
        List<Candidate> identical = new ArrayList<>(List.of(
                new Candidate("BALANCED", 514.79, 0.0002, 86.957, 3.25),
                new Candidate("MIN_EXCESS", 514.79, 0.0002, 86.957, 3.25),
                new Candidate("LOWEST_COST", 514.79, 0.0002, 86.957, 3.25)));
        for (int i = 0; i < 5; i++) {
            Collections.shuffle(identical, new java.util.Random(i));
            Result r = scoring.score(identical, PRICE, 20);
            assertThat(r.selectedStrategy()).isEqualTo("LOWEST_COST");
            assertThat(r.scores()).extracting(PlanScoringService.Score::strategy)
                    .containsExactly("LOWEST_COST", "MIN_EXCESS", "BALANCED");
            assertThat(r.selectionReason()).contains("Tied on score with MIN_EXCESS, BALANCED");
        }

        // equal score to the paisa -> lower cost wins before the fixed order
        Result r = scoring.score(List.of(
                new Candidate("LOWEST_COST", 1000.004, 0, 100, 3.0),
                new Candidate("BALANCED", 1000.000, 0.0002, 100, 3.0)), PRICE, 20);
        assertThat(r.selectedStrategy()).isEqualTo("BALANCED");
    }

    @Test
    void scoringNeverChangesTheCandidates() {
        List<Candidate> input = List.of(new Candidate("LOWEST_COST", 1000, 5, 100, 3.0),
                new Candidate("MIN_EXCESS", 1100, 0, 90, 3.0));
        List<Candidate> copy = List.copyOf(input);
        scoring.score(input, PRICE, 20);
        assertThat(input).isEqualTo(copy);
        assertThat(scoring.score(List.of(), PRICE, 20).selectedStrategy()).isNull();
    }
}
