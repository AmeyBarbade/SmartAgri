package com.agrioptima.engine.knowledge;

import com.agrioptima.engine.Nutrient;
import com.agrioptima.engine.NutrientAmounts;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Versioned agronomic knowledge base (resources/knowledge/nutrient-kb-v1.json), validated by
 * {@link KnowledgeBaseLoader}. Every value carries a status ({@code REFERENCED}, {@code MAPPING_ASSUMPTION},
 * {@code PROTOTYPE_ASSUMPTION}, {@code DERIVED}) and, where referenced, a source id.
 */
public record KnowledgeBase(String id, String version, String releasedOn, String status, String disclaimer,
                            Map<String, String> statusLegend, Map<String, String> units, List<Source> sources,
                            SoilTest soilTest, PreviousApplications previousApplications, List<CropKnowledge> crops) {

    public Optional<CropKnowledge> crop(String code) {
        return crops.stream().filter(c -> c.code().equals(code)).findFirst();
    }

    public Optional<Source> source(String sourceId) {
        return sources.stream().filter(s -> s.id().equals(sourceId)).findFirst();
    }

    public record Source(String id, String citation, String url, String accessed, String usedFor) {
    }

    public record SoilTest(String basis, Map<String, Rating> ratings, String ratingNotes,
                           AdjustmentFactors adjustmentFactors, Rating organicCarbonPercent, PhLimits ph,
                           LabelledValue maxAgeDays) {
    }

    /** Values below {@code lowBelow} are LOW, above {@code highAbove} HIGH, otherwise MEDIUM (inclusive). */
    public record Rating(double lowBelow, double highAbove, String status, String sourceId, String use) {
    }

    public record AdjustmentFactors(@JsonProperty("LOW") double low, @JsonProperty("MEDIUM") double medium,
                                    @JsonProperty("HIGH") double high, String status, String note) {
    }

    public record PhLimits(double stronglyAcidBelow, double stronglyAlkalineAbove, String status, String sourceId,
                           String use) {
    }

    public record LabelledValue(double value, String status, String note) {
    }

    public record PreviousApplications(LabelledValue windowDaysBeforeSowing, LabelledValue fallbackLookbackDays,
                                       String carryOver) {
    }

    public record MonthDayRule(int month, int day, String status, String sourceId, String note) {
    }

    public record CropKnowledge(String code, String defaultProfile, String rainfedProfile, String lateSownProfile,
                                MonthDayRule lateSowingAfter, List<Profile> profiles, List<Schedule> schedules) {

        public Optional<Profile> profile(String profileCode) {
            return profiles.stream().filter(p -> p.code().equals(profileCode)).findFirst();
        }

        public Optional<Schedule> schedule(String scheduleCode) {
            return schedules.stream().filter(s -> s.code().equals(scheduleCode)).findFirst();
        }
    }

    public record Target(double n, double p2o5, double k2o) {

        public NutrientAmounts toAmounts() {
            return new NutrientAmounts(n, p2o5, k2o);
        }
    }

    public record Profile(String code, String description, Target target, String schedule, String status,
                          String sourceId) {
    }

    public record Schedule(String code, List<Split> splits, String sourceId, String note) {
    }

    /** Share of the season's dose of each nutrient applied at {@code stage}. */
    public record Split(String stage, Fraction n, Fraction p2o5, Fraction k2o, String timing, String status) {

        public Fraction fraction(Nutrient nutrient) {
            return switch (nutrient) {
                case N -> n;
                case P2O5 -> p2o5;
                case K2O -> k2o;
            };
        }

        public NutrientAmounts fractions() {
            return new NutrientAmounts(n.toDouble(), p2o5.toDouble(), k2o.toDouble());
        }
    }
}
