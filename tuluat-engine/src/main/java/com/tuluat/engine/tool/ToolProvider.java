package com.tuluat.engine.tool;

import java.util.List;

/**
 * SPI contract for contributing tools. Implementations are discovered via
 * {@link java.util.ServiceLoader} from mounted tool JARs, or registered
 * programmatically for compiled-in tools.
 */
public interface ToolProvider {

	/**
	 * Unique provider identifier (e.g. "builtin", "external-db").
	 */
	String providerName();

	/**
	 * Tools contributed by this provider.
	 */
	List<Tool> provideTools();
}
