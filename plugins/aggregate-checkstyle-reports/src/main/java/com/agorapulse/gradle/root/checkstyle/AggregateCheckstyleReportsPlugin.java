package com.agorapulse.gradle.root.checkstyle;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.plugins.AppliedPlugin;
import org.gradle.api.plugins.quality.Checkstyle;
import org.gradle.api.plugins.quality.CheckstyleExtension;
import org.gradle.api.plugins.quality.CheckstylePlugin;
import org.gradle.api.plugins.quality.CheckstyleReports;
import org.gradle.api.resources.TextResource;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.gradle.api.plugins.quality.CheckstylePlugin.DEFAULT_CHECKSTYLE_VERSION;

public class AggregateCheckstyleReportsPlugin implements Plugin<Project> {

    public static final String AGGREGATE_CHECKSTYLE_TASK_NAME = "aggregateCheckstyle";

    private static final List<String> CONFIG_LOCATIONS = Arrays.asList(
        "config/checkstyle.xml",
        "../config/checkstyle.xml",
        "config/checkstyle/checkstyle.xml",
        "../config/checkstyle/checkstyle.xml"
    );

    @Override
    public void apply(Project rootProject) {
        CheckstyleExtension rootExt = createExtension(rootProject);

        rootProject.getTasks().register(AGGREGATE_CHECKSTYLE_TASK_NAME, Checkstyle.class, new Action<Checkstyle>() {
            @Override
            public void execute(Checkstyle aggregateCheckstyle) {
                aggregateCheckstyle.setGroup("reporting");
                aggregateCheckstyle.setDescription("Aggregates all Checkstyle reports");

                configureTaskConventionMapping(rootExt, aggregateCheckstyle);

                CheckstyleReports reports = aggregateCheckstyle.getReports();
                reports.getHtml().getRequired().set(true);
                reports.getXml().getRequired().set(true);

                DirectoryProperty buildDir = rootProject.getLayout().getBuildDirectory();
                reports.getHtml().setDestination(buildDir.file("reports/checkstyle/aggregate.html").map(RegularFile::getAsFile));
                reports.getXml().setDestination(buildDir.file("reports/checkstyle/aggregate.xml").map(RegularFile::getAsFile));
            }
        });

        rootProject.subprojects(new Action<Project>() {
            @Override
            public void execute(Project subproject) {
                subproject.getPluginManager().withPlugin("java-base", new Action<AppliedPlugin>() {
                    @Override
                    public void execute(AppliedPlugin appliedPlugin) {
                        subproject.getPluginManager().apply(CheckstylePlugin.class);

                        CheckstyleExtension ext = subproject.getExtensions().getByType(CheckstyleExtension.class);
                        ext.setToolVersion(rootExt.getToolVersion());
                        ext.setConfigFile(rootExt.getConfigFile());
                        ext.getConfigDirectory().set(rootExt.getConfigDirectory());

                        subproject.getTasks().withType(Checkstyle.class, new Action<Checkstyle>() {
                            @Override
                            public void execute(Checkstyle checkstyle) {
                                checkstyle.doFirst(new Action<Task>() {
                                    @Override
                                    public void execute(Task task) {
                                        checkstyle.setIgnoreFailures(subproject.getGradle().getTaskGraph().hasTask(":" + AGGREGATE_CHECKSTYLE_TASK_NAME));
                                    }
                                });

                                checkstyle.setConfigFile(ext.getConfigFile());
                                checkstyle.getConfigDirectory().set(ext.getConfigDirectory());

                                subproject.getRootProject().getTasks().getByName(AGGREGATE_CHECKSTYLE_TASK_NAME, new Action<Task>() {
                                    @Override
                                    public void execute(Task task) {
                                        Checkstyle aggregateTask = (Checkstyle) task;
                                        aggregateTask.dependsOn(checkstyle);

                                        subproject.afterEvaluate(new Action<Project>() {
                                            @Override
                                            public void execute(Project project) {
                                                aggregateTask.source(checkstyle.getSource());
                                                aggregateTask.setClasspath(join(aggregateTask.getClasspath(), checkstyle.getClasspath()));
                                                aggregateTask.setCheckstyleClasspath(join(checkstyle.getCheckstyleClasspath(), aggregateTask.getCheckstyleClasspath()));
                                            }
                                        });
                                    }
                                });
                            }
                        });
                    }
                });
            }
        });

    }

    private static File getConfigFile(Project rootProject) {
        for (String location : CONFIG_LOCATIONS) {
            File specificConfigFile = rootProject.file(location);
            if (specificConfigFile.exists()) {
                return specificConfigFile;
            }
        }
        return null;
    }

    private static FileCollection join(FileCollection a, FileCollection b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }

        return a.plus(b);
    }

    private static CheckstyleExtension createExtension(Project project) {
        CheckstyleExtension extension = project.getExtensions().create("checkstyle", CheckstyleExtension.class, project);
        extension.setToolVersion(DEFAULT_CHECKSTYLE_VERSION);

        File configFile = getConfigFile(project);

        if (configFile != null) {
            configureConfigLocation(project, extension, configFile);
        } else {
            configureDefaultConfigLocation(project, extension);
        }

        return extension;
    }

    private static void configureConfigLocation(Project project, CheckstyleExtension extension, File configFile) {
        Directory directory = project.getRootProject().getLayout().getProjectDirectory().dir(configFile.getParentFile().getAbsolutePath());
        extension.getConfigDirectory().convention(directory);
        extension.setConfig(project.getResources().getText().fromFile(extension.getConfigDirectory().file(configFile.getName())
            // If for whatever reason the provider above cannot be resolved, go back to default location, which we know how to ignore if missing
            .orElse(directory.file(configFile.getName()))));
    }

    private static void configureDefaultConfigLocation(Project project, CheckstyleExtension extension) {
        Directory directory = project.getRootProject().getLayout().getProjectDirectory().dir("config/checkstyle");
        extension.getConfigDirectory().convention(directory);
        extension.setConfig(project.getResources().getText().fromFile(extension.getConfigDirectory().file("checkstyle.xml")
            // If for whatever reason the provider above cannot be resolved, go back to default location, which we know how to ignore if missing
            .orElse(directory.file("checkstyle.xml"))));
    }

    private static void configureTaskConventionMapping(CheckstyleExtension extension, Checkstyle task) {
        ConventionMapping taskMapping = task.getConventionMapping();
        taskMapping.map("config", (Callable<TextResource>) extension::getConfig);
        taskMapping.map("configProperties", (Callable<Map<String, Object>>) extension::getConfigProperties);
        taskMapping.map("ignoreFailures", (Callable<Boolean>) extension::isIgnoreFailures);
        taskMapping.map("showViolations", (Callable<Boolean>) extension::isShowViolations);
        taskMapping.map("maxErrors", (Callable<Integer>) extension::getMaxErrors);
        taskMapping.map("maxWarnings", (Callable<Integer>) extension::getMaxWarnings);

        task.getConfigDirectory().convention(extension.getConfigDirectory());
    }

}
