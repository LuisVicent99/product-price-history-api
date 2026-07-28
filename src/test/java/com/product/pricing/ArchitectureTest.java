package com.product.pricing;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
        .withImportOption(new ImportOption.DoNotIncludeTests())
        .importPackages("com.product.pricing");

    @Test
    void domainDoesNotDependOnApiInfrastructureVertxOrJaxRs() {
        noClasses()
            .that().resideInAPackage("com.product.pricing.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "com.product.pricing.api..",
                "com.product.pricing.infrastructure..",
                "io.vertx..",
                "jakarta.ws.rs..",
                "com.github.benmanes..")
            .check(CLASSES);
    }
}
