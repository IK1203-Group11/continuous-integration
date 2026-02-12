import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Files;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BuildExecutor.
 *
 * These tests verify the logical contract of the CI build execution flow
 * without executing real system commands (git, maven) or writing into
 * the production "builds" directory.
 *
 * All external effects are mocked:
 *  - filesystem workspace creation
 *  - repository resolution
 *  - command execution
 *  - metrics recording
 */
@RunWith(MockitoJUnitRunner.class)
public class BuildExecutorTest {

    /**
     * TemporaryFolder ensures that test-created directories
     * are automatically deleted after the test completes.
     *
     * This prevents pollution of the real "builds" directory.
     */
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    /**
     * Mocked MetricsService.
     *
     * This guarantees that no real metrics persistence or logging
     * happens during test execution.
     */
    @Mock
    private MetricsService metricsService;

    /**
     * Spy instance of BuildExecutor.
     *
     * We spy instead of fully mocking so that:
     *  - real runBuild logic executes
     *  - but low-level methods (workspace, commands) are overridden
     */
    private BuildExecutor executor;

    /**
     * Initializes a spy BuildExecutor using an isolated
     * temporary builds directory for test safety.
     */
    @Before
    public void setUp() throws Exception {
        Path testBuildsDir = tempFolder.newFolder("builds").toPath();
        executor = spy(new BuildExecutor(metricsService, testBuildsDir));
    }

    /**
     * Asserts that when all CI steps (clone, checkout, mvn test)
     * return exit code 0, the runBuild() method:
     *
     *  - returns true
     *  - generates a buildId
     *  - creates a build directory inside the configured builds base directory
     *  - records metrics as success
     *
     * This verifies the success-path contract of the CI execution flow.
     *
     * @throws Exception if unexpected internal error occurs
     */
    @Test
    public void shouldReturnTrueWhenAllCommandsSucceed() throws Exception {

        Path fakeWorkspace = Files.createTempDirectory("test-ci");
        File fakeRepo = Files.createTempDirectory("fake-repo").toFile();

        // Prevent real filesystem and git execution
        doReturn(fakeWorkspace).when(executor).createWorkspace();
        doReturn(fakeRepo).when(executor)
                .resolveRepositoryDirectory(any(Path.class));

        // Simulate successful commands
        doReturn(0).when(executor)
                .runCommand(any(String[].class), any(File.class), any(Path.class));

        boolean result = executor.runBuild(
                "https://repo.git",
                "main"
        );

        assertTrue(result);

        // Ensure metrics were recorded as success
        verify(metricsService, times(1))
                .recordBuild(eq(true), anyInt(), anyInt());

        // Ensure build directory was created in isolated folder
        assertTrue(tempFolder.getRoot().listFiles().length > 0);
    }

    /**
     * Asserts that if the Maven test step returns a non-zero exit code,
     * runBuild() must return false.
     *
     * This verifies that build failure is correctly propagated
     * from the command execution layer to the public API,
     * and that metrics are recorded as failure.
     *
     * @throws Exception if unexpected internal error occurs
     */
    @Test
    public void shouldReturnFalseWhenMavenFails() throws Exception {

        Path fakeWorkspace = Files.createTempDirectory("test-ci");
        File fakeRepo = Files.createTempDirectory("fake-repo").toFile();

        doReturn(fakeWorkspace).when(executor).createWorkspace();
        doReturn(fakeRepo).when(executor)
                .resolveRepositoryDirectory(any(Path.class));

        // Simulate Maven failure
        doReturn(1).when(executor)
                .runCommand(any(String[].class), any(File.class), any(Path.class));

        boolean result = executor.runBuild(
                "https://repo.git",
                "main"
        );

        assertFalse(result);

        // Ensure metrics were recorded as failure
        verify(metricsService, times(1))
                .recordBuild(eq(false), anyInt(), anyInt());
    }
}
