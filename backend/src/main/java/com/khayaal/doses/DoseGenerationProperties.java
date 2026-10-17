package com.khayaal.doses;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.doses")
public record DoseGenerationProperties(int generationHorizonDays) { }
