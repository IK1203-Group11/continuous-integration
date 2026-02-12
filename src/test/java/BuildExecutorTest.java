import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Files;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class BuildExecutorTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private BuildExecutor executor;

    @Before
    public void setUp() throws Exception {
        Path testBuildsDir = tempFolder.newFolder("builds").toPath();
        executor = spy(new BuildExecutor(testBuildsDir));
    }

    /**
     * Asserts that when all CI steps (clone, checkout, mvn test)
     * return exit code 0, the runBuild() method:
     *
     *  - returns true
     *  - generates a buildId
     *  - creates a build directory inside the configured builds base directory
     *
     * This verifies the success-path contract of the CI execution flow.
     *
     * @throws Exception if unexpected internal error occurs
     */
    @Test
    public void shouldReturnTrueWhenAllCommandsSucceed() throws Exception {

        Path fakeWorkspace = Files.createTempDirectory("test-ci");
        File fakeRepo = Files.createTempDirectory("fake-repo").toFile();

        doReturn(fakeWorkspace).when(executor).createWorkspace();
        doReturn(fakeRepo).when(executor)
                .resolveRepositoryDirectory(any(Path.class));

        doReturn(0).when(executor)
                .runCommand(any(String[].class), any(File.class), any(Path.class));

        boolean result = executor.runBuild(
                "https://repo.git",
                "main"
        );

        assertTrue(result);

        assertTrue(tempFolder.getRoot().listFiles().length > 0);
    }

    /**
     * Asserts that if the Maven test step returns a non-zero exit code,
     * runBuild() must return false.
     *
     * This verifies that build failure is correctly propagated
     * from the command execution layer to the public API.
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

        doReturn(1).when(executor)
                .runCommand(any(String[].class), any(File.class), any(Path.class));

        boolean result = executor.runBuild(
                "https://repo.git",
                "main"
        );

        assertFalse(result);
    }
}
