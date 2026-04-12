package io.jenkins.plugins.sentinel;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Action;
import hudson.model.Job;
import hudson.model.Run;
import jenkins.model.TransientActionFactory;

/**
 * Factory that automatically attaches {@link SentinelProjectAction}
 * to jobs that have builds with mutation testing results.
 *
 * <p>Jenkins calls {@link #createFor(Job)} for each job. If the
 * last completed build has a {@link SentinelBuildAction}, the
 * factory returns a {@link SentinelProjectAction} for the job.</p>
 */
@Extension
public class SentinelProjectActionFactory
        extends TransientActionFactory<Job> {

    /** {@inheritDoc} */
    @Override
    public Class<Job> type() {
        return Job.class;
    }

    /** {@inheritDoc} */
    @Override
    @NonNull
    public Collection<? extends Action> createFor(
            @NonNull final Job target) {
        final Run<?, ?> lastBuild = target.getLastCompletedBuild();
        if (lastBuild == null) {
            return Collections.emptyList();
        }

        final SentinelBuildAction action =
                lastBuild.getAction(SentinelBuildAction.class);
        if (action == null) {
            return Collections.emptyList();
        }

        return List.of(new SentinelProjectAction(target));
    }
}
