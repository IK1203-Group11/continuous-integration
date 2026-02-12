import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * This class is responsible for executing the core CI build steps.
 *  - clone a repository
 *  - checkout the given branch
 *  - run Maven tests
 *
 * The result of the build is returned as a boolean value.
 */
public class BuildExecutor {

    // Stores the build id of the most recent build (used for linking logs in the CI server).
    private volatile String lastBuildId = null;

    // Reference to the MetricsService to record build metrics after each build.
    private final MetricsService metricsService;

    // base directory for builds (log storage)
    private final Path buildsBaseDir;

    /**
     * Creates a BuildExecutor using the default "builds" directory
     * as the base location for storing build logs.
     *
     * This constructor is used in production by the CI server.
     *
     * @param metricsService MetricsService instance used to record build metrics
     */
    public BuildExecutor(MetricsService metricsService) {
        this(metricsService, Path.of("builds"));
    }

    /**
     * Creates a BuildExecutor with a custom base directory
     * for storing build logs.
     *
     * This constructor is primarily intended for testing,
     * allowing isolation from the production "builds" directory.
     *
     * @param metricsService MetricsService instance used to record build metrics
     * @param buildsBaseDir the base directory where build logs will be written
     */
    public BuildExecutor(MetricsService metricsService, Path buildsBaseDir) {
        this.metricsService = metricsService;
        this.buildsBaseDir = buildsBaseDir;
    }

    /**
     * Runs the CI build for a given repository and branch, while recording metrics about the build execution.
     * @param cloneURL
     * @param branch
     * @return
     * @throws Exception
     */
    public boolean runBuild(String cloneURL, String branch) throws Exception {
        long startTime = System.currentTimeMillis();
        boolean success;

        // Run the build and catch any exceptions to ensure metrics are recorded even if the build fails unexpectedly.
        try {
            success = runBuildInterval(cloneURL, branch);
        } catch (Exception e) {
            success = false;
            long buildEndTime = System.currentTimeMillis();
            metricsService.recordBuild(false,
                    (int) (buildEndTime - startTime),
                    (int) buildEndTime);
            throw e;

        }
        // Record the build result and duration in the MetricsService after the build completes.
        long buildEndTime = System.currentTimeMillis();
        metricsService.recordBuild(success,
                (int) (buildEndTime - startTime),
                (int) buildEndTime);
        return success;
    }

    /**
     * Runs the CI build for a given repository and branch.
     *
     * @param cloneUrl URL of the GitHub repository to clone
     * @param branch   branch name to checkout
     * @return true if the Maven build succeeds, false otherwise
     * @throws Exception if any system command (git/maven) fails or IO error occurs
     */
    public boolean runBuildInterval(String cloneUrl, String branch) throws Exception {
        // 1. Create a temporary directory for this build.
        // Each build runs in its own isolated workspace.
        Path tempDirectory = createWorkspace();
        System.out.println("[CI] Workspace: " + tempDirectory);

        // Create a unique build id and prepare a log file for it.
        // The log is stored at: builds/<buildId>/log.txt
        lastBuildId = Long.toString(System.currentTimeMillis());

        // use injected base directory
        Path buildLogDir = buildsBaseDir.resolve(lastBuildId);
        Files.createDirectories(buildLogDir);

        Path buildLogPath = buildLogDir.resolve("log.txt");

        // 2. Clone the repository into the temp directory.
        runCommand(
                new String[]{"git", "clone", cloneUrl},
                tempDirectory.toFile(),
                buildLogPath
        );

        // 3. After cloning, find the repository directory.
        File repoDir = resolveRepositoryDirectory(tempDirectory);

        // 4. Checkout the requested branch.
        runCommand(
                new String[]{"git", "checkout", branch},
                repoDir,
                buildLogPath
        );

        // 5. Run Maven tests.
        // Maven returns exit code 0 on success, non-zero on failure.
        String mvnCmd;
        if (Files.exists(repoDir.toPath().resolve(isWindows() ? "mvnw.cmd" : "mvnw"))) {
            mvnCmd = isWindows() ? "mvnw.cmd" : "./mvnw";
        } else {
            mvnCmd = isWindows() ? "mvn.cmd" : "mvn";
        }

        int exitCode = runCommand(
                new String[]{mvnCmd, "test"},
                repoDir,
                buildLogPath
        );

        // Return true only if the build was successful.
        return exitCode == 0;
    }

    /**
     * Returns the build id of the most recent build.
     * The corresponding log is available at: builds/{@code <buildId>}/log.txt
     *
     * @return last build id, or null if no build has been executed yet
     */
    public String getLastBuildId() {
        return lastBuildId;
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    /**
     * Runs a system command inside a given working directory.
     *
     * The output of the process is printed to the CI server console.
     *
     * @param cmd command and arguments (e.g. {"git", "clone", url})
     * @param dir working directory where the command is executed
     * @param logFile file where build output is appended (builds/<buildId>/log.txt)
     * @return the exit code of the process
     * @throws IOException if the process cannot be started
     * @throws InterruptedException if the process is interrupted
     */
    protected int runCommand(String[] cmd, File dir, Path logFile)
            throws IOException, InterruptedException {

        // Prepare the process builder with the given command.
        ProcessBuilder pb = new ProcessBuilder(cmd);

        // Set the working directory for the command.
        pb.directory(dir);

        // Merge stdout and stderr so we can read everything together.
        pb.redirectErrorStream(true);

        // Start the process.
        Process p = pb.start();

        // Read and print the process output line by line.
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(p.getInputStream()))) {

            String line;
            while ((line = r.readLine()) != null) {
                String out = "[BUILD] " + line;
                System.out.println(out);
                Files.writeString(logFile, out + System.lineSeparator(),
                        java.nio.charset.StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.APPEND);
            }
        }

        // Wait for the process to finish and return its exit code.
        int exit = p.waitFor();
        Files.writeString(logFile, "exitCode=" + exit + System.lineSeparator(),
                java.nio.charset.StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND);
        return exit;
    }

    // Backwards-compatible overload to keep existing behavior if needed elsewhere.
    protected int runCommand(String[] cmd, File dir)
            throws IOException, InterruptedException {

        Path fallbackLogDir = buildsBaseDir.resolve(
                lastBuildId == null ? "unknown" : lastBuildId
        );

        try {
            Files.createDirectories(fallbackLogDir);
        } catch (IOException ignored) { }

        Path fallbackLog = fallbackLogDir.resolve("log.txt");
        return runCommand(cmd, dir, fallbackLog);
    }

    protected Path createWorkspace() throws IOException {
        return Files.createTempDirectory("ci-build-");
    }

    protected File resolveRepositoryDirectory(Path workspace) {
        File[] dirs = workspace.toFile().listFiles(File::isDirectory);
        if (dirs == null || dirs.length == 0) {
            throw new RuntimeException("Repository directory not found after clone");
        }
        return dirs[0];
    }

}
