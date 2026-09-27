package com.agrioptima.service;

import com.agrioptima.dto.farm.FarmRequest;
import com.agrioptima.dto.farm.FarmResponse;
import com.agrioptima.entity.Farm;
import com.agrioptima.exception.ResourceNotFoundException;
import com.agrioptima.repository.FarmRepository;
import com.agrioptima.repository.FieldRepository;
import com.agrioptima.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Farm CRUD. Every operation is scoped to the calling user. */
@Service
@Transactional
public class FarmService {

    private final FarmRepository farmRepository;
    private final FieldRepository fieldRepository;
    private final UserRepository userRepository;

    public FarmService(FarmRepository farmRepository, FieldRepository fieldRepository,
                       UserRepository userRepository) {
        this.farmRepository = farmRepository;
        this.fieldRepository = fieldRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<FarmResponse> list(Long ownerId) {
        return farmRepository.findAllByOwnerIdOrderByNameAsc(ownerId).stream()
                .map(f -> FarmResponse.from(f, fieldRepository.countByFarmId(f.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public FarmResponse get(Long ownerId, Long farmId) {
        Farm farm = requireOwned(ownerId, farmId);
        return FarmResponse.from(farm, fieldRepository.countByFarmId(farmId));
    }

    public FarmResponse create(Long ownerId, FarmRequest request) {
        Farm farm = new Farm(userRepository.getReferenceById(ownerId));
        apply(farm, request);
        return FarmResponse.from(farmRepository.save(farm), 0);
    }

    public FarmResponse update(Long ownerId, Long farmId, FarmRequest request) {
        Farm farm = requireOwned(ownerId, farmId);
        apply(farm, request);
        return FarmResponse.from(farmRepository.saveAndFlush(farm), fieldRepository.countByFarmId(farmId));
    }

    /** Deletes the farm; fields, soil records and applications are removed by ON DELETE CASCADE. */
    public void delete(Long ownerId, Long farmId) {
        farmRepository.delete(requireOwned(ownerId, farmId));
    }

    /** @throws ResourceNotFoundException if the farm does not exist or belongs to another user */
    public Farm requireOwned(Long ownerId, Long farmId) {
        return farmRepository.findByIdAndOwnerId(farmId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Farm", farmId));
    }

    private static void apply(Farm farm, FarmRequest r) {
        farm.setName(r.name().trim());
        farm.setLocationName(blankToNull(r.locationName()));
        farm.setLatitude(r.latitude());
        farm.setLongitude(r.longitude());
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
