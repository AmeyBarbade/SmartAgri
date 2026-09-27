package com.agrioptima.engine.knowledge;

import com.agrioptima.engine.Nutrient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class KnowledgeBaseLoaderTest {

    static final String RESOURCE = "/knowledge/nutrient-kb-v1.json";
    static final ObjectMapper JSON = new ObjectMapper();

    public static KnowledgeBase shipped() {
        try (InputStream in = KnowledgeBaseLoaderTest.class.getResourceAsStream(RESOURCE)) {
            return KnowledgeBaseLoader.load(in);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void shippedKnowledgeBaseIsValidAndVersioned() {
        KnowledgeBase kb = shipped();
        assertThat(kb.version()).isEqualTo("1.0.0");
        assertThat(kb.status()).isEqualTo("PROTOTYPE");
        assertThat(kb.crops()).extracting(KnowledgeBase.CropKnowledge::code).containsExactly("WHEAT", "RICE", "MAIZE");
        assertThat(kb.statusLegend()).containsKeys("REFERENCED", "MAPPING_ASSUMPTION", "PROTOTYPE_ASSUMPTION");
    }

    @Test
    void everyProfileDoseIsReferencedToAKnownSource() {
        KnowledgeBase kb = shipped();
        kb.crops().forEach(c -> c.profiles().forEach(p -> {
            assertThat(p.status()).as(p.code()).isEqualTo("REFERENCED");
            assertThat(kb.source(p.sourceId())).as(p.code()).isPresent();
        }));
    }

    @Test
    void soilTestAdjustmentIsLabelledAsAPrototypeAssumption() {
        assertThat(shipped().soilTest().adjustmentFactors().status()).isEqualTo("PROTOTYPE_ASSUMPTION");
    }

    @Test
    void shippedValuesMatchTheCitedDocuments() {
        KnowledgeBase kb = shipped();
        var wheat = kb.crop("WHEAT").orElseThrow();
        assertThat(wheat.profile("IRRIGATED_TIMELY_SOWN").orElseThrow().target())
                .isEqualTo(new KnowledgeBase.Target(120, 60, 40));
        assertThat(wheat.profile("IRRIGATED_TIMELY_SOWN_NWPZ_NEPZ").orElseThrow().target())
                .isEqualTo(new KnowledgeBase.Target(150, 60, 40));
        assertThat(kb.crop("RICE").orElseThrow().profile("NRRI_GENERAL").orElseThrow().target())
                .isEqualTo(new KnowledgeBase.Target(120, 60, 40));
        assertThat(kb.soilTest().ratings().get("N").lowBelow()).isEqualTo(280);
        assertThat(kb.soilTest().ratings().get("K").lowBelow()).isEqualTo(118);
    }

    @Test
    void everyScheduleSplitsEachNutrientExactlyOnce() {
        shipped().crops().forEach(c -> c.schedules().forEach(s -> {
            for (Nutrient n : Nutrient.values()) {
                Fraction sum = s.splits().stream().map(sp -> sp.fraction(n)).reduce(Fraction.ZERO, Fraction::plus);
                assertThat(sum).as(s.code() + " " + n).isEqualTo(Fraction.ONE);
            }
        }));
    }

    @Test
    void splitsNotSummingToOneAreRejected() {
        assertRejected(root -> firstSplit(root, "WHEAT").put("n", "1/2"), "N splits sum to 7/6");
    }

    @Test
    void unknownSourceIsRejected() {
        assertRejected(root -> firstProfile(root, "RICE").put("sourceId", "NO_SUCH_SOURCE"), "requires a known sourceId");
    }

    @Test
    void unknownScheduleReferenceIsRejected() {
        assertRejected(root -> firstProfile(root, "MAIZE").put("schedule", "NOPE"), "unknown schedule NOPE");
    }

    @Test
    void ratingsOutOfOrderAreRejected() {
        assertRejected(root -> ((ObjectNode) root.at("/soilTest/ratings/P")).put("highAbove", 5),
                "need 0 <= lowBelow < highAbove");
    }

    @Test
    void negativeTargetIsRejected() {
        assertRejected(root -> ((ObjectNode) firstProfile(root, "WHEAT").get("target")).put("k2o", -1),
                "must not be negative");
    }

    @Test
    void unknownStatusIsRejected() {
        assertRejected(root -> firstProfile(root, "WHEAT").put("status", "TRUST_ME"), "unknown status TRUST_ME");
    }

    @Test
    void adjustmentFactorsMustBeOrdered() {
        assertRejected(root -> ((ObjectNode) root.at("/soilTest/adjustmentFactors")).put("HIGH", 1.5),
                "HIGH <= MEDIUM <= LOW");
    }

    @Test
    void unknownKeysAndMissingKeysMakeTheFileUnreadable() {
        assertRejected(root -> root.put("surprise", true), "unreadable");
        assertRejected(root -> root.remove("soilTest"), "unreadable");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1/0", "-1/2", "a/b", "", "1/2/3"})
    void badFractionsAreRejected(String text) {
        assertThatThrownBy(() -> Fraction.parse(text)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fractionsAreExactAndNormalised() {
        assertThat(Fraction.parse("2/6")).isEqualTo(Fraction.parse("1/3"));
        assertThat(Fraction.parse("1/3").plus(Fraction.parse("1/3")).plus(Fraction.parse("1/3")))
                .isEqualTo(Fraction.ONE);
        assertThat(Fraction.parse("1").toDouble()).isEqualTo(1.0);
        assertThat(Fraction.parse("1/4").toString()).isEqualTo("1/4");
    }

    // --- helpers

    private static void assertRejected(Consumer<ObjectNode> mutation, String expectedMessagePart) {
        ObjectNode root;
        try (InputStream in = KnowledgeBaseLoaderTest.class.getResourceAsStream(RESOURCE)) {
            root = (ObjectNode) JSON.readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        mutation.accept(root);
        byte[] bytes;
        try {
            bytes = JSON.writeValueAsBytes(root);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        assertThatThrownBy(() -> KnowledgeBaseLoader.load(new ByteArrayInputStream(bytes)))
                .isInstanceOf(KnowledgeBaseException.class)
                .hasMessageContaining(expectedMessagePart);
    }

    private static ObjectNode crop(ObjectNode root, String code) {
        for (var c : (ArrayNode) root.get("crops")) {
            if (c.get("code").asText().equals(code)) {
                return (ObjectNode) c;
            }
        }
        throw new IllegalArgumentException(code);
    }

    private static ObjectNode firstProfile(ObjectNode root, String cropCode) {
        return (ObjectNode) crop(root, cropCode).get("profiles").get(0);
    }

    private static ObjectNode firstSplit(ObjectNode root, String cropCode) {
        return (ObjectNode) crop(root, cropCode).get("schedules").get(0).get("splits").get(0);
    }
}
