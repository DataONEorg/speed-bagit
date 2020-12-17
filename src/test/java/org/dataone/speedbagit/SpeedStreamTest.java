package org.dataone.speedbagit;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


public class SpeedStreamTest {

    /**
     * Test that the default arguments are correctly set.
     *
     */
    @Test
    public void testCtor() throws NoSuchAlgorithmException, IOException {
        String testData = "12345, 345rfdew, 45tgfdr";

        InputStream testDataStream = new ByteArrayInputStream(testData.getBytes());
        MessageDigest targetDigest = MessageDigest.getInstance("MD5");
        SpeedStream speedStream = new SpeedStream(testDataStream, targetDigest);

        // Make sure that the size always starts at 0
        assertEquals(speedStream.getSize(), 0);
    }

    /**
     * Tests that the method performing single byte reads is properly working.
     * The ipnut data should match the data that's read; the checksum and size
     * should also be correct.
     */
    @Test
    public void testSingleByteRead() throws NoSuchAlgorithmException, IOException {
        String testData = "12345, 345rfdew, 45tgfdr";
        String expectedSHA1 = "2fe482afad4e73addf3cb3823ff9b83144763bf2";

        InputStream testDataStream = new ByteArrayInputStream(testData.getBytes());
        MessageDigest targetDigest = MessageDigest.getInstance("SHA-1");
        SpeedStream speedStream = new SpeedStream(testDataStream, targetDigest);

        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = speedStream.read()) != -1) {
            result.write(buffer, 0, length);
        }

        assertEquals(expectedSHA1, speedStream.getChecksum());
        // Check that the SpeedStream size is correct
        assertEquals(testData.length(), speedStream.getSize());
    }

    /**
     * Tests that the method for reading chunks of the stream is properly working. The
     * size, checksum, and end data should all be correct.
     *
     */
    @Test
    public void testChecksummingBufferedRead() throws NoSuchAlgorithmException, IOException {
        String testData = "12345, 345rfdew, 45tgfdr";
        String expectedSHA1 = "2fe482afad4e73addf3cb3823ff9b83144763bf2";

        InputStream testDataStream = new ByteArrayInputStream(testData.getBytes());
        MessageDigest targetDigest = MessageDigest.getInstance("SHA-1");
        SpeedStream speedStream = new SpeedStream(testDataStream, targetDigest);

        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = speedStream.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }

        assertEquals(expectedSHA1, speedStream.getChecksum());
        // Check that the SpeedStream size is correct
        assertEquals(testData.length(), speedStream.getSize());
        // Check that the SpeedStream size matches the size of the result
        assertEquals(result.toString().length(), testData.length());
    }
}
