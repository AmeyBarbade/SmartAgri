package com.agrioptima.engine;

/** The engine was given inputs it cannot compute a requirement for (unknown crop/stage/profile, bad values). */
public class EngineInputException extends IllegalArgumentException {

    public EngineInputException(String message) {
        super(message);
    }
}
