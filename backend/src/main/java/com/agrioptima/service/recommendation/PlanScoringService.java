package com.agrioptima.service.recommendation;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Ranks optimizer-generated, verifier-approved plans with a transparent prototype score (INR/ha):
 *
 * <pre>
 *   score = predicted yield (t/ha) x crop price (INR/t) - fertilizer cost (INR/ha) - penalty (INR/kg) x total excess (kg/ha)
 * </pre>
 *
 * If any plan has no yield prediction (unsupported crop, model unavailable, missing inputs), revenue is left out for
 * every plan ({@link Mode#COST_AND_EXCESS_ONLY}) so the plans stay comparable.
 *
 * <p>This service only reads the candidates' figures and returns scores; candidates are immutable and it cannot create,
 * resize or change a plan. Ties (scores equal to the paisa) are broken by lower cost, then lower excess, then lower
 * product mass, then the fixed order LOWEST_COST, MIN_EXCESS, BALANCED.
 */
@Service
public class PlanScoringService {

    public enum Mode { REVENUE_MINUS_COST_AND_EXCESS, COST_AND_EXCESS_ONLY }

    public static final List<String> TIE_BREAK_ORDER = List.of("LOWEST_COST", "MIN_EXCESS", "BALANCED");
    public static final String FORMULA = "score (INR/ha) = predicted yield (t/ha) x crop price (INR/t) "
            + "- fertilizer cost (INR/ha) - excess penalty (INR/kg) x total N+P2O5+K2O excess (kg/ha)";
    public static final String FORMULA_WITHOUT_YIELD = "score (INR/ha) = - fertilizer cost (INR/ha) "
            + "- excess penalty (INR/kg) x total N+P2O5+K2O excess (kg/ha)   (no yield prediction available)";
    public static final String TIE_BREAK = "equal score (to 0.01 INR/ha) -> lower cost -> lower total excess "
            + "-> lower product mass -> LOWEST_COST, MIN_EXCESS, BALANCED";

    /** Figures of one plan; {@code predictedYieldTHa} is null when no prediction is available. */
    public record Candidate(String strategy, double costPerHa, double totalExcessKgHa, double totalMassKgHa,
                            Double predictedYieldTHa) {
    }

    public record Score(String strategy, Double expectedRevenuePerHa, double fertilizerCostPerHa,
                        double excessPenaltyPerHa, double scorePerHa, int rank) {
    }

    public record Result(Mode mode, String formula, List<Score> scores, String selectedStrategy,
                         String selectionReason) {
    }

    public Result score(List<Candidate> candidates, BigDecimal cropPriceInrPerTonne, double excessPenaltyInrPerKg) {
        boolean withYield = cropPriceInrPerTonne != null && !candidates.isEmpty()
                && candidates.stream().allMatch(c -> c.predictedYieldTHa() != null);
        Mode mode = withYield ? Mode.REVENUE_MINUS_COST_AND_EXCESS : Mode.COST_AND_EXCESS_ONLY;

        List<Scored> scored = new ArrayList<>();
        for (Candidate c : candidates) {
            Double revenue = withYield ? c.predictedYieldTHa() * cropPriceInrPerTonne.doubleValue() : null;
            double penalty = excessPenaltyInrPerKg * c.totalExcessKgHa();
            double value = (revenue == null ? 0 : revenue) - c.costPerHa() - penalty;
            scored.add(new Scored(c, revenue, penalty, value, paise(value)));
        }
        scored.sort(Comparator.comparingLong((Scored s) -> -s.paise)
                .thenComparingDouble(s -> s.candidate.costPerHa())
                .thenComparingDouble(s -> s.candidate.totalExcessKgHa())
                .thenComparingDouble(s -> s.candidate.totalMassKgHa())
                .thenComparingInt(s -> tieIndex(s.candidate.strategy())));

        List<Score> scores = new ArrayList<>();
        for (int i = 0; i < scored.size(); i++) {
            Scored s = scored.get(i);
            scores.add(new Score(s.candidate.strategy(), s.revenue, s.candidate.costPerHa(), s.penalty, s.value, i + 1));
        }
        String selected = scored.isEmpty() ? null : scored.get(0).candidate.strategy();
        return new Result(mode, withYield ? FORMULA : FORMULA_WITHOUT_YIELD, List.copyOf(scores), selected,
                reason(scored, mode));
    }

    private record Scored(Candidate candidate, Double revenue, double penalty, double value, long paise) {
    }

    private static String reason(List<Scored> scored, Mode mode) {
        if (scored.isEmpty()) {
            return null;
        }
        Scored best = scored.get(0);
        String breakdown = mode == Mode.REVENUE_MINUS_COST_AND_EXCESS
                ? String.format(Locale.ROOT, "expected revenue %.2f - fertilizer %.2f - excess penalty %.2f",
                        best.revenue, best.candidate.costPerHa(), best.penalty)
                : String.format(Locale.ROOT, "fertilizer %.2f + excess penalty %.2f; no yield prediction",
                        best.candidate.costPerHa(), best.penalty);
        String text = String.format(Locale.ROOT, "%s has the highest score: %.2f INR/ha (%s).",
                best.candidate.strategy(), best.value, breakdown);
        List<String> tied = scored.stream().skip(1).filter(s -> s.paise == best.paise)
                .map(s -> s.candidate.strategy()).toList();
        if (!tied.isEmpty()) {
            text += " Tied on score with " + String.join(", ", tied) + "; tie-break: " + TIE_BREAK + ".";
        }
        return text;
    }

    private static long paise(double inr) {
        return BigDecimal.valueOf(inr).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }

    private static int tieIndex(String strategy) {
        int i = TIE_BREAK_ORDER.indexOf(strategy);
        return i < 0 ? TIE_BREAK_ORDER.size() : i;
    }
}
