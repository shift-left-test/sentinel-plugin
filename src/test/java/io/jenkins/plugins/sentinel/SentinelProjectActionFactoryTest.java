/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;

import hudson.model.Action;
import hudson.model.Job;
import hudson.model.Run;
import io.jenkins.plugins.sentinel.model.MutationScore;
import io.jenkins.plugins.sentinel.model.SentinelResult;
import org.junit.jupiter.api.Test;

class SentinelProjectActionFactoryTest {

    @Test
    @SuppressWarnings("unchecked")
    void createsActionWhenBuildHasSentinelAction() {
        final Job<?, ?> job = mock(Job.class);
        final Run<?, ?> build = mock(Run.class);
        when(job.getLastCompletedBuild()).thenReturn((Run) build);

        final MutationScore score = new MutationScore(1, 0, 0);
        final SentinelResult result = new SentinelResult(
                score, List.of(), List.of());
        final SentinelBuildAction buildAction = new SentinelBuildAction(result);
        when(build.getAction(SentinelBuildAction.class)).thenReturn(buildAction);

        final SentinelProjectActionFactory factory =
                new SentinelProjectActionFactory();
        final Collection<? extends Action> actions = factory.createFor(job);

        assertThat(actions).hasSize(1);
        assertThat(actions.iterator().next())
                .isInstanceOf(SentinelProjectAction.class);
    }

    @Test
    @SuppressWarnings("unchecked")
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
}
