package com.test;

import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.artifact.repository.layout.FlatRepositoryLayout;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class Main {

    private static final String DEFAULT_HINT = "default";
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    private static final PriorityBlockingQueue<ResolutionTask> taskQueue = new PriorityBlockingQueue<>();

    private static final ServiceLocator serviceLocator = MavenRepositorySystemUtils.newServiceLocator()
            .addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class)
            .addService(TransporterFactory.class, FileTransporterFactory.class)
            .addService(TransporterFactory.class, HttpTransporterFactory.class);

    public static void main(String[] args) {
        List<Object> depResults = new ArrayList<>();
        PlexusContainer container = null;
        try {
            container = new DefaultPlexusContainer();
            // bind eclipse aether RepositorySystem
            container.addComponent(serviceLocator.getService(RepositorySystem.class), RepositorySystem.class, DEFAULT_HINT);
            container.addComponent(serviceLocator.getService(RemoteRepositoryManager.class), RemoteRepositoryManager.class, DEFAULT_HINT);
            // this shouldn't be necessary, but apparently it is...
            container.addComponent(new DefaultRepositoryLayout(), ArtifactRepositoryLayout.class, DEFAULT_HINT);
            container.addComponent(new FlatRepositoryLayout(), ArtifactRepositoryLayout.class, "flat");

            LOGGER.info("Starting resolution process");
            final ExecutorService backgroundWorkers = Executors.newFixedThreadPool(2, runnable -> {
                Thread result = new Thread(runnable, "WorkerQueue Thread");
                result.setDaemon(true);
                return result;
            });

            taskQueue.put(container.lookup(MavenResolutionTask.class));

            ResolutionTask finalizer = new ResolutionTask(10) {
                @Override
                protected void runImpl() throws Exception {
                    depResults.forEach(System.out::println);
                }
            };
            finalizer.whenComplete((a, ex) -> {
                backgroundWorkers.shutdown();
                if (ex != null) {
                    LOGGER.error("Finalizer completed with an exception: {}", ex);
                    ex.printStackTrace(System.err);
                }
            });
            taskQueue.put(finalizer);

            List<CompletableFuture<List<Object>>> currentCompletables = new ArrayList<>();
            while (!taskQueue.isEmpty()) {
                ResolutionTask task = taskQueue.poll();
                if (task == finalizer) {
                    CompletableFuture<Void> cleanup;
                    cleanup = CompletableFuture.allOf(currentCompletables.toArray(new CompletableFuture<?>[0]));
                    currentCompletables.clear();
                    cleanup.join();
                    if (taskQueue.isEmpty()) {
                        finalizer.run();
                    } else {
                        taskQueue.put(finalizer);
                    }
                } else {
                    task.thenAccept(collection -> {
                        LOGGER.info("Completing resolution process with {} results", collection.size());
                        depResults.addAll(collection);
                    });
                    currentCompletables.add(task);
                    backgroundWorkers.execute(task);
                }
            }
            // assume we've finished all the carp after three minutes?
            backgroundWorkers.awaitTermination(3, TimeUnit.MINUTES);
        } catch (ComponentLookupException | PlexusContainerException  e) {
            LOGGER.error("Top-Level Exception caught: {}", e);
            // Something really went wrong.
            e.printStackTrace();
        } catch (InterruptedException e) {
            LOGGER.info("Termination wait was interrupted");
            // not sure what the whole point is, but eh
            Thread.currentThread().interrupt();
        } finally {
            if (container != null) {
                container.dispose();
            }
        }
    }
}
