package com.tuluat.engine.skill;

import java.util.List;

/**
 * SPI contract for contributing skills. Implementations are discovered via
 * {@link java.util.ServiceLoader} from mounted skill JARs, or registered
 * programmatically for compiled-in skills.
 */
public interface SkillProvider {

	/**
	 * Unique provider identifier (e.g. "builtin", "external-db").
	 */
	String providerName();

	/**
	 * Skills contributed by this provider.
	 */
	List<Skill> provideSkills();
}
