package com.agrioptima.engine.knowledge;

import java.util.List;

/** The knowledge base is unreadable or inconsistent. Raised at startup, so the application refuses to run. */
public class KnowledgeBaseException extends RuntimeException {

    private final List<String> problems;

    public KnowledgeBaseException(List<String> problems) {
        super("Invalid nutrient knowledge base: " + String.join("; ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }
}
