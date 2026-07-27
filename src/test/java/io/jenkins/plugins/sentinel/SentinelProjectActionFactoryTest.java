/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;

import hudson.model.Action;
import hudson.model.Job;
import hudson.model.Run;
import io.jenkins.plugins.sentinel.model.MutationScore;
import io.jenkins.plugins.sentinel.model.SentinelResult;
import org.junit.jupiter.api.Test;

@SuppressWarnings("unchecked")
class SentinelProjectActionFactoryTest {

    @Test
    void createsActionWhenBuildHasSentinelAction() {
        final Job<?, ?> job = mock(Job.class);
        final Run<?, ?> build = mock(Run.class);
        when(job.getLastCompletedBuild()).thenReturn((Run) build);
        when(build.getAction(SentinelBuildAction.class))
                .thenReturn(newBuildAction());

        final SentinelProjectActionFactory factory =
                new SentinelProjectActionFactory();
        final Collection<? extends Action> actions = factory.createFor(job);

        assertThat(actions).hasSize(1);
        assertThat(actions.iterator().next())
                .isInstanceOf(SentinelProjectAction.class);
    }

    @Test
    void returnsEmptyWhenNoBuildHasSentinelAction() {
        final Job<?, ?> job = mock(Job.class);
        final Run<?, ?> build = mock(Run.class);
        when(job.getLastCompletedBuild()).thenReturn((Run) build);
        when(build.getAction(SentinelBuildAction.class)).thenReturn(null);

        final SentinelProjectActionFactory factory =
                new SentinelProjectActionFactory();
        final Collection<? extends Action> actions = factory.createFor(job);

        assertThat(actions).isEmpty();
    }

    @Test
    void createsActionWhenOlderBuildHasSentinelAction() {
        final Job<?, ?> job = mock(Job.class);
        final Run<?, ?> last = mock(Run.class);
        final Run<?, ?> older = mock(Run.class);
        when(job.getLastCompletedBuild()).thenReturn((Run) last);
        when(last.getAction(SentinelBuildAction.class)).thenReturn(null);
        when(last.getPreviousBuild()).thenReturn((Run) older);
        when(older.getAction(SentinelBuildAction.class))
                .thenReturn(newBuildAction());

        final SentinelProjectActionFactory factory =
                new SentinelProjectActionFactory();
        final Collection<? extends Action> actions = factory.createFor(job);

        assertThat(actions).hasSize(1);
        assertThat(actions.iterator().next())
                .isInstanceOf(SentinelProjectAction.class);
    }

    @Test
    void cachesDecisionUntilNextCompletedBuild() {
        final Job<?, ?> job = mock(Job.class);
        final Run<?, ?> build = mock(Run.class);
        when(job.getLastCompletedBuild()).thenReturn((Run) build);
        when(build.getAction(SentinelBuildAction.class))
                .thenReturn(newBuildAction());

        final SentinelProjectActionFactory factory =
                new SentinelProjectActionFactory();
        assertThat(factory.createFor(job)).hasSize(1);
        assertThat(factory.createFor(job)).hasSize(1);

        // Second call must hit the cache, not walk the history again.
        verify(build, times(1)).getAction(SentinelBuildAction.class);
    }

    @Test
    void returnsEmptyWhenNoBuilds() {
        final Job<?, ?> job = mock(Job.class);
        when(job.getLastCompletedBuild()).thenReturn(null);

        final SentinelProjectActionFactory factory =
                new SentinelProjectActionFactory();
        final Collection<? extends Action> actions = factory.createFor(job);

        assertThat(actions).isEmpty();
    }

    @Test
    void typeReturnsJobClass() {
        final SentinelProjectActionFactory factory =
                new SentinelProjectActionFactory();
        assertThat(factory.type()).isEqualTo(Job.class);
    }

    private static SentinelBuildAction newBuildAction() {
        final MutationScore score = new MutationScore(1, 0, 0);
        final SentinelResult result = new SentinelResult(
                score, List.of(), List.of());
        return new SentinelBuildAction(result);
    }
}
