package com.agrioptima.service;

import com.agrioptima.dto.farm.FarmRequest;
import com.agrioptima.entity.Farm;
import com.agrioptima.entity.Role;
import com.agrioptima.entity.User;
import com.agrioptima.exception.ResourceNotFoundException;
import com.agrioptima.repository.FarmRepository;
import com.agrioptima.repository.FieldRepository;
import com.agrioptima.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FarmServiceTest {

    @Mock
    FarmRepository farmRepository;
    @Mock
    FieldRepository fieldRepository;
    @Mock
    UserRepository userRepository;
    @InjectMocks
    FarmService farmService;

    static final long OWNER = 1L;
    static final long INTRUDER = 2L;

    @Test
    void lookupsAreAlwaysScopedToTheCaller() {
        when(farmRepository.findByIdAndOwnerId(10L, INTRUDER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> farmService.get(INTRUDER, 10L)).isInstanceOf(ResourceNotFoundException.class);
        // Never falls back to an unscoped lookup.
        verify(farmRepository, never()).findById(anyLong());
    }

    @Test
    void updateAndDeleteOfForeignFarmDoNothing() {
        when(farmRepository.findByIdAndOwnerId(10L, INTRUDER)).thenReturn(Optional.empty());
        FarmRequest request = new FarmRequest("Hijacked", null, null, null);

        assertThatThrownBy(() -> farmService.update(INTRUDER, 10L, request))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> farmService.delete(INTRUDER, 10L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(farmRepository, never()).saveAndFlush(any());
        verify(farmRepository, never()).delete(any());
    }

    @Test
    void createAssignsCallerAsOwnerAndCleansInput() {
        User owner = new User("Owner", "o@example.com", "h", Role.FARMER);
        when(userRepository.getReferenceById(OWNER)).thenReturn(owner);
        when(farmRepository.save(any(Farm.class))).thenAnswer(inv -> inv.getArgument(0));

        farmService.create(OWNER, new FarmRequest("  Green Acres ", "   ", new BigDecimal("29.6856"),
                new BigDecimal("76.9905")));

        ArgumentCaptor<Farm> saved = ArgumentCaptor.forClass(Farm.class);
        verify(farmRepository).save(saved.capture());
        assertThat(saved.getValue().getOwner()).isSameAs(owner);
        assertThat(saved.getValue().getName()).isEqualTo("Green Acres");
        assertThat(saved.getValue().getLocationName()).isNull();
        assertThat(saved.getValue().getLatitude()).isEqualByComparingTo("29.6856");
    }
}
