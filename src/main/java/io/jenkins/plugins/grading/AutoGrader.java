package io.jenkins.plugins.grading;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import edu.hm.hafner.util.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.NonNull;

import net.sf.json.JSONException;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pitmutation.PitBuildAction;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.tasks.junit.TestResultAction;
import jenkins.tasks.SimpleBuildStep;

import io.jenkins.plugins.analysis.core.model.ResultAction;
import io.jenkins.plugins.coverage.CoverageAction;
import io.jenkins.plugins.coverage.targets.CoverageElement;
import io.jenkins.plugins.util.LogHandler;

/**
 * This recorder gathers all the needed results of previous run check in the job. Inputs are Saved, and Quality Score is
 * computed on base of defined configurations.
 *
 * @author Eva-Maria Zeintl
 */
public class AutoGrader extends Recorder implements SimpleBuildStep {
    private final String configuration;

    /**
     * Creates a new instance of {@link AutoGrader}.
     *
     * @param configuration
     *         the configuration to use
     */
    @DataBoundConstructor
    public AutoGrader(final String configuration) {
        super();

        this.configuration = configuration;
    }

    public String getConfiguration() {
        return configuration;
    }

    private List<AnalysisScore> createDefaultBase(final List<ResultAction> actions) {
        List<AnalysisScore> defaultBase = new ArrayList<>();
        for (ResultAction action : actions) {
            //store base Results from static checks
            defaultBase.add(new AnalysisScore(action.getId(), action.getResult().getTotalErrorsSize(),
                    action.getResult().getTotalHighPrioritySize(), action.getResult().getTotalNormalPrioritySize(),
                    action.getResult().getTotalLowPrioritySize(), action.getResult().getTotalSize()));
        }
        return defaultBase;
    }

    private List<PITs> createPitBase(final List<PitBuildAction> pitAction) {
        List<PITs> pitBases = new ArrayList<>();
        for (PitBuildAction action : pitAction) {
            //store base Results from mutation check
            pitBases.add(new PITs(action.getDisplayName(), action.getReport().getMutationStats().getTotalMutations(),
                    action.getReport().getMutationStats().getUndetected(),
                    action.getReport().getMutationStats().getKillPercent()));
        }
        return pitBases;
    }

    private List<TestRes> createJunitBase(final List<TestResultAction> testActions) {
        List<TestRes> junitBases = new ArrayList<>();
        for (TestResultAction action : testActions) {
            //store base Results from junit tests
            junitBases.add(
                    new TestRes(action.getDisplayName(), action.getResult().getPassCount(), action.getTotalCount(),
                            action.getResult().getFailCount(), action.getResult().getSkipCount()));
        }
        return junitBases;
    }

    private List<CoCos> createCocoBase(final List<CoverageAction> coverageActions) {
        List<CoCos> cocoBases = new ArrayList<>();
        for (CoverageAction action : coverageActions) {
            //store base Results from code coverage check
            Set<CoverageElement> elements = action.getResult().getElements();
            for (CoverageElement element : elements) {
                cocoBases.add(new CoCos(element.getName(),
                        (int) action.getResult().getCoverage(element).numerator,
                        (int) action.getResult().getCoverage(element).denominator,
                        action.getResult().getCoverage(element).getPercentage()));
            }
        }
        return cocoBases;
    }

    @Override
    public void perform(@NonNull final Run<?, ?> run, @NonNull final FilePath workspace,
            @NonNull final Launcher launcher,
            @NonNull final TaskListener listener) {
        LogHandler logHandler = new LogHandler(listener, "Autograding");
        logHandler.log("Using configuration '%s'", configuration);

        try {
            Score actualScore = new Score();
            JSONObject gradingConfiguration = JSONObject.fromObject(configuration);
            JSONObject analysis = (JSONObject) gradingConfiguration.get("analysis");
            if (analysis != null) {
                AnalysisConfiguration analysisConfiguration = AnalysisConfiguration.from(analysis);
                List<AnalysisScore> scores = new ArrayList<>();
                for (ResultAction action : run.getActions(ResultAction.class)) {
                    logHandler.log("Grading static analysis results for " + action.getLabelProvider().getName());
                    scores.add(new AnalysisScore(analysisConfiguration, action.getResult(), logHandler));
                }
                int total = actualScore.addAnalysisTotal(analysisConfiguration.getMaxScore(), scores);
                logHandler.log("Total score for static analysis results: " + total);
            }
            else {
                logHandler.log("Skipping static analysis results");
            }
            run.addAction(new AutoGradingBuildAction(run, actualScore));
        }
        catch (JSONException exception) {
            throw new IllegalArgumentException("Invalid configuration: " + configuration);
        }

//        List<ResultAction> actions = run.getActions(ResultAction.class);
//
//        listener.getLogger().println("[CodeQuality] Starting extraction of previous performed checks");
//        List<PitBuildAction> pitAction = run.getActions(PitBuildAction.class);
//        List<TestResultAction> testActions = run.getActions(TestResultAction.class);
//        List<CoverageAction> coverageActions = run.getActions(CoverageAction.class);
//
//        // read configs from XML File
//        ConfigXmlStream configReader = new ConfigXmlStream();
//        Configuration configs = configReader.read(Paths.get(workspace.child("auto-grading.xml").getRemote()));
//        listener.getLogger().println("[CodeQuality] Read Configs:");
//        listener.getLogger().println("[CodeQuality] Configs read successfully.");
//
//        Score score = new Score(configs.getMaxScore());
//        score.addConfigs(configs);
//
//        //Defaults Rechnen
//        if (actions.isEmpty()) {
//            score.addToScore(-configs.getdMaxScore());
//        }
//        else {
//            List<AnalysisScore> defaultBase = createDefaultBase(actions);
//            updateAnalysisGrade(new AnalysisConfigurationBuilder().build(), score, defaultBase, listener);
//        }
//
////        PIT lesen und rechnen
//        if (pitAction.isEmpty()) {
//            score.addToScore(-configs.getpMaxScore());
//        }
//        else {
//            List<PITs> pitBases = createPitBase(pitAction);
//            updatePitGrade(configs, score, pitBases, listener);
//        }
//
//        //JUNIT lesen und rechnen
//        if (testActions.isEmpty()) {
//            score.addToScore(-configs.getjMaxScore());
//        }
//        else {
//            List<TestRes> junitBases = createJunitBase(testActions);
//            updateJunitGrade(configs, score, junitBases, listener);
//        }
//
//        //code-coverage lesen und rechnen
//        if (coverageActions.isEmpty()) {
//            score.addToScore(-configs.getcMaxScore());
//        }
//        else {
//            List<CoCos> cocoBases = createCocoBase(coverageActions);
//            updateCocoGrade(configs, score, cocoBases, listener);
//        }
//
//        listener.getLogger().println("[CodeQuality] Code Quality Score calculation completed");
//
//        run.addAction(new AutoGradingBuildAction(run, score));
    }

    private void updateCocoGrade(final Configuration configs, final Score score, final List<CoCos> cocoBases,
            @NonNull final TaskListener listener) {
        int change = 0;
        for (CoCos base : cocoBases) {
            change = change + base.calculate(configs, listener);

            score.addCocoBase(base);
            listener.getLogger().println("[CodeQuality] Saved Code Coverage Base Results");
        }

        if (configs.getcMaxScore() + change >= 0 && change < 0) {
            score.addToScore(change);
            listener.getLogger().println("[CodeQuality] Updated Score by Code Coverage Delta");
        }
        else if (configs.getcMaxScore() + change < 0) {
            score.addToScore(-configs.getcMaxScore());
            listener.getLogger().println("[CodeQuality] Updated Score by Code Coverage Delta");
        }

    }

    private void updateAnalysisGrade(final AnalysisConfiguration configs, final Score score,
            final List<AnalysisScore> defaultBase, @NonNull final TaskListener listener) {
        int change = 0;
        for (AnalysisScore base : defaultBase) {
            change = change + base.calculate(configs, listener);
            score.addAnalysisScore(base);
            listener.getLogger().println("[CodeQuality] Saved Static Analysis Base Results");
        }

        if (configs.getMaxScore() + change >= 0 && change < 0) {
            score.addToScore(change);
            listener.getLogger().println("[CodeQuality] Updated Score by Static Analysis Delta");
        }
        else if (configs.getMaxScore() + change < 0) {
            score.addToScore(-configs.getMaxScore());
            listener.getLogger().println("[CodeQuality] Updated Score by Static Analysis Delta");
        }

    }

    private void updatePitGrade(final Configuration configs, final Score score,
            final List<PITs> pitBases, final @NonNull TaskListener listener) {
        int change = 0;
        for (PITs base : pitBases) {
            change = change + base.calculate(configs, listener);
            score.addPitBase(base);
            listener.getLogger().println("[CodeQuality] Saved pitmuation Base Results");
        }
        if (configs.getpMaxScore() + change >= 0 && change < 0) {
            score.addToScore(change);
            listener.getLogger().println("[CodeQuality] Updated Score by pitmutation Delta");
        }
        else if (configs.getpMaxScore() + change < 0) {
            score.addToScore(-configs.getpMaxScore());
            listener.getLogger().println("[CodeQuality] Updated Score by pitmutation Delta");
        }

    }

    private void updateJunitGrade(final Configuration configs, final Score score, final List<TestRes> junitBases,
            @NonNull final TaskListener listener) {
        int change = 0;
        for (TestRes base : junitBases) {
            change = change + base.calculate(configs, listener);
            score.addJunitBase(base);
            listener.getLogger().println("[CodeQuality] Saved Junit Base Results");
        }
        if (configs.getjMaxScore() + change >= 0 && change < 0) {
            score.addToScore(change);
            listener.getLogger().println("[CodeQuality] Updated Score by junit Delta");
        }
        else if (configs.getjMaxScore() + change < 0) {
            score.addToScore(-configs.getjMaxScore());
            listener.getLogger().println("[CodeQuality] Updated Score by junit Delta");
        }

    }

    @VisibleForTesting
    void updateCocoGrade(final Configuration configs, final List<CoCos> cocoBases, final Score score) {
        updateCocoGrade(configs, score, cocoBases, TaskListener.NULL);
    }

    @VisibleForTesting
    void updateAnalysisGrade(final AnalysisConfiguration configs, final Score score,
            final List<AnalysisScore> defaultBase) {
        updateAnalysisGrade(configs, score, defaultBase, TaskListener.NULL);
    }

    @VisibleForTesting
    void updatePitGrade(final Configuration configs, final Score score,
            final List<PITs> pitBases) {
        updatePitGrade(configs, score, pitBases, TaskListener.NULL);
    }

    @VisibleForTesting
    void updateJunitGrade(final Configuration configs, final Score score, final List<TestRes> junitBases) {
        updateJunitGrade(configs, score, junitBases, TaskListener.NULL);
    }

    /** Descriptor for this step. */
    @Extension(ordinal = -100_000)
    @Symbol("autoGrade")
    @SuppressWarnings("unused") // most methods are used by the corresponding jelly view
    public static class Descriptor extends BuildStepDescriptor<Publisher> {
        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.Action_Name();
        }

        @Override
        public boolean isApplicable(final Class<? extends AbstractProject> jobType) {
            return true;
        }
    }
}
