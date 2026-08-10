package com.tuluat.engine.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Stream;

/**
 * Loads {@link SkillProvider} implementations from external JARs dropped into a
 * skill folder (ADR 007). Each JAR is loaded in its own {@link URLClassLoader}
 * and must be closed after use to avoid classloader leaks.
 */
public final class SkillJarLoader {

	private static final Logger log = LoggerFactory.getLogger(SkillJarLoader.class);

	private SkillJarLoader() {
	}

	/**
	 * Scans a folder for {@code *.jar} files and loads all {@link SkillProvider}
	 * implementations found in them.
	 *
	 * @return loaded providers; their classloaders are owned by the caller (the
	 *         registry keeps them for the lifetime of the skills)
	 */
	public static List<LoadedProvider> loadFromFolder(Path folder) {
		List<LoadedProvider> providers = new ArrayList<>();
		if (folder == null || !Files.isDirectory(folder)) {
			return providers;
		}
		try (Stream<Path> jars = Files.list(folder)) {
			jars.filter(p -> p.toString().endsWith(".jar")).sorted().forEach(jar -> providers.addAll(loadJar(jar)));
		} catch (IOException e) {
			log.warn("Failed to scan skill folder {}: {}", folder, e.getMessage());
		}
		return providers;
	}

	private static List<LoadedProvider> loadJar(Path jar) {
		List<LoadedProvider> providers = new ArrayList<>();
		URLClassLoader loader = null;
		try {
			loader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, SkillJarLoader.class.getClassLoader());
			ServiceLoader<SkillProvider> serviceLoader = ServiceLoader.load(SkillProvider.class, loader);
			for (SkillProvider provider : serviceLoader) {
				providers.add(new LoadedProvider(provider, loader));
				log.info("Loaded skill provider [{}] from {}", provider.providerName(), jar.getFileName());
			}
		} catch (Exception e) {
			log.warn("Failed to load skill JAR {}: {}", jar, e.getMessage());
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
	 * skills are in use.
	 */
	public record LoadedProvider(SkillProvider provider, URLClassLoader classLoader) {
	}
}
