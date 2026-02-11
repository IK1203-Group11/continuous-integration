import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.UUID;

/**
 * This class is responsible for executing the core CI build steps.
 *  - clone a repository
 *  - checkout the given branch
 *  - run Maven tests
 *
 * The result of the build is returned as a boolean value.
 */
public class BuildExecutor {

    // P7: persistent build history directory (local filesystem)
    private static final Path BUILD_HISTORY_DIR = Paths.get("build-history");

    /**
     * Runs the CI build for a given repository and branch.
     *
     * @param cloneUrl URL of the GitHub repository to clone
     * @param branch   branch name to checkout
     * @return true if the Maven build succeeds, false otherwise
     * @throws Exception
     */
    public boolean runBuild(String cloneUrl, String branch) throws Exception {

        // ensure build-history directory exists
        Files.createDirectories(BUILD_HISTORY_DIR);

        // unique build id
        String buildId = UUID.randomUUID().toString();
        Path buildDir = BUILD_HISTORY_DIR.resolve(buildId);
        Files.createDirectories(buildDir);

        Path logFile = buildDir.resolve("build.log");
        Path metaFile = buildDir.resolve("meta.txt");

        Instant startTime = Instant.now();

        // 1. Create a temporary directory for this build.
        // Each build runs in its own isolated workspace.
        Path tempDirectory = Files.createTempDirectory("ci-build-");
        System.out.println("[CI] Workspace: " + tempDirectory);

        // writer for build log
        BufferedWriter logWriter = Files.newBufferedWriter(logFile);

        boolean success = false;

        try {
            // 2. Clone the repository into the temp directory.
            runCommand(
                    new String[]{"git", "clone", cloneUrl},
                    tempDirectory.toFile(),
                    logWriter
            );

            // 3. After cloning, find the repository directory.
            File[] dirs = tempDirectory.toFile().listFiles(File::isDirectory);
            if (dirs == null || dirs.length == 0) {
                throw new RuntimeException("Repository directory not found after clone");
            }
            File repoDir = dirs[0];

            // 4. Checkout the requested branch.
            runCommand(
                    new String[]{"git", "checkout", branch},
                    repoDir,
                    logWriter
            );

            // 5. Run Maven tests.
            // Maven returns exit code 0 on success, non-zero on failure.
            int exitCode = runCommand(
                    new String[]{"mvn", "test"},
                    repoDir,
                    logWriter
            );

            success = (exitCode == 0);
            return success;

        } finally {
            // write build metadata (P7)
            BufferedWriter metaWriter = Files.newBufferedWriter(metaFile);
            metaWriter.write("buildId=" + buildId + "\n");
            metaWriter.write("repo=" + cloneUrl + "\n");
            metaWriter.write("branch=" + branch + "\n");
            metaWriter.write("startedAt=" + startTime + "\n");
            metaWriter.write("success=" + success + "\n");
            metaWriter.close();

            logWriter.close();
        }
    }

    /**
     * Runs a system command inside a given working directory.
     *
     * The output of the process is printed to the CI server console.
     *
     * @param cmd command and arguments (e.g. {"git", "clone", url})
     * @param dir working directory where the command is executed
     * @param logWriter log writer for build history
     * @return the exit code of the process
     * @throws IOException if the process cannot be started
     * @throws InterruptedException if the process is interrupted
     */


    private int runCommand(String[] cmd, File dir, BufferedWriter logWriter)
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
                System.out.println("[BUILD] " + line);
                logWriter.write(line);
                logWriter.newLine();
            }
        }
        logWriter.flush();

        // Wait for the process to finish and return its exit code.
        return p.waitFor();
    }
}
