package org.whitesource.fs.configuration;
import org.junit.Assert;
import org.junit.Test;
import org.whitesource.agent.dependency.resolver.npm.TestHelper;
import org.whitesource.fs.CommandLineArgs;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

public class ConfigurationValidationTest {

    @Test
    public void shouldWorkWithProjectPerFolder() throws IOException {

        // arrange
        File file = TestHelper.getFileFromResources(CommandLineArgs.CONFIG_FILE_NAME);
        final String JAVA_TEMP_DIR = System.getProperty("java.io.tmpdir");

        Path tmpPath = Paths.get(JAVA_TEMP_DIR, file.getName());
        Files.copy(file.toPath(), tmpPath, StandardCopyOption.REPLACE_EXISTING);

        replaceSelected(tmpPath.toString(), "#projectPerFolder=true", "projectPerFolder=true");
        ConfigurationValidation configurationValidation = new ConfigurationValidation();

        // act
        Properties configProperties = configurationValidation.readAndValidateConfigFile(tmpPath.toString(), "");

        // assert
        Assert.assertNotNull(configProperties);
    }

    private void replaceSelected(String filename, String toFind, String replaceWith) {
        try {
            // input the file content to the StringBuffer "input"
            BufferedReader file = new BufferedReader(new FileReader(filename));
            String line;
            StringBuffer inputBuffer = new StringBuffer();

            while ((line = file.readLine()) != null) {
                inputBuffer.append(line);
                inputBuffer.append('\n');
            }
            String inputStr = inputBuffer.toString();

            file.close();

            System.out.println(inputStr); // check that it's inputted right

            inputStr = inputStr.replace(toFind, replaceWith);

            // check if the new input is right
            System.out.println("----------------------------------\n" + inputStr);

            // write the new String with the replaced line OVER the same file
            FileOutputStream fileOut = new FileOutputStream(filename);
            fileOut.write(inputStr.getBytes());
            fileOut.close();

        } catch (Exception e) {
            System.out.println("Problem reading file.");
        }
    }
}
