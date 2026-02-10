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

    /**
     * Runs the CI build for a given repository and branch.
     *
     * @param cloneUrl URL of the GitHub repository to clone
     * @param branch   branch name to checkout
     * @return true if the Maven build succeeds, false otherwise
     * @throws Exception
     */
    public boolean runBuild(String cloneUrl, String branch) throws Exception {
        // 1. Create a temporary directory for this build.
        // Each build runs in its own isolated workspace.
        Path tempDirectory = Files.createTempDirectory("ci-build-");
        System.out.println("[CI] Workspace: " + tempDirectory);

        // 2. Clone the repository into the temp directory.
        runCommand(
                new String[]{"git", "clone", cloneUrl},
                tempDirectory.toFile()
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
                repoDir
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
                repoDir
        );

        // Return true only if the build was successful.
        return exitCode == 0;
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
     * @return the exit code of the process
     * @throws IOException if the process cannot be started
     * @throws InterruptedException if the process is interrupted
     */
    private int runCommand(String[] cmd, File dir)
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
            }
        }

        // Wait for the process to finish and return its exit code.
        return p.waitFor();
    }
}
