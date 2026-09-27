package com.agrioptima.service.recommendation;

import com.agrioptima.entity.IrrigationType;
import com.agrioptima.service.recommendation.YieldFeatureMapper.Base;
import com.agrioptima.service.recommendation.YieldFeatureMapper.Source;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class YieldFeatureMapperTest {

    static final LocalDate SOWN = LocalDate.of(2025, 11, 20);

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
            "'Patna, Bihar', BIHAR", "'Lucknow, Uttar Pradesh', UTTAR_PRADESH", "uttar-pradesh, UTTAR_PRADESH",
            "'Raipur, Chattisgarh', CHHATTISGARH", "West Bengal, WEST_BENGAL", "'Karnal', null", "null, null",
            "'Bihar / Punjab border', null", "'Biharsharif', null"})
    void stateIsTakenFromTheFarmLocationOnlyWhenUnambiguous(String location, String expected) {
        Base base = YieldFeatureMapper.map("WHEAT", location, SOWN, null, IrrigationType.IRRIGATED, null);
        assertThat(base.state()).isEqualTo(expected);
        assertThat(base.available()).isEqualTo(expected != null);
    }

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
            "Sandy loam, LIGHT", "Loamy sand, LIGHT", "Clay loam, HEAVY", "Black cotton soil, HEAVY", "Loam, MEDIUM",
            "Silt loam, MEDIUM", "Alluvial, MEDIUM", "medium, MEDIUM", "Laterite, null", "null, null"})
    void soilTypeMapsToTexture(String soilType, String texture) {
        assertThat(YieldFeatureMapper.soilTexture(soilType)).isEqualTo(texture);
    }

    @Test
    void everyInputIsReportedWithItsSource() {
        Base base = YieldFeatureMapper.map("RICE", "Cuttack, Odisha", SOWN, "Clay", null, "Wheat");
        assertThat(base.available()).isTrue();
        assertThat(base.irrigationAvailable()).isTrue();
        assertThat(source(base, "irrigation_available")).isEqualTo(Source.PROTOTYPE_DEFAULT);
        assertThat(source(base, "state")).isEqualTo(Source.MAPPED);
        assertThat(source(base, "soil_texture")).isEqualTo(Source.MAPPED);
        assertThat(source(base, "variety_type")).isEqualTo(Source.NOT_RECORDED);
        assertThat(source(base, "fym_applied")).isEqualTo(Source.PROTOTYPE_DEFAULT);
        assertThat(source(base, "zn_applied")).isEqualTo(Source.DERIVED);
        var scenario = base.scenario(120, 60, 40);
        assertThat(scenario.varietyType()).isNull();
        assertThat(scenario.previousCrop()).isEqualTo("Wheat");
        assertThat(scenario.nKgHa()).isEqualTo(120);
    }

    @Test
    void rainfedAndMissingSowingDate() {
        Base base = YieldFeatureMapper.map("WHEAT", "Patna, Bihar", null, null, IrrigationType.RAINFED, " ");
        assertThat(base.irrigationAvailable()).isFalse();
        assertThat(base.previousCrop()).isNull();
        assertThat(base.available()).isFalse();
        assertThat(base.unavailableReason()).contains("sowing date");
    }

    private static Source source(Base base, String feature) {
        return base.inputs().stream().filter(i -> i.feature().equals(feature)).findFirst().orElseThrow().source();
    }
}
