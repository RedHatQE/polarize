package polarize.bus.tests;

import polarize.bus.tests.config.ConfigTest;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;


public class Helper {
    public static void installDefaultConfig(String configPath) {
        InputStream cfg = ConfigTest.class.getClassLoader().getResourceAsStream("configs/default.yaml");
        String content = "";
        try (BufferedReader buffer = new BufferedReader(new InputStreamReader(cfg))) {
            content = buffer.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        File cfgFile = new File(configPath);
        if (cfgFile.exists())
            cfgFile.delete();
        Path path = cfgFile.toPath();
        try (BufferedWriter wrt = Files.newBufferedWriter(path)){
            wrt.write(content);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getDefaultConfigPath() {
        return Paths.get(System.getProperty("user.home"), ".polarize", "busconfig.yaml").toString();
    }
}
