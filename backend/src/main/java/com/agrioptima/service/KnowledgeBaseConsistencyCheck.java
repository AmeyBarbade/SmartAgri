package com.agrioptima.service;

import com.agrioptima.engine.knowledge.KnowledgeBase;
import com.agrioptima.engine.knowledge.KnowledgeBaseException;
import com.agrioptima.entity.Crop;
import com.agrioptima.entity.CropGrowthStage;
import com.agrioptima.repository.CropGrowthStageRepository;
import com.agrioptima.repository.CropRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * At startup, checks that every crop and growth-stage code used by the knowledge base exists in the
 * Flyway reference data, so a renamed stage cannot silently drop out of a schedule.
 */
@Component
public class KnowledgeBaseConsistencyCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseConsistencyCheck.class);

    private final KnowledgeBase kb;
    private final CropRepository cropRepository;
    private final CropGrowthStageRepository stageRepository;

    public KnowledgeBaseConsistencyCheck(KnowledgeBase kb, CropRepository cropRepository,
                                         CropGrowthStageRepository stageRepository) {
        this.kb = kb;
        this.cropRepository = cropRepository;
        this.stageRepository = stageRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public void run(ApplicationArguments args) {
        List<String> problems = check();
        if (!problems.isEmpty()) {
            throw new KnowledgeBaseException(problems);
        }
    }

    public List<String> check() {
        List<String> problems = new ArrayList<>();
        List<Crop> crops = cropRepository.findAll();
        for (KnowledgeBase.CropKnowledge ck : kb.crops()) {
            Crop crop = crops.stream().filter(c -> c.getCode().equals(ck.code())).findFirst().orElse(null);
            if (crop == null) {
                problems.add("knowledge base crop " + ck.code() + " is not in the crops table");
                continue;
            }
            Set<String> stageCodes = stageRepository.findAllByCropIdOrderBySeqAsc(crop.getId()).stream()
                    .map(CropGrowthStage::getCode).collect(Collectors.toSet());
            ck.schedules().forEach(s -> s.splits().forEach(split -> {
                if (!stageCodes.contains(split.stage())) {
                    problems.add("schedule " + ck.code() + "." + s.code() + " uses unknown stage " + split.stage());
                }
            }));
        }
        crops.stream().map(Crop::getCode).filter(code -> kb.crop(code).isEmpty())
                .forEach(code -> log.warn("Crop {} has no entry in the nutrient knowledge base", code));
        return problems;
    }
}
