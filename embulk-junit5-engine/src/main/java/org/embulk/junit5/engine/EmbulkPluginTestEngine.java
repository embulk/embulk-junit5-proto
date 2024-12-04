/*
 * Copyright 2023 The Embulk project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.embulk.junit5.engine;

import java.io.File;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.regex.Pattern;
import org.embulk.junit5.api.EmbulkPluginTest;
import org.embulk.plugin.PluginClassLoader;
import org.embulk.plugin.PluginClassLoaderFactory;
import org.embulk.plugin.PluginClassLoaderFactoryImpl;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;
import org.junit.platform.engine.EngineDiscoveryRequest;
import org.junit.platform.engine.ExecutionRequest;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.discovery.ClassSelector;
import org.junit.platform.engine.support.hierarchical.HierarchicalTestEngine;
import org.junit.platform.engine.support.hierarchical.HierarchicalTestExecutorService;
import org.junit.platform.engine.support.hierarchical.OpenTest4JAwareThrowableCollector;
import org.junit.platform.engine.support.hierarchical.SameThreadHierarchicalTestExecutorService;
import org.junit.platform.engine.support.hierarchical.ThrowableCollector;
import org.junit.platform.launcher.LauncherDiscoveryRequest;

public final class EmbulkPluginTestEngine extends HierarchicalTestEngine<EmbulkPluginTestEngineExecutionContext> {
    public EmbulkPluginTestEngine() throws InvalidPathException, IllegalArgumentException, MalformedURLException {
        super();
        final Class<?> klass = this.getClass();
        this.klassLoader = klass.getClassLoader();
        logger.info(() -> "Initializing EmbulkPluginTestEngine@" + Integer.toHexString(this.hashCode()));
        logger.info(() -> "EmbulkPluginTestEngine's ClassLoader: " + this.klassLoader.toString());
        this.pluginClassLoaderFactory = PluginClassLoaderFactoryImpl.of();

        final String pluginClassPath = System.getProperty("org.embulk.junit5.plugin.class.path");
        logger.info(() -> "System property \"org.embulk.junit5.plugin.class.path\": " + pluginClassPath);
        final ArrayList<URL> classPathUrls = new ArrayList<>();
        if (pluginClassPath != null && !pluginClassPath.isEmpty()) {
            final String[] paths = pluginClassPath.split(Pattern.quote(File.pathSeparator));
            for (final String pathString : paths) {
                final Path path;
                try {
                    path = Paths.get(pathString);
                } catch (final InvalidPathException ex) {
                    throw ex;
                }
                try {
                    classPathUrls.add(path.toUri().toURL());
                } catch (final MalformedURLException ex) {
                    throw new UncheckedIOException(ex);
                } catch (final IllegalArgumentException ex) {
                    throw ex;
                }
            }
        }
        logger.info(() -> "Building PluginClassLoader with: " + classPathUrls);

        this.pluginClassLoader =
                this.pluginClassLoaderFactory.create(Collections.unmodifiableList(classPathUrls), this.klassLoader);
    }

    /**
     * Returns {@code "embulk-junit5-engine"} as the engine ID.
     */
    @Override
    public final String getId() {
        return "embulk-junit5-engine";
    }

    /**
     * Discover tests according to the supplied EngineDiscoveryRequest.
     *
     * The supplied UniqueId must be used as the unique ID of the returned root TestDescriptor.
     * In addition, the UniqueId must be used to create unique IDs for children of the root's descriptor
     * by calling UniqueId.append(java.lang.String, java.lang.String).
     */
    @Override
    public TestDescriptor discover(final EngineDiscoveryRequest discoveryRequest, final UniqueId uniqueId) {
        logger.trace(() -> "EngineDiscoveryRequest: " + discoveryRequest.toString());
        if (discoveryRequest instanceof LauncherDiscoveryRequest) {
            final LauncherDiscoveryRequest launcherDiscoveryRequest = (LauncherDiscoveryRequest) discoveryRequest;
            logger.trace(() -> "  EngineFilters: " + launcherDiscoveryRequest.getEngineFilters());
            logger.trace(() -> "  PostDiscoveryFilters: " + launcherDiscoveryRequest.getPostDiscoveryFilters());
        }
        logger.trace(() -> "  ConfigurationParameters: " + discoveryRequest.getConfigurationParameters());
        logger.trace(() -> "UniqueId: " + uniqueId.toString());

        final EmbulkPluginTestEngineDescriptor engineDescriptor = new EmbulkPluginTestEngineDescriptor(uniqueId);

        discoveryRequest.getSelectorsByType(ClassSelector.class).forEach(classSelector -> {
            // NOTE: Gradle('s test worker) once loads the target test class in its class loader before starting the test.
            // It means that the target test class has already loaded in the its (top-level) class loader.
            //
            // https://github.com/gradle/gradle/blob/v8.10.0/platforms/jvm/testing-junit-platform/src/main/java/org/gradle/api/internal/tasks/testing/junitplatform/JUnitPlatformTestClassProcessor.java#L83-L90
            //
            // https://github.com/gradle/gradle/blob/v8.10.0/platforms/jvm/testing-junit-platform/src/main/java/org/gradle/api/internal/tasks/testing/junitplatform/JUnitPlatformTestClassProcessor.java#L99
            //
            // Unfortunately, it can conflict with the requirement for the test class to be loaded in Embulk's PluginClassLoader
            // along with the plugin's main classes.
            //
            // It is unavoidable. However, in order to mitigate the situation, Embulk and this Test Engine had smoe tweaks.
            // * This Test Engine tries to get the test class by class name, not by the Java class object. (below)
            // * This Test Engine loads the test class in Embulk's PluginClassLoader with "#loadClassInThisClassLoader"
            //     ** Added in https://github.com/embulk/embulk/pull/1686
            // * Embulk (v0.11.5+) PluginClassLoader prioritizes more classes to be loaded in it, not in the parent class loader.
            //     ** Changed in https://github.com/embulk/embulk/pull/1684
            //     ** Not all "org.embulk" classes are loaded in the parent class loader in priority.

            // final Class<?> testClass = classSelector.getJavaClass();
            // Not to get the Java class "in the top-level class loader" directly!

            final String testClassName = classSelector.getClassName();

            // Just debug prints.
            checkClass("org.embulk.input.junit5example.ExampleInputPlugin");
            checkClass("org.embulk.input.junit5example.TestExample");
            checkClass("org.embulk.input.junit5example.TestExample1");
            checkClass("org.embulk.util.config.Config");

            final Class<?> testClass = findOrLoadClassFrom(this.pluginClassLoader, testClassName);
            logger.info(() -> "<" + testClass.getName() + "> has been already loaded in [" + testClass.getClassLoader() + "]: "
                                + testClass.toString() + "@" + testClass.hashCode());

            final TestDescriptor classDescriptor =
                    new ClassTestDescriptor(uniqueId.append("class", testClass.getName()), testClass);
            System.out.println(classDescriptor);

            for (final Method method : testClass.getDeclaredMethods()) {
                if (method.isAnnotationPresent(EmbulkPluginTest.class)) {
                    logger.info(() -> "Test method <" + method + "> is found.");
                    final MethodTestDescriptor methodDescriptor =
                            new MethodTestDescriptor(uniqueId.append("method", method.getName()), testClass, method);
                    classDescriptor.addChild(methodDescriptor);
                    logger.info(() -> "Test method <" + methodDescriptor + "> is registered.");
                }
            }

            if (!classDescriptor.getChildren().isEmpty()) {
                engineDescriptor.addChild(classDescriptor);
                logger.info(() -> "Test class <" + classDescriptor + "> is registered.");
            }
        });

        logger.info(() -> "Test engine <" + engineDescriptor + "> is registered.");
        return engineDescriptor;
    }

    /**
     * Returns {@code "org.embulk"} as the artifact ID.
     */
    @Override
    public final Optional<String> getGroupId() {
        return Optional.of("org.embulk");
    }

    /**
     * Returns {@code "embulk-junit5-engine"} as the artifact ID.
     */
    @Override
    public final Optional<String> getArtifactId() {
        return Optional.of("embulk-junit5-engine");
    }

    @Override
    protected HierarchicalTestExecutorService createExecutorService​(final ExecutionRequest request) {
        return new SameThreadHierarchicalTestExecutorService();
    }

    @Override
    protected ThrowableCollector.Factory createThrowableCollectorFactory​(final ExecutionRequest request) {
        return OpenTest4JAwareThrowableCollector::new;
    }

    @Override
    protected EmbulkPluginTestEngineExecutionContext createExecutionContext​(final ExecutionRequest request) {
        return new EmbulkPluginTestEngineExecutionContext();
    }

    private static Class<?> checkClass(final String name) {
        final Class<?> clazz;
        try {
            clazz = Class.forName(name);
        } catch (final ClassNotFoundException ex) {
            logger.info(() -> "<" + name + "> has not been loaded in the top-level classLoader.");
            return null;
        }
        logger.info(() -> "<" + name + "> has been loaded in the top-level classLoader: " + clazz.toString() + "@" + clazz.hashCode());
        return clazz;
    }

    private static Class<?> findOrLoadClassFrom(final PluginClassLoader classLoader, final String name) {
        final Class<?> foundClass = LoadedClassFinder.findFrom(classLoader, name);
        if (foundClass != null) {
            logger.info(() -> "<" + name + "> has been already loaded in [" + classLoader + "]: " + foundClass.toString());
            return foundClass;
        }

        logger.info(() -> "<" + name + "> has not been loaded in [" + classLoader + "].");
        try {
            return classLoader.loadClassInThisClassLoader(name, false);
        } catch (final ClassNotFoundException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(EmbulkPluginTestEngine.class);

    private final ClassLoader klassLoader;

    private final PluginClassLoaderFactory pluginClassLoaderFactory;

    private final PluginClassLoader pluginClassLoader;
}
