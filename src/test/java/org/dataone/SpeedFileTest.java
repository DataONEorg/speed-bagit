package org.dataone;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static org.junit.Assert.*;
import static org.junit.Assert.fail;

public class SpeedFileTest {

    /**
     * Test that getPath works
     */
    @Test
    public void testGetPath()
    {
        String testCsvFile = "1234, 56789";
        String targetPath = "data/my_csv_file.csv";
        InputStream fileStream = new ByteArrayInputStream(testCsvFile.getBytes(StandardCharsets.UTF_8));
        try {
            SpeedStream speedStream = new SpeedStream(fileStream, MessageDigest.getInstance("MD5"));
            SpeedFile testFile = new SpeedFile(speedStream, targetPath, false);
            String path = testFile.getPath();
            assertEquals(path, targetPath);
        } catch (NoSuchAlgorithmException e) {
            fail();
        }
    }

    /**
     * Test that getStream is returning the stream that was passed into the
     * constructor
     */
    @Test
    public void testGetStream()
    {
        String testCsvFile = "1234, 56789";
        String targetPath = "data/my_csv_file.csv";
        InputStream fileStream = new ByteArrayInputStream(testCsvFile.getBytes(StandardCharsets.UTF_8));
        try {
            SpeedStream speedStream = new SpeedStream(fileStream, MessageDigest.getInstance("MD5"));
            SpeedFile testFile = new SpeedFile(speedStream, targetPath, false);
            SpeedStream stream = testFile.getStream();
            assertEquals(stream, speedStream);
        } catch (NoSuchAlgorithmException e) {
            fail();
        }
    }

    /**
     * Test isTagFile is returning the correct property
     */
    @Test
    public void testIsTagFile()
    {
        String testCsvFile = "1234, 56789";
        String targetPath = "data/my_csv_file.csv";
        boolean tagFile = false;
        InputStream fileStream = new ByteArrayInputStream(testCsvFile.getBytes(StandardCharsets.UTF_8));
        try {
            SpeedStream speedStream = new SpeedStream(fileStream, MessageDigest.getInstance("MD5"));
            SpeedFile testFile = new SpeedFile(speedStream, targetPath, tagFile);
            boolean isTag = testFile.isTagFile();
            assertEquals(isTag, tagFile);
        } catch (NoSuchAlgorithmException e) {
            fail();
        }
    }
}
