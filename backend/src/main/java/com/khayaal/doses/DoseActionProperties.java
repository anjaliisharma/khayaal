package com.khayaal.doses;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.doses.actions")
public record DoseActionProperties(int missedGraceMinutes, int undoWindowMinutes) {
    public DoseActionProperties {
        if (missedGraceMinutes < 1) missedGraceMinutes = 120;
        if (undoWindowMinutes < 1) undoWindowMinutes = 240;
    }
}
