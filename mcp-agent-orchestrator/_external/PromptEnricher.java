package com.mcp.engine.reflection;

import com.mcp.core.domain.memory.FailureEntity;
import com.mcp.core.domain.memory.SkillEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromptEnricher {

    private final SkillLibraryService skillLibraryService;
    private final FailureLibraryService failureLibraryService;
    private final SkillGraphService skillGraphService;

    private static final int MAX_SKILLS = 3;
    private static final int MAX_RELATED_SKILLS = 2;

    public Mono<EnrichmentResult> enrich(String userRequest) {
        if (userRequest == null || userRequest.isBlank()) {
            return Mono.just(EnrichmentResult.empty());
        }

        List<SkillEntity> skills = skillLibraryService.retrieveRelevantSkills(userRequest);
        List<SkillEntity> topSkills = skills.stream().limit(MAX_SKILLS).toList();
        String skillPrompt = skillLibraryService.buildSkillPrompt(topSkills);

        List<SkillEntity> relatedSkills = skillGraphService.getRelatedSkills(
                topSkills.stream().map(SkillEntity::getId).toList(),
                MAX_RELATED_SKILLS);
        String relatedPrompt = skillGraphService.buildRelatedSkillPrompt(relatedSkills);

        return failureLibraryService.matchFailure(userRequest, "", null)
                .map(matchResult -> {
                    String failureWarning = "";
                    if (matchResult.shouldWarn()) {
                        failureWarning = failureLibraryService.buildFailureWarning(
                                List.of(matchResult.failure()));
                    }

                    StringBuilder enrichment = new StringBuilder();
                    if (!skillPrompt.isEmpty()) {
                        enrichment.append(skillPrompt);
                    }
                    if (!relatedPrompt.isEmpty()) {
                        enrichment.append(relatedPrompt);
                    }
                    if (!failureWarning.isEmpty()) {
                        enrichment.append(failureWarning);
                    }

                    List<SkillEntity> allMatchedSkills = new java.util.ArrayList<>(topSkills);
                    allMatchedSkills.addAll(relatedSkills);

                    return new EnrichmentResult(
                            enrichment.toString(),
                            allMatchedSkills,
                            matchResult.shouldWarn() ? List.of(matchResult.failure()) : List.of(),
                            matchResult.warningMessage()
                    );
                })
                .onErrorReturn(new EnrichmentResult(
                        skillPrompt + relatedPrompt,
                        topSkills,
                        List.of(),
                        ""
                ));
    }

    public EnrichmentResult enrichSync(String userRequest) {
        return enrich(userRequest).block();
    }

    public record EnrichmentResult(
            String promptText,
            List<SkillEntity> matchedSkills,
            List<FailureEntity> matchedFailures,
            String failureWarning
    ) {
        public static EnrichmentResult empty() {
            return new EnrichmentResult("", List.of(), List.of(), "");
        }

        public boolean hasSkills() {
            return matchedSkills != null && !matchedSkills.isEmpty();
        }

        public boolean hasFailures() {
            return matchedFailures != null && !matchedFailures.isEmpty();
        }

        public boolean isEmpty() {
            return promptText == null || promptText.isEmpty();
        }
    }
}