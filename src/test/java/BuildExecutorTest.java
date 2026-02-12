import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class BuildExecutorTest {

    private BuildExecutor executor;

    @Before
    public void setUp() {
        executor = spy(new BuildExecutor());
    }

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
        assertNotNull(executor.getLastBuildId());
    }

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
