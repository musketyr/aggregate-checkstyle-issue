package com.agorapulse.gradle.root.checkstyle

import com.agorapulse.testing.fixt.Fixt
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir

class AggregateCheckstyleReportsPluginSpec extends Specification {

    @Shared
    Fixt fixt = Fixt.create(this)

    @TempDir
    File projectRoot

    void setup() {
        fixt.copyTo('extension-test', projectRoot)
    }

    void 'integration test'() {
        when:
            BuildResult result = GradleRunner
                .create()
                .withProjectDir(projectRoot)
                .forwardOutput()
                .withPluginClasspath()
//                .withDebug(true)
                .withArguments(
                    AggregateCheckstyleReportsPlugin.AGGREGATE_CHECKSTYLE_TASK_NAME,
//                    System.getenv('CI') ? '' : '--scan',
//                    '--info',
                    '--stacktrace'
                )
                .buildAndFail()
        then:
            // expect some errors
            result.task(':' + AggregateCheckstyleReportsPlugin.AGGREGATE_CHECKSTYLE_TASK_NAME).outcome == TaskOutcome.FAILED

        when:
            File reportFile = new File(projectRoot, 'build/reports/checkstyle/aggregate.html')
        then:
            reportFile.exists()
            reportFile.text.contains('other')
            reportFile.text.contains('pkg')
    }

}
