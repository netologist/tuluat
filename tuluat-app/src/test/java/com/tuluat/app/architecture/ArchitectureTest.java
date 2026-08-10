package com.tuluat.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Plural;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import jakarta.persistence.Entity;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

@AnalyzeClasses(packages = "com.tuluat")
public class ArchitectureTest {

	// ── helpers ───────────────────────────────────────────────────────────────

	private static final ArchCondition<com.tngtech.archunit.core.domain.JavaClass> HAVE_CREATION_TIMESTAMP =
			new ArchCondition<>("have a field annotated with @CreationTimestamp") {
				@Override
				public void check(com.tngtech.archunit.core.domain.JavaClass javaClass, ConditionEvents events) {
					boolean found = javaClass.getAllFields().stream()
							.anyMatch(f -> f.isAnnotatedWith(CreationTimestamp.class));
					if (!found) {
						events.add(SimpleConditionEvent.violated(javaClass,
								javaClass.getName() + " has no field annotated with @CreationTimestamp"));
					}
				}
			};

	// ── 1. KUBERNETES OPERATOR RULES ──────────────────────────────────────────

	@ArchTest
	public static final ArchRule reconcilers_must_reside_in_operator_package = classes().that()
			.implement(Reconciler.class).should().resideInAPackage("..operator.reconciler..").allowEmptyShould(true);

	@ArchTest
	public static final ArchRule reconcilers_must_be_suffixed_and_annotated = classes().that()
			.implement(Reconciler.class).should().haveSimpleNameEndingWith("Reconciler").andShould()
			.beAnnotatedWith(Component.class).orShould().beAnnotatedWith(Service.class).allowEmptyShould(true);

	@ArchTest
	public static final ArchRule crd_domain_isolation = noClasses().that().resideInAPackage("..crd..").should()
			.dependOnClassesThat().resideInAnyPackage("..operator..", "..engine..", "..app..").allowEmptyShould(true);

	// ── 2. SPRING BOOT RULES ──────────────────────────────────────────────────

	@ArchTest
	public static final ArchRule controllers_must_reside_in_controller_package = classes().that()
			.areAnnotatedWith(RestController.class).should().resideInAPackage("..app.controller..").andShould()
			.haveSimpleNameEndingWith("Controller").allowEmptyShould(true);

	@ArchTest
	public static final ArchRule no_field_injection_allowed = noFields().should().beAnnotatedWith(Autowired.class)
			.allowEmptyShould(true);

	@ArchTest
	public static final ArchRule controllers_not_accessed_by_core_modules = noClasses().that()
			.resideInAnyPackage("..crd..", "..engine..", "..guardrails..", "..protocols..").should().accessClassesThat()
			.resideInAPackage("..app.controller..").allowEmptyShould(true);

	// ── 3. AI & EMBABEL RULES ─────────────────────────────────────────────────

	@ArchTest
	public static final ArchRule embabel_and_ai_engines_must_reside_in_engine_package = classes().that()
			.haveSimpleNameContaining("Embabel").or().haveSimpleNameEndingWith("Engine").should()
			.resideInAPackage("..engine..").allowEmptyShould(true);

	@ArchTest
	public static final ArchRule guardrails_isolation = noClasses().that().resideInAPackage("..guardrails..").should()
			.dependOnClassesThat().resideInAnyPackage("..app.controller..", "..operator..").allowEmptyShould(true);

	@ArchTest
	public static final ArchRule protocols_isolation = noClasses().that().resideInAPackage("..protocols..").should()
			.dependOnClassesThat().resideInAnyPackage("..operator..", "..app.controller..").allowEmptyShould(true);

	// ── 4. JDK 25 MODERNIZATION RULES ─────────────────────────────────────────

	@ArchTest
	public static final ArchRule crd_classes_must_have_kind_plural_shortnames = classes().that()
			.areAssignableTo(CustomResource.class)
			.should().beAnnotatedWith(Kind.class)
			.andShould().beAnnotatedWith(Plural.class)
			.andShould().beAnnotatedWith(ShortNames.class)
			.allowEmptyShould(true);

	@ArchTest
	public static final ArchRule no_legacy_date_or_calendar = noFields().should()
			.haveRawType(java.util.Date.class)
			.orShould().haveRawType(java.util.Calendar.class)
			.allowEmptyShould(true);

	@ArchTest
	public static final ArchRule prefer_slf4j_over_explicit_logger = noFields().should()
			.haveRawType(org.slf4j.Logger.class)
			.allowEmptyShould(true);

	@ArchTest
	public static final ArchRule jpa_entities_must_have_creation_timestamp = classes().that()
			.areAnnotatedWith(Entity.class)
			.should(HAVE_CREATION_TIMESTAMP)
			.allowEmptyShould(true);
}
