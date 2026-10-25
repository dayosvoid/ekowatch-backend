package com.tesa.ekowatch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

class ModuleBoundaryTest {
    @Test
    void modulesDoNotAccessOtherModulesInternals() {
        JavaClasses classes = new ClassFileImporter().importPackages("com.tesa.ekowatch");
        String[] modules = {"auth", "user", "ward", "collection", "report", "confirmation", "dashboard"};
        for (String source : modules)
            for (String target : modules)
                if (!source.equals(target)) {
                    ArchRuleDefinition.noClasses().that().resideInAPackage("com.tesa.ekowatch." + source + "..")
                            .should().dependOnClassesThat().resideInAnyPackage(
                                    "com.tesa.ekowatch." + target + ".domain..", "com.tesa.ekowatch." + target + ".repository..",
                                    "com.tesa.ekowatch." + target + ".service..", "com.tesa.ekowatch." + target + ".web..")
                            .allowEmptyShould(true).check(classes);
                }
    }
}
