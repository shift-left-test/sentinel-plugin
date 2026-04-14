package io.jenkins.plugins.sentinel;

import java.util.Set;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.sentinel.config.SentinelConfiguration;
import io.jenkins.plugins.sentinel.config.ThresholdAction;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Pipeline step: sentinelPipeline.
 * Orchestrates distributed mutation testing with a user-provided closure.
 *
 * <p>Usage:</p>
 * <pre>
 * script {
 *     sentinelPipeline(
 *         buildCommand: 'make all',
 *         testCommand: 'make test',
 *         testResultDir: 'test-results/',
 *         partitions: 4,
 *         nodeLabel: 'linux',
 *         outputDir: 'sentinel-report',
 *         threshold: 80.0,
 *         thresholdAction: 'UNSTABLE'
 *     ) {
 *         docker.image('build-env').inside {
 *             checkout scm
 *             sh 'cmake . &amp;&amp; make'
 *             sentinelRun()
 *         }
 *     }
 * }
 * </pre>
 */
public class SentinelPipelineStep extends SentinelStepBase {

    private static final long serialVersionUID = 1L;

    private int partitions = 1;
    private String nodeLabel = "";
    private Double threshold;
    private String thresholdAction;

    /**
     * Creates a new SentinelPipelineStep with required fields.
     *
     * @param buildCommand  build command
     * @param testCommand   test command
     * @param testResultDir test result directory
     */
    @DataBoundConstructor
    public SentinelPipelineStep(
            final String buildCommand,
            final String testCommand,
            final String testResultDir) {
        super(buildCommand, testCommand, testResultDir);
    }

    /**
     * Returns the number of partitions.
     *
     * @return partitions count
     */
    public int getPartitions() {
        return partitions;
    }

    /**
     * Returns the node label.
     *
     * @return node label
     */
    public String getNodeLabel() {
        return nodeLabel;
    }

    /**
     * Returns the threshold percentage.
     *
     * @return threshold
     */
    public Double getThreshold() {
        return threshold;
    }

    /**
     * Returns the threshold action name.
     *
     * @return threshold action name
     */
    public String getThresholdAction() {
        return thresholdAction;
    }

    /**
     * Sets the number of partitions.
     *
     * @param v partitions count
     */
    @DataBoundSetter
    public void setPartitions(final int v) {
        partitions = v;
    }

    /**
     * Sets the node label.
     *
     * @param v node label
     */
    @DataBoundSetter
    public void setNodeLabel(final String v) {
        nodeLabel = v;
    }

    /**
     * Sets the threshold percentage.
     *
     * @param v threshold
     */
    @DataBoundSetter
    public void setThreshold(final Double v) {
        threshold = v;
    }

    /**
     * Sets the threshold action name.
     *
     * @param v threshold action name
     */
    @DataBoundSetter
    public void setThresholdAction(final String v) {
        thresholdAction = v;
    }

    /**
     * Converts this step's fields into a SentinelConfiguration.
     *
     * @return populated SentinelConfiguration
     */
    public SentinelConfiguration toConfiguration() {
        final SentinelConfiguration c = new SentinelConfiguration();
        populateConfiguration(c);
        c.setPartitions(partitions);
        c.setNodeLabel(nodeLabel);
        if (threshold != null) {
            c.setThreshold(threshold);
        }
        if (thresholdAction != null) {
            c.setThresholdAction(
                    ThresholdAction.fromString(thresholdAction));
        }
        return c;
    }

    @Override
    public StepExecution start(final StepContext context) {
        return new SentinelOrchestrator(context, this);
    }

    /**
     * Descriptor for the sentinelPipeline step.
     */
    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "sentinelPipeline";
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return "Distributed mutation testing";
        }

        /**
         * Fills the thresholdAction dropdown.
         *
         * @return list of threshold action options
         */
        public ListBoxModel doFillThresholdActionItems() {
            final ListBoxModel items = new ListBoxModel();
            items.add("", "");
            for (final ThresholdAction action : ThresholdAction.values()) {
                items.add(action.name(), action.name());
            }
            return items;
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(
                    FilePath.class, Launcher.class,
                    TaskListener.class, EnvVars.class,
                    Run.class);
        }
    }
}
