<<<<<<< HEAD
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.UUID;
=======
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
>>>>>>> main

/**
 * This class is responsible for executing the core CI build steps.
 *  - clone a repository
 *  - checkout the given branch
 *  - run Maven tests
 *
 * The result of the build is returned as a boolean value.
 */
public class BuildExecutor {

<<<<<<< HEAD
    // P7: persistent build history directory (local filesystem)
    private static final Path BUILD_HISTORY_DIR = Paths.get("build-history");
=======
    // Stores the build id of the most recent build (used for linking logs in the CI server).
    private volatile String lastBuildId = null;
>>>>>>> main

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

<<<<<<< HEAD
        // writer for build log
        BufferedWriter logWriter = Files.newBufferedWriter(logFile);
=======
        // Create a unique build id and prepare a log file for it.
        // The log is stored at: builds/<buildId>/log.txt
        lastBuildId = Long.toString(System.currentTimeMillis());
        Path buildLogDir = Path.of("builds", lastBuildId);
        Files.createDirectories(buildLogDir);
        Path buildLogPath = buildLogDir.resolve("log.txt");

        // 2. Clone the repository into the temp directory.
        runCommand(
                new String[]{"git", "clone", cloneUrl},
                tempDirectory.toFile(),
                buildLogPath
        );
>>>>>>> main

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
<<<<<<< HEAD
=======
        File repoDir = dirs[0];

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
>>>>>>> main
    }

    /**
     * Returns the build id of the most recent build.
     * The corresponding log is available at: builds/<buildId>/log.txt
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
<<<<<<< HEAD
     * @param logWriter log writer for build history
=======
     * @param logFile file where build output is appended (builds/<buildId>/log.txt)
>>>>>>> main
     * @return the exit code of the process
     * @throws IOException if the process cannot be started
     * @throws InterruptedException if the process is interrupted
     */
<<<<<<< HEAD


    private int runCommand(String[] cmd, File dir, BufferedWriter logWriter)
=======
    private int runCommand(String[] cmd, File dir, Path logFile)
>>>>>>> main
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
<<<<<<< HEAD
                System.out.println("[BUILD] " + line);
                logWriter.write(line);
                logWriter.newLine();
=======
                String out = "[BUILD] " + line;
                System.out.println(out);
                Files.writeString(logFile, out + System.lineSeparator(),
                        java.nio.charset.StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.APPEND);
>>>>>>> main
            }
        }
        logWriter.flush();

        // Wait for the process to finish and return its exit code.
        int exit = p.waitFor();
        Files.writeString(logFile, "exitCode=" + exit + System.lineSeparator(),
                java.nio.charset.StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND);
        return exit;
    }

    // Backwards-compatible overload to keep existing behavior if needed elsewhere.
    private int runCommand(String[] cmd, File dir)
            throws IOException, InterruptedException {
        Path fallbackLogDir = Path.of("builds", (lastBuildId == null ? "unknown" : lastBuildId));
        try {
            Files.createDirectories(fallbackLogDir);
        } catch (IOException ignored) { }
        Path fallbackLog = fallbackLogDir.resolve("log.txt");
        return runCommand(cmd, dir, fallbackLog);
    }
}
