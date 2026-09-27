package com.agrioptima.service;

import com.agrioptima.dto.field.FieldRequest;
import com.agrioptima.entity.Crop;
import com.agrioptima.entity.CropGrowthStage;
import com.agrioptima.entity.Farm;
import com.agrioptima.exception.InvalidRequestException;
import com.agrioptima.exception.ResourceNotFoundException;
import com.agrioptima.repository.CropGrowthStageRepository;
import com.agrioptima.repository.CropRepository;
import com.agrioptima.repository.FieldRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FieldServiceTest {

    @Mock
    FieldRepository fieldRepository;
    @Mock
    FarmService farmService;
    @Mock
    CropRepository cropRepository;
    @Mock
    CropGrowthStageRepository stageRepository;
    @InjectMocks
    FieldService fieldService;

    @Test
    void cannotCreateFieldInAFarmTheCallerDoesNotOwn() {
        when(farmService.requireOwned(2L, 10L)).thenThrow(new ResourceNotFoundException("Farm", 10L));

        assertThatThrownBy(() -> fieldService.create(2L, 10L, request(null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(fieldRepository, never()).save(any());
    }

    @Test
    void growthStageMustBelongToTheSelectedCrop() {
        Crop wheat = entity(Crop.class, 1L);
        Crop rice = entity(Crop.class, 2L);
        CropGrowthStage riceTillering = entity(CropGrowthStage.class, 20L);
        ReflectionTestUtils.setField(riceTillering, "crop", rice);

        when(farmService.requireOwned(1L, 10L)).thenReturn(mock(Farm.class));
        when(cropRepository.findById(1L)).thenReturn(Optional.of(wheat));
        when(stageRepository.findById(20L)).thenReturn(Optional.of(riceTillering));

        assertThatThrownBy(() -> fieldService.create(1L, 10L, request(1L, 20L)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("does not belong");
        verify(fieldRepository, never()).save(any());
    }

    @Test
    void unknownCropIsInvalidRequest() {
        when(farmService.requireOwned(1L, 10L)).thenReturn(mock(Farm.class));
        when(cropRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fieldService.create(1L, 10L, request(99L, null)))
                .isInstanceOf(InvalidRequestException.class);
    }

    private static FieldRequest request(Long cropId, Long stageId) {
        return new FieldRequest("Plot", new BigDecimal("1.5"), null, null, cropId, stageId, null, null, null);
    }

    private static <T> T entity(Class<T> type, long id) {
        T e = BeanUtils.instantiateClass(type);
        ReflectionTestUtils.setField(e, "id", id);
        return e;
    }
}
