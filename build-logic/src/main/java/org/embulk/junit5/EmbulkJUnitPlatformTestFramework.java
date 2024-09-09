package org.embulk.junit5;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.gradle.api.GradleException;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.internal.tasks.testing.TestFramework;
import org.gradle.api.internal.tasks.testing.WorkerTestClassProcessorFactory;
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.api.internal.tasks.testing.junitplatform.JUnitPlatformTestFramework;
import org.gradle.api.internal.tasks.testing.junitplatform.JUnitPlatformSpec;

import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.api.internal.tasks.testing.TestFramework;
import org.gradle.api.internal.tasks.testing.WorkerTestClassProcessorFactory;
import org.gradle.api.internal.tasks.testing.detection.TestFrameworkDetector;
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.actor.ActorFactory;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.jvm.UnsupportedJavaRuntimeException;
import org.gradle.internal.scan.UsedByScanPlugin;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.time.Clock;
import org.gradle.process.internal.worker.WorkerProcessBuilder;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.util.List;

public class EmbulkJUnitPlatformTestFramework extends JUnitPlatformTestFramework {
    private final DefaultTestFilter filter2;

    public EmbulkJUnitPlatformTestFramework(
            final DefaultTestFilter filter,
            final Provider<Boolean> dryRun) {
        super(filter, /* useImplementationDependencies */ false, dryRun);
        this.filter2 = filter;
    }

    /*
    @Override
    public WorkerTestClassProcessorFactory getProcessorFactory() {
        final WorkerTestClassProcessorFactory factory;

        factory = super.getProcessorFactory();

        // factory = new EmbulkJUnitPlatformTestClassProcessorFactory(new JUnitPlatformSpec(this.getOptions(),
        //     filter2.getIncludePatterns(), filter2.getExcludePatterns(),
        //     filter2.getCommandLineIncludePatterns()));

        System.out.println(factory.getClass());
        System.out.println(factory.toString());

        return factory;
    }

    public static class EmbulkJUnitPlatformTestClassProcessorFactory implements WorkerTestClassProcessorFactory, Serializable {
        private final JUnitPlatformSpec spec;

        EmbulkJUnitPlatformTestClassProcessorFactory(JUnitPlatformSpec spec) {
            this.spec = spec;
        }

        @Override
        public TestClassProcessor create(ServiceRegistry serviceRegistry) {
            try {
                IdGenerator<?> idGenerator = serviceRegistry.get(IdGenerator.class);
                Clock clock = serviceRegistry.get(Clock.class);
                ActorFactory actorFactory = serviceRegistry.get(ActorFactory.class);
                Class<?> clazz = getClass().getClassLoader().loadClass("org.gradle.api.internal.tasks.testing.junitplatform.JUnitPlatformTestClassProcessor");
                // Class<?> clazz = getClass().getClassLoader().loadClass("org.embulk.gradle.embulk_plugins.EmbulkJUnitPlatformTestClassProcessor");

                Constructor<?> constructor = clazz.getConstructor(JUnitPlatformSpec.class, IdGenerator.class, ActorFactory.class, Clock.class);
                return (TestClassProcessor) constructor.newInstance(spec, idGenerator, actorFactory, clock);
            } catch (Exception e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
    }
    */
}
