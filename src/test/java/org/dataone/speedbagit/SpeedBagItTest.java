/**
 * This work was created by participants in the DataONE project, and is
 * jointly copyrighted by participating institutions in DataONE. For
 * more information on DataONE, see our web site at http://dataone.org.
 *
 *   Copyright ${year}
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * $Id$
 */

package org.dataone.speedbagit;

import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Enumeration;
import java.util.zip.ZipFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;

import org.junit.jupiter.api.io.TempDir;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit tests for the SpeedBagIt class. Because this class is the main interface for
 * creating bags, most integrated unit tests are in this file.
 */
public class SpeedBagItTest {

    @TempDir
    Path directory = Files.createTempDirectory("speedbag_tests");

    public SpeedBagItTest() throws IOException {
    }

    private void validateBagitFile(String contents, double bagVersion) throws IOException {
    	BufferedReader bufReader = new BufferedReader(new StringReader(contents));
    	String line=null;
    	while( (line=bufReader.readLine()) != null )
    	{
            String[] keyPair = line.split(": ");
            if (keyPair[0].equals("BagIt-Version")) {
                assertEquals(keyPair[1], String.valueOf(bagVersion));
            } else if (keyPair[0].equals("Tag-File-Character-Encoding")) {
                assertEquals(keyPair[1], "UTF-8");
            }
    	}
    }

    /**
     * Validates the contents of the bag-info.txt file
     *
     * @param contents:   The contents of the file
     * @param zipFile: The version of the bag
     * @param dataFileCount: The number of files in the data/ directory
     * @throws IOException 
     */
    private void validateBagInfoFile(String contents, ZipFile zipFile, int dataFileCount) throws IOException {
    	BufferedReader bufReader = new BufferedReader(new StringReader(contents));
    	String line=null;
    	while( (line=bufReader.readLine()) != null )
    	{
            String[] keyPair = line.split(": ");
            switch (keyPair[0]) {
                case "Payload-Oxum":
                    // Split the oxum into its two parts
                    String[] payloadOxum = keyPair[1].split("\\.");
                    // Check that the second digit is the number of data files streamed
                    assertEquals(payloadOxum[1], String.valueOf(dataFileCount));
                    break;
                case "Bagging-Date":
                    // Check that it was recently bagged
                    LocalDateTime dateTime = LocalDateTime.now();
                    DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH);
                    assertEquals(keyPair[1], dateFormat.format(dateTime));
                    break;
            }
    	}
    }

    static String convertStreamToString(java.io.InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    /**
     * Test that the constructor is properly preserving input and
     * inserting the correct properties into the bag metadata file.
     */
    @Test
    public void testCtorMetadata() throws IOException {
        double bagVersion = 1.0;
        String checksumAlgorithm = "MD5";

        SpeedBagIt bag;
        bag = new SpeedBagIt(bagVersion, checksumAlgorithm);
        assertEquals(bag.version, bagVersion);
        assertEquals(bag.checksumAlgorithm, checksumAlgorithm);
        assertEquals(bag.bagitMetadata.size(), 0);
    }

    /**
     * Test that the constructor is properly preserving input and
     * inserting the correct properties into the bag metadata file.
     * Helper method that creates a stock Bag
     * @return The SpeedBag object
     */
    public SpeedBagIt getStockBag() throws SpeedBagException, NoSuchAlgorithmException, IOException {
        double bagVersion = 1.0;
        String checksumAlgorithm = "MD5";
        Map<String, String> bagMetadata = new HashMap<>();

        SpeedBagIt bag = null;

        bag = new SpeedBagIt(bagVersion, checksumAlgorithm, bagMetadata);

        // Create & add data files
        String dataFile1 = "1234, 9876, 3845";
        String dataFile2 = "trees, cars, bridges";
        InputStream dataFile1Stream = new ByteArrayInputStream(dataFile1.getBytes(StandardCharsets.UTF_8));
        InputStream dataFile2Stream = new ByteArrayInputStream(dataFile2.getBytes(StandardCharsets.UTF_8));
        bag.addFile(dataFile1Stream, "data/data_file1.csv", false);
        bag.addFile(dataFile2Stream, "data/data_file2.csv", false);

        // Create and add the tag files
        String fetchFile = "someURI, somePath";
        String metadataFile = "extra metadata";
        InputStream fetchFile1Stream = new ByteArrayInputStream(fetchFile.getBytes(StandardCharsets.UTF_8));
        InputStream metadataFile2Stream = new ByteArrayInputStream(metadataFile.getBytes(StandardCharsets.UTF_8));
        bag.addFile(fetchFile1Stream, "./fetch.txt", true);
        bag.addFile(metadataFile2Stream, "metadata/metadata.csv", true);

        return bag;
    }

    /**
     * Test that the constructor is setting the right parameters.
     */
    @Test
    public void testCtor() throws IOException {
        double bagVersion = 1.0;
        String checksumAlgorithm = "MD5";
        // Custom bag metadata
        Map<String, String> bagMetadata = new HashMap<>();
        bagMetadata.put("External-Description", "A bag used for testing.");
        bagMetadata.put("External-Identifier", "1234");

        SpeedBagIt bag;
        bag = new SpeedBagIt(bagVersion, checksumAlgorithm, bagMetadata);
        assertEquals(bag.version, bagVersion);
        assertEquals(bag.checksumAlgorithm, checksumAlgorithm);
        assertEquals(bag.bagitMetadata.size(), bagMetadata.size());
    }

    /**
     * Test that the default bagit.txt file is correct
     */
    @Test
    public void testGenerateBagIt() throws IOException {
        // Don't specify any additional metadata for bagit.txt
        double bagVersion = 1.0;
        SpeedBagIt bag = new SpeedBagIt(bagVersion, "MD5");

        // Generate the text for the bagit.txt file
        String bagitTxtFile = bag.generateBagitTxt();

        // The bare minimum content  to check against
        Map<String, String> minimumMetadata = new HashMap<>();
        minimumMetadata.put("version", String.valueOf(bagVersion));
        minimumMetadata.put("Tag-File-Character-Encoding", "UTF-8");

    	BufferedReader bufReader = new BufferedReader(new StringReader(bagitTxtFile));
    	String line=null;
    	while( (line=bufReader.readLine()) != null )
    	{
            String[] keyPair = line.split(": ");
            String key = minimumMetadata.get(keyPair[0]);
            String value = minimumMetadata.get(keyPair[1]);
            assertEquals(minimumMetadata.get(key), value);
    		
    	}
    }

    /**
     * Test that the bagit.txt file has additional parameters that were specified
     * by the user.
     */
    @Test
    public void testGenerateBagitTxtCustom() throws IOException {

        String bagDescription = "A test bag.";
        String externalDescription = "A bag used for testing.";
        String contactEmail = "aFakeEmail";
        String externalIdentifier = "doi:xx.xxx.xx";

        Map<String, String> bagMetadata = new HashMap<>();
        bagMetadata.put("description", bagDescription);
        bagMetadata.put("External-Description", externalDescription);
        bagMetadata.put("Contact-Email", contactEmail);
        bagMetadata.put("External-Identifier", externalIdentifier);

        SpeedBagIt bag = new SpeedBagIt(1.0, "MD5", bagMetadata);

        // Generate the text for the bagit.txt file
        String bagitTxtFile = bag.generateBagitTxt();
    	BufferedReader bufReader = new BufferedReader(new StringReader(bagitTxtFile));
    	String line=null;
    	while( (line=bufReader.readLine()) != null )
        {
            String[] keyPair = line.split(": ");
            String key = bagMetadata.get(keyPair[0]);
            String value = bagMetadata.get(keyPair[1]);
            assertEquals(bagMetadata.get(key), value);
        }
    }

    /**
     * Iterates over a bag, looking for bagit files that can be validated.
     *
     * @param zipFile:    The bag being validated
     * @param bagVersion: The version of the bag
     * @param dataFileCount: The number of files in the data/ directory
     */
    private void validateBagItFiles(ZipFile zipFile, double bagVersion, int dataFileCount) throws IOException {
        // Process each of the bag metadata files
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            InputStream fileStream = zipFile.getInputStream(entry);
            String contents = convertStreamToString(fileStream);
            switch (entry.getName()) {
                case "bag-info.txt":
                    this.validateBagInfoFile(contents, zipFile, dataFileCount);
                    break;
                case "bagit.txt":
                    this.validateBagitFile(contents, bagVersion);
                    break;
            }
        }
    }

    /**
     * Tests that a bag can be created without a payload
     * Test that the bag properly exports
     */
    @Test
    public void testEmptyBag() throws IOException {
        double bagVersion = 1.0;
        String checksumAlgorithm = "MD5";
        Map<String, String> bagMetadata = new HashMap<>();

        SpeedBagIt bag = null;
        Path bagFilePath = null;
        try {
            // Create and write the bag
            bag = new SpeedBagIt(bagVersion, checksumAlgorithm, bagMetadata);
            Path bagPath = Paths.get(directory.toString() + "emptyBag.zip");
            bagFilePath = Files.createFile(bagPath);
            FileOutputStream fos = new FileOutputStream(bagFilePath.toString());
            InputStream bagStream = bag.stream();
            IOUtils.copy(bagStream, fos);
        } catch (IOException | NoSuchAlgorithmException e) {
            e.printStackTrace();
            if (bagFilePath != null) {
                Files.delete(bagFilePath);
            }
            fail();
        }

        // Open to bag to read
        ZipFile zipFile = new ZipFile(bagFilePath.toString());
        this.validateBagItFiles(zipFile, bagVersion, bag.getPayloadFileCount());
        Files.delete(bagFilePath);
    }

    /**
     * Tests a case where the bag only has files in the data directory.
     *
     * Test that streams are correctly added.
     */
    @Test
    public void testAddFile() throws SpeedBagException, NoSuchAlgorithmException, IOException {

        // Keep track of the locations where the files should go so that
        // the bag can be tested against them
        List<String> expectedDataPaths = new ArrayList<>();
        expectedDataPaths.add("data/data_file1.csv");
        expectedDataPaths.add("data/data_file2.csv");

        List<String> expecteMetadataPaths = new ArrayList<>();
        expecteMetadataPaths.add("./fetch.txt");
        expecteMetadataPaths.add("metadata/metadata.csv");


        SpeedBagIt bag = getStockBag();
        List<SpeedFile> dataFiles = bag.getDataFiles();
        assert dataFiles.size() == 2;
        for (SpeedFile dataFile: dataFiles) {
            assert expectedDataPaths.contains(dataFile.getPath());
        }

        List<SpeedFile> metadataFiles = bag.getTagFiles();
        assert metadataFiles.size() == 2;
        for (SpeedFile tagFile: metadataFiles) {
            assert expecteMetadataPaths.contains(tagFile.getPath());
        }
    }

    @Test
    public void testGetDataFiles() throws SpeedBagException, NoSuchAlgorithmException, IOException {
        SpeedBagIt bag = getStockBag();
        List<SpeedFile> dataFiles = bag.getDataFiles();
        assert dataFiles.size() == 2;
    }

    @Test
    public void testGetTagFiles() throws SpeedBagException, NoSuchAlgorithmException, IOException {
        SpeedBagIt bag = getStockBag();
        List<SpeedFile> metadataFiles = bag.getTagFiles();
        assert metadataFiles.size() == 2;
    }

    @Test
    public void testDataBagExport() {
        double bagVersion = 1.0;
        String checksumAlgorithm = "MD5";
        Map<String, String> bagMetadata = new HashMap<>();

        SpeedBagIt bag ;
        Path bagFilePath;
        try {
            bag = new SpeedBagIt(bagVersion, checksumAlgorithm, bagMetadata);

            String dataFile1 = "1234, 9876, 3845";
            String dataFile2 = "1234, 9876, 38";
            String dataFile3 = "1234, 9876";

            // Simulate getting a stream to those bytes and adding them to the bag
            InputStream dataFileStream1 = new ByteArrayInputStream(dataFile1.getBytes(StandardCharsets.UTF_8));
            InputStream dataFileStream2 = new ByteArrayInputStream(dataFile2.getBytes(StandardCharsets.UTF_8));
            InputStream dataFileStream3 = new ByteArrayInputStream(dataFile3.getBytes(StandardCharsets.UTF_8));
            bag.addFile(dataFileStream1, "data/data_file1.csv", MessageDigest.getInstance("MD5"), false);
            bag.addFile(dataFileStream2, "data/data_file2.csv", MessageDigest.getInstance("MD5"), false);
            bag.addFile(dataFileStream3, "data/data_file3.csv", MessageDigest.getInstance("MD5"), false);

            Path bagPath = Paths.get(directory.toString() + "dataBag.zip");
            bagFilePath = Files.createFile(bagPath);
            FileOutputStream fos = new FileOutputStream(bagFilePath.toString());
            InputStream bagStream = bag.stream();
            IOUtils.copy(bagStream, fos);

            // Open to bag to read
            ZipFile zipFile = new ZipFile(bagFilePath.toString());
            // Make sure that the bag files are correct
            this.validateBagItFiles(zipFile, bagVersion, bag.getPayloadFileCount());
            Files.delete(bagFilePath);

        } catch (IOException | NoSuchAlgorithmException e) {
            fail(e);
        }
    }


    @Test
    public void testDataBagExportDuplicateFilename() {
        double bagVersion = 1.0;
        String checksumAlgorithm = "MD5";
        Map<String, String> bagMetadata = new HashMap<>();

        SpeedBagIt bag ;
        Path bagFilePath;
        try {
            bag = new SpeedBagIt(bagVersion, checksumAlgorithm, bagMetadata);

            String dataFile1 = "1234, 9876, 3845";
            String dataFile2 = "1234, 9876, 38";

            // Simulate getting a stream to those bytes and adding them to the bag
            InputStream dataFileStream1 = new ByteArrayInputStream(dataFile1.getBytes(StandardCharsets.UTF_8));
            InputStream dataFileStream2 = new ByteArrayInputStream(dataFile2.getBytes(StandardCharsets.UTF_8));
            bag.addFile(dataFileStream1, "data/data_file1.csv", MessageDigest.getInstance("MD5"), false);
            bag.addFile(dataFileStream2, "data/data_file1.csv", MessageDigest.getInstance("MD5"), false);

            Path bagPath = Paths.get(directory.toString() + "dataBag.zip");
            bagFilePath = Files.createFile(bagPath);
            FileOutputStream fos = new FileOutputStream(bagFilePath.toString());
            InputStream bagStream = bag.stream();
            IOUtils.copy(bagStream, fos);

            // Open to bag to read
            ZipFile zipFile = new ZipFile(bagFilePath.toString());
            // Make sure that the bag files are correct
            this.validateBagItFiles(zipFile, bagVersion, bag.getPayloadFileCount());
            Files.delete(bagFilePath);

        } catch (IOException | NoSuchAlgorithmException e) {
            fail(e);
        }
        fail("java.util.zip.ZipException should have thrown");
    }


    @Test
    public void testMetadataBagExport() {
        double bagVersion = 1.0;
        String checksumAlgorithm = "MD5";
        Map<String, String> bagMetadata = new HashMap<>();

        SpeedBagIt bag;
        Path bagFilePath;
        try {
            bag = new SpeedBagIt(bagVersion, checksumAlgorithm, bagMetadata);

            String metadataFile1 = "col1: frog_counts";
            String metadataFile2 = "col2: fish_counts";
            String metadataFile3 = "col3: moss_counts";

            // Simulate getting a stream to those bytes and adding them to the bag
            InputStream dataFileStream1 = new ByteArrayInputStream(metadataFile1.getBytes(StandardCharsets.UTF_8));
            InputStream dataFileStream2 = new ByteArrayInputStream(metadataFile2.getBytes(StandardCharsets.UTF_8));
            InputStream dataFileStream3 = new ByteArrayInputStream(metadataFile3.getBytes(StandardCharsets.UTF_8));
            bag.addFile(dataFileStream1, "metadata/metadataFile1.csv", MessageDigest.getInstance("MD5"), true);
            bag.addFile(dataFileStream2, "metadata/metadataFile2.csv", MessageDigest.getInstance("MD5"), true);
            bag.addFile(dataFileStream3, "metadata/metadataFile3.csv", MessageDigest.getInstance("MD5"), true);

            Path bagPath = Paths.get(directory.toString() + "metadataBag.zip");
            bagFilePath = Files.createFile(bagPath);
            FileOutputStream fos = new FileOutputStream(bagFilePath.toString());
            InputStream bagStream = bag.stream();
            IOUtils.copy(bagStream, fos);
            // Open to bag to read
            ZipFile zipFile = new ZipFile(bagFilePath.toString());
            // Make sure that the bag files are correct
            this.validateBagItFiles(zipFile, bagVersion, bag.getPayloadFileCount());
            Files.delete(bagFilePath);

        } catch (IOException | NoSuchAlgorithmException e) {
            fail(e);
        }
    }
}
