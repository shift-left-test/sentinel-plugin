package io.jenkins.plugins.sentinel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import hudson.model.Job;
import hudson.model.Run;
import io.jenkins.plugins.sentinel.model.MutationScore;
import io.jenkins.plugins.sentinel.model.SentinelResult;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.junit.jupiter.api.Test;

@SuppressWarnings("unchecked")
class SentinelProjectActionTest {

    private static final int KILLED_12 = 12;
    private static final int SURVIVED_8 = 8;
    private static final int SKIPPED_2 = 2;
    private static final int KILLED_18 = 18;
    private static final int SURVIVED_2 = 2;
    private static final int SKIPPED_1 = 1;
    private static final int BUILD_1 = 1;
    private static final int BUILD_2 = 2;

    @Test
    void trendDataCollectsFromRecentBuilds() {
        final Job<?, ?> job = mock(Job.class);
        final Run<?, ?> build2 = mockBuild(BUILD_2,
                KILLED_18, SURVIVED_2, SKIPPED_1, null);
        final Run<?, ?> build1 = mockBuild(BUILD_1,
                KILLED_12, SURVIVED_8, SKIPPED_2, build2);
        when(job.getLastBuild()).thenReturn((Run) build1);

        final SentinelProjectAction action = new SentinelProjectAction(job);
        final String json = action.getTrendDataJson();
        final JSONArray array = JSONArray.fromObject(json);

        assertThat(array.size()).isEqualTo(2);
    }

    @Test
    void trendDataReturnsChronologicalOrder() {
        final Job<?, ?> job = mock(Job.class);
        final Run<?, ?> build2 = mockBuild(BUILD_2,
                KILLED_18, SURVIVED_2, SKIPPED_1, null);
        final Run<?, ?> build1 = mockBuild(BUILD_1,
                KILLED_12, SURVIVED_8, SKIPPED_2, build2);
        when(job.getLastBuild()).thenReturn((Run) build1);

        final SentinelProjectAction action = new SentinelProjectAction(job);
        final String json = action.getTrendDataJson();
        final JSONArray array = JSONArray.fromObject(json);

        final JSONObject first = array.getJSONObject(0);
        final JSONObject second = array.getJSONObject(1);
        assertThat(first.getInt("buildNumber")).isEqualTo(BUILD_2);
        assertThat(second.getInt("buildNumber")).isEqualTo(BUILD_1);
    }

    @Test
    void trendDataEmptyWhenNoBuilds() {
        final Job<?, ?> job = mock(Job.class);
        when(job.getLastBuild()).thenReturn(null);

        final SentinelProjectAction action = new SentinelProjectAction(job);
        assertThat(action.getTrendDataJson()).isEqualTo("[]");
    }

    @Test
    void trendDataSkipsBuildWithoutAction() {
        final Job<?, ?> job = mock(Job.class);
        final Run<?, ?> buildWithout = mock(Run.class);
        when(buildWithout.getAction(SentinelBuildAction.class)).thenReturn(null);
        when(buildWithout.getPreviousBuild()).thenReturn(null);
        when(buildWithout.getNumber()).thenReturn(BUILD_2);

        final Run<?, ?> buildWith = mockBuild(BUILD_1,
                KILLED_12, SURVIVED_8, SKIPPED_2, buildWithout);
        when(job.getLastBuild()).thenReturn((Run) buildWith);

        final SentinelProjectAction action = new SentinelProjectAction(job);
        final String json = action.getTrendDataJson();
        final JSONArray array = JSONArray.fromObject(json);

        assertThat(array.size()).isEqualTo(1);
        assertThat(array.getJSONObject(0).getInt("buildNumber"))
                .isEqualTo(BUILD_1);
    }

    @Test
    void actionMetadata() {
        final Job<?, ?> job = mock(Job.class);
        final SentinelProjectAction action = new SentinelProjectAction(job);

        assertThat(action.getIconFileName()).isEqualTo("graph.png");
        assertThat(action.getDisplayName()).isEqualTo("Sentinel Trend Report");
        assertThat(action.getUrlName()).isEqualTo("sentinel-trend");
    }

    @Test
    void getJobReturnsConstructorArgument() {
        final Job<?, ?> job = mock(Job.class);
        final SentinelProjectAction action = new SentinelProjectAction(job);
        assertThat(action.getJob()).isSameAs(job);
    }

    private Run<?, ?> mockBuild(final int number,
                                final int killed,
                                final int survived,
                                final int skipped,
                                final Run<?, ?> previousBuild) {
        final Run<?, ?> build = mock(Run.class);
        when(build.getNumber()).thenReturn(number);
        when(build.getPreviousBuild()).thenReturn((Run) previousBuild);

        final MutationScore score = new MutationScore(killed, survived, skipped);
        final SentinelResult result = new SentinelResult(
                score, List.of(), List.of());
        final SentinelBuildAction buildAction = new SentinelBuildAction(result);
        when(build.getAction(SentinelBuildAction.class)).thenReturn(buildAction);

        return build;
    }
}
