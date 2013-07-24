package org.jenkinsci.plugins.vstest_runner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import hudson.CopyOnWrite;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Result;
import hudson.tasks.Builder;
import hudson.tools.ToolInstallation;
import hudson.util.ArgumentListBuilder;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 *
 * @author Yasuyuki Saito
 */
public class VsTestBuilder extends Builder {

    /** Platform:x86 */
    private static final String PLATFORM_X86 = "x86";

    /** Platform:x64 */
    private static final String PLATFORM_X64 = "x64";

    /** Platform:ARM */
    private static final String PLATFORM_ARM = "ARM";

    /** Platform:Other */
    private static final String PLATFORM_OTHER = "Other";

    /** .NET Framework 3.5 */
    private static final String FRAMEWORK_35 = "framework35";

    /** .NET Framework 4.0 */
    private static final String FRAMEWORK_40 = "framework40";

    /** .NET Framework 4.5 */
    private static final String FRAMEWORK_45 = "framework45";

    /** .NET Framework Other */
    private static final String FRAMEWORK_OTHER = "Other";

    /** Logger TRX */
    private static final String LOGGER_TRX = "trx";

    /** Logger Other */
    private static final String LOGGER_OTHER = "Other";


    private final String vsTestName;
    private final String testFiles;
    private final String settings;
    private final String tests;
    private final String testCaseFilter;
    private final boolean enablecodecoverage;
    private final boolean inIsolation;
    private final boolean useVsixExtensions;
    private final String platform;
    private final String otherPlatform;
    private final String framework;
    private final String otherFramework;
    private final String logger;
    private final String otherLogger;
    private final String cmdLineArgs;
    private final boolean failBuild;

    /**
     *
     * @param vsTestName
     * @param testFiles
     * @param settings
     * @param tests
     * @param testCaseFilter
     * @param enablecodecoverage
     * @param inIsolation
     * @param useVsixExtensions
     * @param platform
     * @param otherPlatform
     * @param framework
     * @param otherFramework
     * @param logger
     * @param otherLogger
     * @param cmdLineArgs
     * @param failBuild
     */
    @DataBoundConstructor
    public VsTestBuilder(String vsTestName, String testFiles, String settings, String tests, String testCaseFilter
                        ,boolean enablecodecoverage, boolean inIsolation, boolean useVsixExtensions, String platform, String otherPlatform
                        ,String framework, String otherFramework, String logger, String otherLogger
                        ,String cmdLineArgs, boolean failBuild) {
        this.vsTestName         = vsTestName;
        this.testFiles          = testFiles;
        this.settings           = settings;
        this.tests              = tests;
        this.testCaseFilter     = testCaseFilter;
        this.enablecodecoverage = enablecodecoverage;
        this.inIsolation        = inIsolation;
        this.useVsixExtensions  = useVsixExtensions;
        this.platform           = platform;
        this.otherPlatform      = PLATFORM_OTHER.equals(this.platform) ? otherPlatform : "";
        this.framework          = framework;
        this.otherFramework     = FRAMEWORK_OTHER.equals(this.framework) ? otherFramework : "";
        this.logger             = logger;
        this.otherLogger        = LOGGER_OTHER.equals(this.logger) ? otherLogger : "";
        this.cmdLineArgs        = cmdLineArgs;
        this.failBuild          = failBuild;
    }

    public String getVsTestName() {
        return vsTestName;
    }

    public String getTestFiles() {
        return testFiles;
    }

    public String getSettings() {
        return settings;
    }

    public String getTests() {
        return tests;
    }

    public boolean isEnablecodecoverage() {
        return enablecodecoverage;
    }

    public boolean isInIsolation() {
        return inIsolation;
    }

    public boolean isUseVsixExtensions() {
        return useVsixExtensions;
    }

    public String getPlatform() {
        return platform;
    }

    public String getOtherPlatform() {
        return otherPlatform;
    }

    public String getFramework() {
        return framework;
    }

    public String getOtherFramework() {
        return otherFramework;
    }

    public String getTestCaseFilter() {
        return testCaseFilter;
    }

    public String getLogger() {
        return logger;
    }

    public String getOtherLogger() {
        return otherLogger;
    }

    public String getCmdLineArgs() {
        return cmdLineArgs;
    }

    public boolean isFailBuild() {
        return failBuild;
    }

    public VsTestInstallation getVsTest() {
        for (VsTestInstallation i : DESCRIPTOR.getInstallations()) {
            if (vsTestName != null && i.getName().equals(vsTestName)) {
                return i;
            }
        }
        return null;
    }

    @Override
    public Descriptor<Builder> getDescriptor() {
         return DESCRIPTOR;
    }

    /**
     * Descriptor should be singleton.
     */
    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    /**
     *
     * @author Yasuyuki Saito
     */
    public static final class DescriptorImpl extends Descriptor<Builder> {

        @CopyOnWrite
        private volatile VsTestInstallation[] installations = new VsTestInstallation[0];

        DescriptorImpl() {
            super(VsTestBuilder.class);
            load();
        }

        public String getDisplayName() {
            return Messages.VsTestBuilder_DisplayName();
        }

        public VsTestInstallation[] getInstallations() {
            return installations;
        }

        public void setInstallations(VsTestInstallation... antInstallations) {
            this.installations = antInstallations;
            save();
        }

        /**
         * Obtains the {@link VsTestInstallation.DescriptorImpl} instance.
         */
        public VsTestInstallation.DescriptorImpl getToolDescriptor() {
            return ToolInstallation.all().get(VsTestInstallation.DescriptorImpl.class);
        }
    }

    /**
     *
     */
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        ArrayList<String> args = new ArrayList<String>();

        EnvVars env = build.getEnvironment(listener);

        // VsTest.console.exe path.
        String pathToVsTest = getVsTestPath(launcher, listener, env);
        if (pathToVsTest == null) return false;
        args.add(pathToVsTest);

        // Tareget dll path
        if (!StringUtils.isBlank(testFiles))
            args.addAll(getTestFilesArguments(build, env));

        // Run tests with additional settings such as data collectors.
        if (!StringUtils.isBlank(settings))
            args.add(convertArgumentWithQuote("Settings", replaceMacro(settings, env, build)));

        // Run tests with names that match the provided values.
        if (!StringUtils.isBlank(tests))
            args.add(convertArgument("Tests", replaceMacro(tests, env, build)));

        // Run tests that match the given expression.
        if (!StringUtils.isBlank(testCaseFilter))
            args.add(convertArgumentWithQuote("TestCaseFilter", replaceMacro(testCaseFilter, env, build)));

        // Enables data diagnostic adapter CodeCoverage in the test run.
        if (enablecodecoverage)
            args.add("/Enablecodecoverage");

        // Runs the tests in an isolated process.
        if (inIsolation)
            args.add("/InIsolation");

        // This makes vstest.console.exe process use or skip the VSIX extensions installed (if any) in the test run.
        if (useVsixExtensions)
            args.add("/UseVsixExtensions:true");
        else
            args.add("/UseVsixExtensions:false");

        // Target platform architecture to be used for test execution.
        String platformArg = getPlatformArgument(env, build);
        if (!StringUtils.isBlank(platformArg))
            args.add(convertArgument("Platform", platformArg));

        // Target .NET Framework version to be used for test execution.
        String frameworkArg = getFrameworkArgument(env, build);
        if (!StringUtils.isBlank(frameworkArg))
            args.add(convertArgument("Framework", frameworkArg));

        // Specify a logger for test results.
        String loggerArg = getLoggerArgument(env, build);
        if (!StringUtils.isBlank(loggerArg))
            args.add(convertArgument("Logger", loggerArg));

        // Manual Command Line String
        if (!StringUtils.isBlank(cmdLineArgs))
            args.add(replaceMacro(cmdLineArgs, env, build));

        // VSTest run.
        boolean r = execVsTest(args, build, launcher, listener, env);

        return r;
    }

    /**
     *
     * @param value
     * @param env
     * @param build
     * @return
     */
    private String replaceMacro(String value, EnvVars env, AbstractBuild<?, ?> build) {
        String result = Util.replaceMacro(value, env);
        result = Util.replaceMacro(result, build.getBuildVariables());
        return result;
    }

    /**
     *
     * @param launcher
     * @param listener
     * @param env
     * @return
     * @throws InterruptedException
     * @throws IOException
     */
    private String getVsTestPath(Launcher launcher, BuildListener listener, EnvVars env) throws InterruptedException, IOException {

        String execName = "vstest.console.exe";

        VsTestInstallation installation = getVsTest();
        if (installation == null) {
            listener.getLogger().println("Path To VSTest.console.exe: " + execName);
            return execName;
        } else {
            installation = installation.forNode(Computer.currentComputer().getNode(), listener);
            installation = installation.forEnvironment(env);
            String pathToVsTest = installation.getHome();
            FilePath exec = new FilePath(launcher.getChannel(), pathToVsTest);

            try {
                if (!exec.exists()) {
                    listener.fatalError(pathToVsTest + " doesn't exist");
                    return null;
                }
            } catch (IOException e) {
                listener.fatalError("Failed checking for existence of " + pathToVsTest);
                return null;
            }

            listener.getLogger().println("Path To VSTest.console.exe: " + pathToVsTest);
            return appendQuote(pathToVsTest);
        }
    }


    /**
     *
     * @param build
     * @param env
     * @return
     * @throws InterruptedException
     * @throws IOException
     */
    private List<String> getTestFilesArguments(AbstractBuild<?, ?> build, EnvVars env) throws InterruptedException, IOException {
        ArrayList<String> args = new ArrayList<String>();

        StringTokenizer testFilesToknzr = new StringTokenizer(testFiles, " \t\r\n");

        while (testFilesToknzr.hasMoreTokens()) {
            String testFile = testFilesToknzr.nextToken();
            testFile = replaceMacro(testFile, env, build);

            if (!StringUtils.isBlank(testFile)) {
                args.add(appendQuote(testFile));
            }
        }

        return args;
    }

    /**
     *
     * @param env
     * @param build
     * @return
     */
    private String getPlatformArgument(EnvVars env, AbstractBuild<?, ?> build) {
        if (PLATFORM_X86.equals(platform) || PLATFORM_X64.equals(platform) || PLATFORM_ARM.equals(platform))
            return platform;
        else if (PLATFORM_OTHER.equals(platform))
            return replaceMacro(otherPlatform, env, build);
        else
            return null;
    }

    /**
     *
     * @param env
     * @param build
     * @return
     */
    private String getFrameworkArgument(EnvVars env, AbstractBuild<?, ?> build) {
        if (FRAMEWORK_35.equals(framework) || FRAMEWORK_40.equals(framework) || FRAMEWORK_45.equals(framework))
            return framework;
        else if (FRAMEWORK_OTHER.equals(framework))
            return replaceMacro(otherFramework, env, build);
        else
            return null;
    }

    /**
     *
     * @param env
     * @param build
     * @return
     */
    private String getLoggerArgument(EnvVars env, AbstractBuild<?, ?> build) {
        if (LOGGER_TRX.equals(logger))
            return logger;
        else if (LOGGER_OTHER.equals(logger))
            return replaceMacro(otherLogger, env, build);
        else
            return null;
    }

    /**
     *
     * @param args
     * @param build
     * @param launcher
     * @param listener
     * @param env
     * @return
     * @throws InterruptedException
     * @throws IOException
     */
    private boolean execVsTest(List<String> args, AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, EnvVars env) throws InterruptedException, IOException {
        ArgumentListBuilder cmdExecArgs = new ArgumentListBuilder();
        FilePath tmpDir = null;
        FilePath pwd = build.getWorkspace();

        if (!launcher.isUnix()) {
            tmpDir = pwd.createTextTempFile("vstest", ".bat", concatString(args), false);
            cmdExecArgs.add("cmd.exe", "/C", tmpDir.getRemote(), "&&", "exit", "%ERRORLEVEL%");
        } else {
            for (String arg : args) {
                cmdExecArgs.add(arg);
            }
        }

        listener.getLogger().println("Executing VSTest: " + cmdExecArgs.toStringWithQuote());

        try {
            int r = launcher.launch().cmds(cmdExecArgs).envs(env).stdout(listener).pwd(pwd).join();

            if (failBuild)
                return (r == 0);
            else {
                if (r != 0)
                    build.setResult(Result.UNSTABLE);
                return true;
            }
        } catch (IOException e) {
            Util.displayIOException(e, listener);
            e.printStackTrace(listener.fatalError("VSTest command execution failed"));
            return false;
        } finally {
            try {
                if (tmpDir != null) tmpDir.delete();
            } catch (IOException e) {
                Util.displayIOException(e, listener);
                e.printStackTrace(listener.fatalError("temporary file delete failed"));
            }
        }
    }

    /**
     *
     * @param option
     * @param param
     * @return
     */
    private String convertArgument(String option, String param) {
        return String.format("/%s:%s", option, param);
    }

    /**
     *
     * @param option
     * @param param
     * @return
     */
    private String convertArgumentWithQuote(String option, String param) {
        return String.format("/%s:\"%s\"", option, param);
    }

    /**
     *
     * @param value
     * @return
     */
    private String appendQuote(String value) {
        return String.format("\"%s\"", value);
    }

    /**
     *
     * @param args
     * @return
     */
    private String concatString(List<String> args) {
        StringBuilder buf = new StringBuilder();
        for (String arg : args) {
            if(buf.length() > 0)  buf.append(' ');
            buf.append(arg);
        }
        return buf.toString();
    }
}
