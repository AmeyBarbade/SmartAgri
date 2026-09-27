package com.agrioptima.engine.knowledge;

import com.agrioptima.engine.Nutrient;
import com.agrioptima.engine.knowledge.KnowledgeBase.CropKnowledge;
import com.agrioptima.engine.knowledge.KnowledgeBase.Profile;
import com.agrioptima.engine.knowledge.KnowledgeBase.Rating;
import com.agrioptima.engine.knowledge.KnowledgeBase.Schedule;
import com.agrioptima.engine.knowledge.KnowledgeBase.Split;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.DateTimeException;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Reads the knowledge-base JSON strictly (unknown keys are errors) and validates it before use. */
public final class KnowledgeBaseLoader {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .build();

    private KnowledgeBaseLoader() {
    }

    public static KnowledgeBase load(InputStream json) {
        KnowledgeBase kb;
        try {
            kb = MAPPER.readValue(json, KnowledgeBase.class);
        } catch (IOException | IllegalArgumentException e) {
            throw new KnowledgeBaseException(List.of("unreadable knowledge base: " + e.getMessage()));
        }
        List<String> errors = validate(kb);
        if (!errors.isEmpty()) {
            throw new KnowledgeBaseException(errors);
        }
        return kb;
    }

    static List<String> validate(KnowledgeBase kb) {
        List<String> e = new ArrayList<>();
        Checker c = new Checker(kb, e);
        c.notBlank(kb.id(), "id");
        c.notBlank(kb.version(), "version");
        c.notBlank(kb.disclaimer(), "disclaimer");
        c.notEmpty(kb.statusLegend(), "statusLegend");
        c.notEmpty(kb.sources(), "sources");
        if (!e.isEmpty()) {
            return e;
        }

        Set<String> sourceIds = new HashSet<>();
        kb.sources().forEach(s -> {
            if (!sourceIds.add(s.id())) {
                e.add("duplicate source id " + s.id());
            }
        });

        var soil = kb.soilTest();
        for (String element : List.of("N", "P", "K")) {
            Rating r = soil.ratings().get(element);
            if (r == null) {
                e.add("soilTest.ratings." + element + " missing");
                continue;
            }
            c.rating(r, "soilTest.ratings." + element);
        }
        c.rating(soil.organicCarbonPercent(), "soilTest.organicCarbonPercent");
        var f = soil.adjustmentFactors();
        if (!(f.low() > 0 && f.medium() > 0 && f.high() > 0)) {
            e.add("soilTest.adjustmentFactors must be positive");
        }
        if (!(f.high() <= f.medium() && f.medium() <= f.low())) {
            e.add("soilTest.adjustmentFactors must satisfy HIGH <= MEDIUM <= LOW");
        }
        c.status(f.status(), "soilTest.adjustmentFactors");
        if (!(soil.ph().stronglyAcidBelow() < soil.ph().stronglyAlkalineAbove())) {
            e.add("soilTest.ph limits out of order");
        }
        c.reference(soil.ph().status(), soil.ph().sourceId(), "soilTest.ph");
        c.positive(soil.maxAgeDays().value(), "soilTest.maxAgeDays");
        c.status(soil.maxAgeDays().status(), "soilTest.maxAgeDays");

        var prev = kb.previousApplications();
        c.nonNegative(prev.windowDaysBeforeSowing().value(), "previousApplications.windowDaysBeforeSowing");
        c.positive(prev.fallbackLookbackDays().value(), "previousApplications.fallbackLookbackDays");

        Set<String> cropCodes = new HashSet<>();
        for (CropKnowledge crop : kb.crops()) {
            String at = "crops." + crop.code();
            if (!cropCodes.add(crop.code())) {
                e.add("duplicate crop " + crop.code());
            }
            Set<String> profileCodes = new HashSet<>();
            for (Profile p : crop.profiles()) {
                String pAt = at + ".profiles." + p.code();
                if (!profileCodes.add(p.code())) {
                    e.add("duplicate profile " + pAt);
                }
                c.notBlank(p.description(), pAt + ".description");
                for (Nutrient n : Nutrient.values()) {
                    c.nonNegative(p.target().toAmounts().get(n), pAt + ".target." + n);
                }
                if (crop.schedule(p.schedule()).isEmpty()) {
                    e.add(pAt + ": unknown schedule " + p.schedule());
                }
                c.reference(p.status(), p.sourceId(), pAt);
            }
            for (String ref : new String[]{crop.defaultProfile(), crop.rainfedProfile(), crop.lateSownProfile()}) {
                if (ref != null && crop.profile(ref).isEmpty()) {
                    e.add(at + ": unknown profile " + ref);
                }
            }
            if (crop.defaultProfile() == null) {
                e.add(at + ": defaultProfile missing");
            }
            if (crop.lateSownProfile() != null) {
                var rule = crop.lateSowingAfter();
                if (rule == null) {
                    e.add(at + ": lateSownProfile requires lateSowingAfter");
                } else {
                    try {
                        MonthDay.of(rule.month(), rule.day());
                    } catch (DateTimeException ex) {
                        e.add(at + ".lateSowingAfter is not a valid date");
                    }
                    c.reference(rule.status(), rule.sourceId(), at + ".lateSowingAfter");
                }
            }
            for (Schedule s : crop.schedules()) {
                c.schedule(s, at + ".schedules." + s.code());
            }
        }
        return e;
    }

    private record Checker(KnowledgeBase kb, List<String> e) {

        void notBlank(String v, String at) {
            if (v == null || v.isBlank()) {
                e.add(at + " is required");
            }
        }

        void notEmpty(java.util.Collection<?> v, String at) {
            if (v == null || v.isEmpty()) {
                e.add(at + " is required");
            }
        }

        void notEmpty(java.util.Map<?, ?> v, String at) {
            if (v == null || v.isEmpty()) {
                e.add(at + " is required");
            }
        }

        void positive(double v, String at) {
            if (!(v > 0)) {
                e.add(at + " must be positive");
            }
        }

        void nonNegative(double v, String at) {
            if (!(v >= 0)) {
                e.add(at + " must not be negative");
            }
        }

        void status(String status, String at) {
            if (status == null || !kb.statusLegend().containsKey(status)) {
                e.add(at + ": unknown status " + status);
            }
        }

        /** REFERENCED / MAPPING_ASSUMPTION values must name a source that exists. */
        void reference(String status, String sourceId, String at) {
            status(status, at);
            boolean needsSource = "REFERENCED".equals(status) || "MAPPING_ASSUMPTION".equals(status);
            if (needsSource && (sourceId == null || kb.source(sourceId).isEmpty())) {
                e.add(at + ": status " + status + " requires a known sourceId, got " + sourceId);
            }
        }

        void rating(Rating r, String at) {
            if (!(r.lowBelow() >= 0 && r.lowBelow() < r.highAbove())) {
                e.add(at + ": need 0 <= lowBelow < highAbove");
            }
            reference(r.status(), r.sourceId(), at);
        }

        void schedule(Schedule s, String at) {
            if (s.splits() == null || s.splits().isEmpty()) {
                e.add(at + " has no splits");
                return;
            }
            Set<String> stages = new HashSet<>();
            for (Split split : s.splits()) {
                if (!stages.add(split.stage())) {
                    e.add(at + ": stage " + split.stage() + " listed twice");
                }
                status(split.status(), at + "." + split.stage());
            }
            for (Nutrient n : Nutrient.values()) {
                Fraction sum = Fraction.ZERO;
                for (Split split : s.splits()) {
                    sum = sum.plus(split.fraction(n));
                }
                if (!sum.equals(Fraction.ONE)) {
                    e.add(at + ": " + n + " splits sum to " + sum + ", expected 1");
                }
            }
            if (s.sourceId() != null && kb.source(s.sourceId()).isEmpty()) {
                e.add(at + ": unknown sourceId " + s.sourceId());
            }
        }
    }
}
