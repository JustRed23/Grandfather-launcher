package dev.JustRed23.Grandfather.launcher;

import dev.JustRed23.Grandfather.versioning.ProgramVersion;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.TemporaryBuffer;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.util.GradleVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.CopyOption;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Launcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(Launcher.class);
    private static final String gradleVersion = "6.8.3";

    private static final File jar = new File(System.getProperty("user.dir") + "/Grandfather.jar");

    private static final int MAX_RESTARTS = 10;
    private static final long RESET_RESTARTS_AFTER = TimeUnit.MINUTES.toMillis(30);

    private static int restarts;
    private static long lastRestartMS;

    public static void main(String[] args) throws Exception {
        Properties properties = new Properties();
        properties.load(Launcher.class.getClassLoader().getResourceAsStream("application.properties"));
        ProgramVersion version = ProgramVersion.fromString((String) properties.getOrDefault("version", "1"));

        LOGGER.info("Running Launcher version {}", version);

        startBot();

        LOGGER.warn("Exiting");
    }

    private static void startBot() throws GitAPIException, IOException, InterruptedException {
        if (!jar.exists()) {
            LOGGER.warn("Jar file does not exist! Downloading");
            downloadLatestVersion();
        }

        boolean running = true;

        ExitCode exit;
        while (running) {
            ProcessBuilder builder = new ProcessBuilder("java", "-jar", jar.getAbsolutePath(), "-XX:+UseConcMarkSweepGC");
            builder.directory(jar.getParentFile());
            builder.redirectErrorStream(true);

            Process bot = builder.start();

            try (
                    InputStreamReader inputStreamReader = new InputStreamReader(bot.getInputStream());
                    BufferedReader reader = new BufferedReader(inputStreamReader)
            ) {
                String output;

                while ((output = reader.readLine()) != null)
                    System.out.println(output);

                bot.waitFor();
                exit = ExitCode.fromCode(bot.exitValue());

                switch (exit) {
                    case SHITTY_CONFIG:
                        LOGGER.error("You have not set the config file properly!");
                    case STOP:
                        running = false;
                        break;
                    case REBOOT:
                        break;
                    case DISCONNECTED:
                        restartHappened();
                        LOGGER.warn("The bot disconnected! Restarting");
                        break;
                    case UPDATE:
                        downloadLatestVersion();
                        break;
                    case GENERIC_ERROR:
                        LOGGER.warn("An unknown error occurred!");
                        running = false;
                        break;
                    default:
                        restartHappened();
                        LOGGER.warn("An unknown error with exit value {} occurred. Trying to restart", bot.exitValue());
                        break;
                }
            }
            bot.destroy();
            System.gc();

            if (limitHit()) {
                LOGGER.warn("Maximum restart limit hit!");
                running = false;
            }
        }
    }

    private static void restartHappened() {
        if ((lastRestartMS + RESET_RESTARTS_AFTER) < System.currentTimeMillis())
            restarts = 0;

        restarts++;
        lastRestartMS = System.currentTimeMillis();
    }

    private static boolean limitHit() {
        return restarts >= MAX_RESTARTS;
    }

    private static void downloadLatestVersion() throws IOException, GitAPIException {
        LOGGER.info("Creating temp file");

        File localPath = File.createTempFile("Grandfather-Repo", "");

        if(!localPath.delete())
            LOGGER.error("Could not delete temp file {}", localPath);

        LOGGER.info("Cloning repo");

        Git result = Git.cloneRepository()
                    .setDirectory(localPath)
                    .setURI("https://github.com/JustRed23/Grandfather-rev")
                    .call();

        LOGGER.info("Downloading Gradle version {}", gradleVersion);

        GradleConnector connector = GradleConnector.newConnector();
        connector.useGradleVersion(GradleVersion.version(gradleVersion).getVersion());
        connector.forProjectDirectory(localPath);

        LOGGER.info("Building");

        ProjectConnection project = connector.connect();
        BuildLauncher build = project.newBuild();
        build.forTasks("shadowJar");

        build.run();
        project.close();
        connector.disconnect();

        result.close();

        LOGGER.info("Renaming jar");

        File jarDirs = new File(localPath, "/build/libs/");
        File actualJar = Arrays.stream(jarDirs.listFiles()).filter(file -> file.getName().endsWith(".jar")).findFirst().orElse(null);

        if (actualJar != null)
            FileUtils.rename(actualJar, jar, StandardCopyOption.REPLACE_EXISTING);
        else throw new FileNotFoundException("Jar file was not found");

        FileUtils.delete(localPath, FileUtils.RECURSIVE);
    }
}
