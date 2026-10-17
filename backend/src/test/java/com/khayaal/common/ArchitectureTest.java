package com.khayaal.common;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

class ArchitectureTest {
    @Test
    void purePackagesMustNotDependOnFrameworksOrPersistence() {
        JavaClasses classes = new ClassFileImporter().importPackages("com.khayaal");
        ArchRuleDefinition.noClasses()
                .that().resideInAnyPackage("..schedule..", "..forecast..", "..adherence..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta.persistence..")
                .allowEmptyShould(true)
                .check(classes);
    }
}

