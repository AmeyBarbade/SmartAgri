package com.agrioptima.service;

import com.agrioptima.dto.soil.SoilRecordRequest;
import com.agrioptima.dto.soil.SoilRecordResponse;
import com.agrioptima.entity.Field;
import com.agrioptima.entity.SoilRecord;
import com.agrioptima.exception.ResourceNotFoundException;
import com.agrioptima.repository.SoilRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.agrioptima.service.FarmService.blankToNull;

/** Soil tests per field. History is newest first; ties on sample date are broken by insertion order. */
@Service
@Transactional
public class SoilRecordService {

    private final SoilRecordRepository soilRecordRepository;
    private final FieldService fieldService;

    public SoilRecordService(SoilRecordRepository soilRecordRepository, FieldService fieldService) {
        this.soilRecordRepository = soilRecordRepository;
        this.fieldService = fieldService;
    }

    public SoilRecordResponse create(Long ownerId, Long fieldId, SoilRecordRequest r) {
        Field field = fieldService.requireOwned(ownerId, fieldId);
        SoilRecord record = new SoilRecord(field, r.sampleDate(), r.nitrogen(), r.phosphorus(), r.potassium(),
                r.ph(), r.organicCarbon(), r.moisture(), blankToNull(r.notes()));
        return SoilRecordResponse.from(soilRecordRepository.save(record));
    }

    @Transactional(readOnly = true)
    public List<SoilRecordResponse> history(Long ownerId, Long fieldId) {
        fieldService.requireOwned(ownerId, fieldId);
        return soilRecordRepository.findAllByFieldIdOrderBySampleDateDescIdDesc(fieldId).stream()
                .map(SoilRecordResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public SoilRecordResponse latest(Long ownerId, Long fieldId) {
        fieldService.requireOwned(ownerId, fieldId);
        return soilRecordRepository.findFirstByFieldIdOrderBySampleDateDescIdDesc(fieldId)
                .map(SoilRecordResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Soil record for field", fieldId));
    }

    @Transactional(readOnly = true)
    public SoilRecordResponse get(Long ownerId, Long recordId) {
        return SoilRecordResponse.from(requireOwned(ownerId, recordId));
    }

    public void delete(Long ownerId, Long recordId) {
        soilRecordRepository.delete(requireOwned(ownerId, recordId));
    }

    private SoilRecord requireOwned(Long ownerId, Long recordId) {
        return soilRecordRepository.findOwned(recordId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Soil record", recordId));
    }
}
