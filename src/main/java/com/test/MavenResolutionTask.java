package com.test;

import org.apache.maven.Maven;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;

import java.io.File;
import java.util.Collections;

@Component(role = MavenResolutionTask.class)
public class MavenResolutionTask extends ResolutionTask {

    @Requirement
    private Maven maven;

    protected MavenResolutionTask() {
        super(MEDIUM_PRIORITY);
    }

    @Override
    protected void runImpl() throws Exception {
        MavenExecutionRequest request = new DefaultMavenExecutionRequest()
                .setLocalRepositoryPath("/tmp")
                .setGoals(Collections.singletonList("license:add-third-party"))
                .setPom(new File("pom.xml"))
                .setRecursive(true);
        MavenExecutionResult result = maven.execute(request);

        if (result.hasExceptions()) {
            RuntimeException ex = new RuntimeException("Exception occurred during maven invocation");
            result.getExceptions().forEach(ex::addSuppressed);
            throw ex;
        }

        MavenProject project = result.getProject();
        for (Artifact a : project.getArtifacts()) {
            System.out.printf("%s;%s;%s;%s;%n",
                    a.getGroupId() + ":" + a.getArtifactId()
                    , a.getVersion()
                    , a.getDownloadUrl() != null ? a.getDownloadUrl() : ""
                    , a.getDependencyTrail()
            );
        }
        complete(Collections.emptyList());
    }
}

