package com.tuluat.engine.tool;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads {@link ToolProvider} implementations from external JARs dropped into a
 * tool folder (ADR 007). Each JAR is loaded in its own {@link URLClassLoader}
 * and must be closed after use to avoid classloader leaks.
 */
@Slf4j
public final class ToolJarLoader {

	/**
	 * Scans a folder for {@code *.jar} files and loads all {@link ToolProvider}
	 * implementations found in them.
	 *
	 * @return loaded providers; their classloaders are owned by the caller (the
	 *         registry keeps them for the lifetime of the tools)
	 */
	public static List<LoadedProvider> loadFromFolder(Path folder) {
		List<LoadedProvider> providers = new ArrayList<>();
		if (folder == null || !Files.isDirectory(folder)) {
			return providers;
		}
		try (Stream<Path> jars = Files.list(folder)) {
			jars.filter(p -> p.toString().endsWith(".jar")).sorted().forEach(jar -> providers.addAll(loadJar(jar)));
		} catch (IOException e) {
			log.warn("Failed to scan tool folder {}: {}", folder, e.getMessage());
		}
		return providers;
	}

	private static List<LoadedProvider> loadJar(Path jar) {
		List<LoadedProvider> providers = new ArrayList<>();
		URLClassLoader loader = null;
		try {
			loader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, ToolJarLoader.class.getClassLoader());
			ServiceLoader<ToolProvider> serviceLoader = ServiceLoader.load(ToolProvider.class, loader);
			for (ToolProvider provider : serviceLoader) {
				providers.add(new LoadedProvider(provider, loader));
				log.info("Loaded tool provider [{}] from {}", provider.providerName(), jar.getFileName());
			}
		} catch (Exception e) {
			log.warn("Failed to load tool JAR {}: {}", jar, e.getMessage());
			if (loader != null) {
				try {
					loader.close();
				} catch (IOException ignored) {
					// best-effort cleanup
				}
			}
		}
		return providers;
	}

	/**
	 * A loaded provider together with the classloader that must stay open while its
	 * tools are in use.
	 */
	public record LoadedProvider(ToolProvider provider, URLClassLoader classLoader) {
	}
}
