package com.jobforge.backend.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Set;

/** Module/dependency rules from ARCHITECTURE §4 and CLAUDE.md §5. A failure here fails CI. */
@AnalyzeClasses(packages = "com.jobforge.backend", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String BASE = "com.jobforge.backend";

    /** Rule 2: modules interact only through each other's facade (and read-only event payloads). */
    private static final Set<String> PUBLIC_LAYERS = Set.of("facade", "events");

    @ArchTest
    static final ArchRule domainDoesNotDependOnOuterLayers = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..api..", "..app..", "..infra..")
            .because("api → app → domain; domain depends on nothing but JDK + shared (ARCHITECTURE §4 rule 1)");

    @ArchTest
    static final ArchRule domainUsesOnlyAllowedLibraries = classes()
            .that().resideInAPackage("..domain..")
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                    "java..", "jakarta.persistence..", "org.hibernate.annotations..",
                    BASE + ".shared..", BASE + ".(*).domain..", "..domain..")
            .because("domain stays free of web/infrastructure frameworks");

    @ArchTest
    static final ArchRule appDoesNotDependOnApi = noClasses()
            .that().resideInAPackage("..app..")
            .should().dependOnClassesThat(resideInAPackage("..api..").and(resideOutsideOfPackage(BASE + ".shared..")))
            .because("services never depend on a module's HTTP edge (shared.api envelope/paging types are kernel, allowed)");

    @ArchTest
    static final ArchRule appContainsNoHttpTypes = noClasses()
            .that().resideInAPackage("..app..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "jakarta.servlet..", "org.springframework.web..", "org.springframework.http..")
            .because("services contain no HTTP types (ARCHITECTURE §4 rule 5)");

    @ArchTest
    static final ArchRule apiDoesNotDependOnInfra = noClasses()
            .that().resideInAPackage("..api..")
            .should().dependOnClassesThat().resideInAPackage("..infra..")
            .because("controllers go through app services, never repositories/adapters");

    @ArchTest
    static final ArchRule sharedKernelDoesNotDependOnModules = noClasses()
            .that().resideInAPackage(BASE + ".shared..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    BASE + ".auth..", BASE + ".user..", BASE + ".profile..", BASE + ".company..",
                    BASE + ".job..", BASE + ".application..", BASE + ".interview..", BASE + ".community..",
                    BASE + ".admin..", BASE + ".audit..", BASE + ".analytics..", BASE + ".storage..",
                    BASE + ".search..", BASE + ".notification..", BASE + ".ai..", BASE + ".platform..")
            .because("the kernel must stay independent of feature modules");

    @ArchTest
    static final ArchRule modulesOnlyCallOtherModulesViaFacade = classes()
            .that().resideInAPackage(BASE + "..")
            .should(onlyUseOtherModulesThroughFacade())
            .because("cross-module calls only via facade (ARCHITECTURE §4 rule 2)");

    @ArchTest
    static final ArchRule noCyclesBetweenModules = slices()
            .matching(BASE + ".(*)..")
            .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule controllersLiveInApiPackages = classes()
            .that().areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
            .or().areAnnotatedWith("org.springframework.stereotype.Controller")
            .should().resideInAPackage("..api..")
            .because("controllers are the HTTP edge only");

    @ArchTest
    static final ArchRule entitiesLiveInDomain = classes()
            .that().areAnnotatedWith("jakarta.persistence.Entity")
            .should().resideInAPackage("..domain..")
            .because("entities are domain objects and are never returned from controllers");

    @ArchTest
    static final ArchRule noFieldInjection = noFields()
            .should().beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
            .because("constructor injection only (CLAUDE.md §5)");

    @ArchTest
    static final ArchRule messagingAndRedisOnlyThroughPlatform = noClasses()
            .that().resideOutsideOfPackage(BASE + ".platform..")
            .should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.kafka.core.KafkaTemplate")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("org.springframework.data.redis.core.RedisTemplate")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("org.springframework.data.redis.core.StringRedisTemplate")
            .because("events go through OutboxPublisher and Redis through platform abstractions (CLAUDE.md §5)");

    @ArchTest
    static final ArchRule noWallClockInDomain = noClasses()
            .that().resideInAPackage("..domain..")
            .should().callMethod(java.time.Instant.class, "now")
            .orShould().callMethod(java.time.LocalDateTime.class, "now")
            .because("inject Clock; no Instant.now() in domain logic (CLAUDE.md §5)");

    // ---- custom condition ----

    private static ArchCondition<JavaClass> onlyUseOtherModulesThroughFacade() {
        return new ArchCondition<>("only use other modules through their facade or events package") {
            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                String originModule = moduleOf(origin.getPackageName());
                if (originModule == null) {
                    return; // application root class
                }
                for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    String targetModule = moduleOf(target.getPackageName());
                    if (targetModule == null
                            || targetModule.equals(originModule)
                            || targetModule.equals("shared")) {
                        continue;
                    }
                    String layer = layerOf(target.getPackageName());
                    if (layer != null && !PUBLIC_LAYERS.contains(layer)) {
                        events.add(SimpleConditionEvent.violated(
                                dependency,
                                origin.getName() + " (module " + originModule + ") uses " + target.getName()
                                        + " (module " + targetModule + ", layer " + layer + "); use its facade"));
                    }
                }
            }
        };
    }

    private static String moduleOf(String packageName) {
        String relative = relative(packageName);
        if (relative == null || relative.isEmpty()) {
            return null;
        }
        int dot = relative.indexOf('.');
        return dot < 0 ? relative : relative.substring(0, dot);
    }

    private static String layerOf(String packageName) {
        String relative = relative(packageName);
        if (relative == null) {
            return null;
        }
        String[] parts = relative.split("\\.");
        return parts.length < 2 ? null : parts[1];
    }

    private static String relative(String packageName) {
        if (packageName.equals(BASE)) {
            return "";
        }
        return packageName.startsWith(BASE + ".") ? packageName.substring(BASE.length() + 1) : null;
    }
}
