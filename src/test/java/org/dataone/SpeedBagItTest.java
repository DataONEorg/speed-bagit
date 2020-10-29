package org.dataone;
import org.junit.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;

/**
 * Unit test for simple App.
 */
public class SpeedBagItTest
{
    /**
     *
     */
    @Test
    public void testCtor()
    {
        double bagVersion = 1.0;
        String checksumAlgorithm = "MD5";
        Map<String, String> bagMetadata = new HashMap<>();

        String bagDescription = "A test bag.";
        String externalDescription = "A bag used for testing.";
        String contactEmail = "aFakeEmail";
        String externalIdentifier = "doi:xx.xxx.xx";

        bagMetadata.put("description", bagDescription);
        bagMetadata.put("External-Description", externalDescription);
        bagMetadata.put("Contact-Email", contactEmail);
        bagMetadata.put("External-Identifier", externalIdentifier);

        SpeedBagIt bag = null;
        try {
            bag = new SpeedBagIt(bagVersion, checksumAlgorithm, bagMetadata);
            assertEquals(bag.version, bagVersion, 0.0);
            assertEquals(bag.checksumAlgorithm, checksumAlgorithm);
            // Confirm the bagit.txt file has been created
            assertTrue(bag.bagitFile.length() > 0);
        } catch (SpeedBagException | NoSuchAlgorithmException e) {
            fail();
        }
        String bagitFile = bag.bagitFile;
        System.out.println(bagitFile);
        // Get an array of bagit file properties (they're separated by \n)
        String[] bagitProperties = bag.bagitFile.split(System.getProperty("line.separator"));
        bag.bagitFile.lines().forEach(System.out::println);
        for (String line: bagitProperties) {
            try {
                String[] keyPair = line.split(": ");
                String key = bagMetadata.get(keyPair[0]);
                String value = bagMetadata.get(keyPair[1]);
                //if (key.equals("description")) {
                //    assertSame(value, bagDescription);
                //}
            } catch (Exception e) {
                fail();
            }
        }
    }

    @Test
    public void testBagExports() {
        double bagVersion = 1.0;
        String checksumAlgorithm = "MD5";
        Map<String, String> bagMetadata = new HashMap<>();

        SpeedBagIt bag = null;

        try {
            bag = new SpeedBagIt(bagVersion, checksumAlgorithm, bagMetadata);
            String dataFile = "1234, 9876, 3845";
            InputStream fileStream = new ByteArrayInputStream(dataFile.getBytes(StandardCharsets.UTF_8));
            bag.addFile(fileStream, "data/data_file.csv", MessageDigest.getInstance("MD5"), false);
            // Add it as a tag file too
            bag.addFile(fileStream, "metadata/metadata.xml", MessageDigest.getInstance("MD5"), true);
            FileOutputStream fos = new FileOutputStream("hello-world.zip");
            ZipOutputStream out =  new ZipOutputStream(new BufferedOutputStream(fos));
            bag.stream(out);

        } catch (SpeedBagException | IOException | NoSuchAlgorithmException e) {
            fail();
        }
    }

    @Test
    public void testChecksum() {

        String data = "12345, 345rfdew, 45tgfdr";
        String expectedSHA1 = "2fe482afad4e73addf3cb3823ff9b83144763bf2";
        String expectedSHA256 = "6403b6be0d099dad457b00626d5c2d4ad9760068e9211a419a0603dcab962154";
        String expectedSHA512 = "c9103f375fcd172e7d80967bfe961c20ab8c77d09860fa2581b47ae13e1fbb91" +
                "ed9d701fd2a3a57da66722ba3996f4bc382273d5896fcd9fe96d8fcf236a2f45";

        SpeedBagIt bag = null;
        double bagVersion = 1.0;
        Map<String, String> bagMetadata = new HashMap<>();

        try {
            String checksumAlgorithm = "SHA-1";
            bag = new SpeedBagIt(bagVersion, checksumAlgorithm, bagMetadata);
            // assertEquals(checksum, expectedSHA1);

            checksumAlgorithm = "SHA-256";
            bag = new SpeedBagIt(bagVersion, checksumAlgorithm, bagMetadata);
            // assertEquals(checksum, expectedSHA256);

            checksumAlgorithm = "SHA-512";
            bag = new SpeedBagIt(bagVersion, checksumAlgorithm, bagMetadata);
            // assertEquals(checksum, expectedSHA512);
        } catch (SpeedBagException | NoSuchAlgorithmException e) {
            fail();
        }
    }

    /**
     * Test that NoSuchAlgorithmException is thrown when a user tries to use an unsupported
     * checksum algorithm.
     */
    @Test
    public void testInvalidChecksum() {

        SpeedBagIt bag = null;
        double bagVersion = 1.0;
        Map<String, String> bagMetadata = new HashMap<>();

        try {
            String checksumAlgorithm = "SHA-1234";
            new SpeedBagIt(bagVersion, checksumAlgorithm, bagMetadata);

        } catch (NoSuchAlgorithmException e) {
            // Should get here
        } catch (SpeedBagException e) {
            // Shouldn't get here
            fail();
        }
    }
}
