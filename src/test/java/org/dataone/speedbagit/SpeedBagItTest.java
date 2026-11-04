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
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Enumeration;
import java.util.zip.ZipFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

import org.junit.jupiter.api.io.TempDir;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;


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
     * Returns the bagit.txt metadata to construct a bag with and the metadata the bag is expected to have.
     *
     * @return The constructor metadata and expected metadata of each case
     */
    static Stream<Arguments> constructorParameters() {
        Map<String, String> customMetadata = new HashMap<>();
        customMetadata.put("External-Description", "A bag used for testing.");
        customMetadata.put("External-Identifier", "1234");
        return Stream.of(
                Arguments.of(null, new HashMap<String, String>()),
                Arguments.of(new HashMap<String, String>(), new HashMap<String, String>()),
                Arguments.of(customMetadata, new HashMap<>(customMetadata)));
    }

    /**
     * Tests that the constructors set the version, checksum algorithm and bagit.txt metadata.
     */
    @ParameterizedTest
    @MethodSource("constructorParameters")
    public void testCtor(Map<String, String> bagMetadata, Map<String, String> expectedMetadata)
            throws IOException {
        double bagVersion = 1.0;
        String checksumAlgorithm = "MD5";

        SpeedBagIt bag;
        if (bagMetadata == null) {
            bag = new SpeedBagIt(bagVersion, checksumAlgorithm);
        } else {
            bag = new SpeedBagIt(bagVersion, checksumAlgorithm, bagMetadata);
        }

        assertEquals(bagVersion, bag.version);
        assertEquals(checksumAlgorithm, bag.checksumAlgorithm);
        assertEquals(expectedMetadata, bag.bagitMetadata);
    }

    /**
     * Test that the constructor is properly preserving input and
     * inserting the correct properties into the bag metadata file.
     * Helper method that creates a stock Bag
     * @return The SpeedBag object
     */
    public SpeedBagIt getStockBag() throws NoSuchAlgorithmException, IOException, SpeedBagException {
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
        bag.addFile(fetchFile1Stream, "fetch.txt", true);
        bag.addFile(metadataFile2Stream, "metadata/metadata.csv", true);

        return bag;
    }

    /**
     * Test that the default bagit.txt file contains only the version and encoding.
     * The RFC has this constraint on 0.97 and 1.0, so test them both here.
     */
    @ParameterizedTest
    @ValueSource(doubles = {0.97, 1.0})
    public void testGenerateBagIt(double bagVersion) throws IOException {
        SpeedBagIt bag = new SpeedBagIt(bagVersion, "MD5");

        String bagitTxtFile = bag.generateBagitTxt();

        assertTrue(bagitTxtFile.endsWith("\n"), bagitTxtFile);
        String[] lines = bagitTxtFile.split("\n");
        assertEquals(2, lines.length, bagitTxtFile);
        assertEquals("BagIt-Version: " + bagVersion, lines[0]);
        assertEquals("Tag-File-Character-Encoding: UTF-8", lines[1]);
    }

    /**
     * Test that the bagit.txt file contains each user-specified fields. The RFC has this constraint on 0.97 and 1.0, so test them both here.
     */
    @ParameterizedTest
    @ValueSource(doubles = {0.97, 1.0})
    public void testGenerateBagitTxtCustom(double bagVersion) throws IOException {

        String bagDescription = "A test bag.";
        String externalDescription = "A bag used for testing.";
        String contactEmail = "aFakeEmail";
        String externalIdentifier = "doi:xx.xxx.xx";

        Map<String, String> bagMetadata = new HashMap<>();
        bagMetadata.put("description", bagDescription);
        bagMetadata.put("External-Description", externalDescription);
        bagMetadata.put("Contact-Email", contactEmail);
        bagMetadata.put("External-Identifier", externalIdentifier);

        SpeedBagIt bag = new SpeedBagIt(bagVersion, "MD5", bagMetadata);

        String bagitTxtFile = bag.generateBagitTxt();

        assertTrue(bagitTxtFile.endsWith("\n"), bagitTxtFile);
        String[] lines = bagitTxtFile.split("\n");
        int customFieldCount = bagMetadata.size();
        assertEquals(customFieldCount + 2, lines.length, bagitTxtFile);
        assertEquals("BagIt-Version: " + bagVersion, lines[customFieldCount]);
        assertEquals("Tag-File-Character-Encoding: UTF-8", lines[customFieldCount + 1]);

        // The custom fields are written in HashMap order, so they are compared as a map
        Map<String, String> writtenMetadata = new HashMap<>();
        for (String line : Arrays.copyOfRange(lines, 0, customFieldCount)) {
            String[] keyValue = line.split(": ", 2);
            assertEquals(2, keyValue.length, line);
            writtenMetadata.put(keyValue[0], keyValue[1]);
        }
        assertEquals(bagMetadata, writtenMetadata);
    }

    /**
     * Iterates over a bag, looking for bagit files that can be validated.
     *
     * @param zipFile:    The bag being validated
     * @param bagVersion: The version of the bag
     * @param dataFileCount: The number of files in the data/ directory
     * @param manifestAlgorithmName: The checksum algorithm name in the manifest file names, e.g. sha256
     */
    private void validateBagItFiles(ZipFile zipFile, double bagVersion, int dataFileCount,
                                    String manifestAlgorithmName) throws IOException {
        // Process each of the bag metadata files and validate them. Also make sure that the
        // manifest files are named properly
        boolean foundManifest = false;
        boolean foundTagManifest = false;
        String manifestName = "manifest-" + manifestAlgorithmName + ".txt";
        String tagManifestName = "tagmanifest-" + manifestAlgorithmName + ".txt";
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            InputStream fileStream = zipFile.getInputStream(entry);
            String contents = convertStreamToString(fileStream);
            String entryName = entry.getName();
            if (entryName.equals("bag-info.txt")) {
                this.validateBagInfoFile(contents, zipFile, dataFileCount);
            } else if(entryName.equals("bagit.txt")) {
                this.validateBagitFile(contents, bagVersion);
            } else if (entryName.equals(manifestName)) {
                foundManifest = true;
            } else if (entryName.equals(tagManifestName)) {
                foundTagManifest = true;
            }
        }
        assertTrue(foundManifest);
        assertTrue(foundTagManifest);
    }

    /**
     * Test helper that returns each checksum algorithm with its name in the manifest file names,
     * for each BagIt version.
     *
     * @return The checksum algorithm, manifest algorithm name and BagIt version of each case
     */
    static Stream<Arguments> bagParameters() {
        String[][] algorithms = {{"MD5", "md5"}, {"SHA-1", "sha1"}, {"SHA-256", "sha256"}, {"SHA-512", "sha512"}};
        double[] versions = {0.97, 1.0};
        List<Arguments> cases = new ArrayList<>();
        for (String[] algorithm : algorithms) {
            for (double version : versions) {
                cases.add(Arguments.of(algorithm[0], algorithm[1], version));
            }
        }
        return cases.stream();
    }

    /**
     * Test helper that returns each case from bagParameters once with data files and once with tag files.
     *
     * @return The checksum algorithm, manifest algorithm name, BagIt version and tag file flag of each case
     */
    static Stream<Arguments> bagExportParameters() {
        return bagParameters().flatMap(bag -> {
            Object[] values = bag.get();
            return Stream.of(
                    Arguments.of(values[0], values[1], values[2], false),
                    Arguments.of(values[0], values[1], values[2], true));
        });
    }

    /**
     * Tests that a bag without a payload is streamed with its bagit.txt, bag-info.txt and manifest files.
     */
    @ParameterizedTest
    @MethodSource("bagParameters")
    public void testEmptyBag(String checksumAlgorithm, String manifestAlgorithmName, double bagVersion)
            throws IOException, NoSuchAlgorithmException {
        SpeedBagIt bag = new SpeedBagIt(bagVersion, checksumAlgorithm);

        Path bagFilePath = directory.resolve("emptyBag.zip");
        try (FileOutputStream fos = new FileOutputStream(bagFilePath.toFile())) {
            IOUtils.copy(bag.stream(), fos);
        }
        try (ZipFile zipFile = new ZipFile(bagFilePath.toFile())) {
            validateBagItFiles(zipFile, bagVersion, bag.getPayloadFileCount(), manifestAlgorithmName);
        }
    }

    /**
     * Tests that addFile places data files in the data files and tag files in the tag files.
     */
    @Test
    public void testAddFile() throws SpeedBagException, NoSuchAlgorithmException, IOException {

        // Keep track of the locations where the files should go so that
        // the bag can be tested against them
        List<String> expectedDataPaths = new ArrayList<>();
        expectedDataPaths.add("data/data_file1.csv");
        expectedDataPaths.add("data/data_file2.csv");

        List<String> expectedMetadataPaths = new ArrayList<>();
        expectedMetadataPaths.add("fetch.txt");
        expectedMetadataPaths.add("metadata/metadata.csv");

        SpeedBagIt bag = getStockBag();
        HashMap<String, SpeedFile> dataFiles = bag.getDataFiles();
        assertEquals(expectedDataPaths.size(), dataFiles.size());
        for (SpeedFile dataFile: dataFiles.values()) {
            assertTrue(expectedDataPaths.contains(dataFile.getPath()), dataFile.getPath());
        }

        HashMap<String, SpeedFile> tagFiles = bag.getTagFiles();
        assertEquals(expectedMetadataPaths.size(), tagFiles.size());
        for (SpeedFile tagFile: tagFiles.values()) {
            assertTrue(expectedMetadataPaths.contains(tagFile.getPath()), tagFile.getPath());
        }
    }

    /**
     * Tests that a bag with three data files or three tag files is streamed with its bagit.txt,
     * bag-info.txt and manifest files.
     */
    @ParameterizedTest
    @MethodSource("bagExportParameters")
    public void testBagExport(String checksumAlgorithm, String manifestAlgorithmName, double bagVersion,
                              boolean isTagFile) throws IOException, NoSuchAlgorithmException, SpeedBagException {
        SpeedBagIt bag = new SpeedBagIt(bagVersion, checksumAlgorithm);

        // Tag files are placed outside data/, which is reserved for the payload
        String directoryName = isTagFile ? "metadata" : "data";
        String[] fileContents = {"1234, 9876, 3845", "1234, 9876, 38", "1234, 9876"};
        for (int i = 0; i < fileContents.length; i++) {
            InputStream fileStream = new ByteArrayInputStream(fileContents[i].getBytes(StandardCharsets.UTF_8));
            bag.addFile(fileStream, String.format("%s/file%d.csv", directoryName, i + 1),
                    MessageDigest.getInstance(checksumAlgorithm), isTagFile);
        }

        Path bagFilePath = directory.resolve("bag.zip");
        try (FileOutputStream fos = new FileOutputStream(bagFilePath.toFile())) {
            IOUtils.copy(bag.stream(), fos);
        }
        try (ZipFile zipFile = new ZipFile(bagFilePath.toFile())) {
            validateBagItFiles(zipFile, bagVersion, bag.getPayloadFileCount(), manifestAlgorithmName);
        }
    }

    /**
     * Tests that when two data files or two tag files are added with the same path,
     * SpeedBagException is thrown.
     */
    @ParameterizedTest
    @CsvSource({"false, data/data_file1.csv", "true, tag/data_file1.csv"})
    void testDuplicateAddFile(boolean isTagFile, String path)
            throws IOException, NoSuchAlgorithmException, SpeedBagException {
        SpeedBagIt bag = new SpeedBagIt(1.0, "MD5");

        String dataFile1 = "1234, 9876, 3845";
        String dataFile2 = "trees, cars, bridges";
        InputStream dataFile1Stream = new ByteArrayInputStream(dataFile1.getBytes(StandardCharsets.UTF_8));
        InputStream dataFile2Stream = new ByteArrayInputStream(dataFile2.getBytes(StandardCharsets.UTF_8));
        bag.addFile(dataFile1Stream, path, isTagFile);
        assertThrows(SpeedBagException.class,  ()-> {
            bag.addFile(dataFile2Stream, path, isTagFile);
        });
    }

    /**
     * Tests that when streaming fails in the background thread, the caller
     * receives an IOException rather than silently getting a truncated stream.
     */
    @Test
    public void testStreamPropagatesError() throws IOException, NoSuchAlgorithmException, SpeedBagException {
        SpeedBagIt bag = new SpeedBagIt(1.0, "MD5");

        // Add a stream that throws on read, which will cause the background thread to fail
        InputStream failingStream = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("simulated read failure");
            }
        };
        bag.addFile(failingStream, "data/bad_file.csv", false);

        InputStream bagStream = bag.stream();
        assertThrows(IOException.class, () -> {
            byte[] buf = new byte[1024];
            while (bagStream.read(buf) != -1) {
                // drain until error
            }
        });
    }

    /**
     * Tests that when streaming fails in the background thread, the caller
     * receives an IOException rather than silently getting a truncated stream. It uses copyLarge
     * to test the method
     */
    @Test
    public void testStreamPropagatesError2() throws IOException, NoSuchAlgorithmException,
        SpeedBagException {
        SpeedBagIt bag = new SpeedBagIt(1.0, "MD5");

        // Add a stream that throws on read, which will cause the background thread to fail
        InputStream failingStream = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("simulated read failure");
            }
        };
        bag.addFile(failingStream, "data/bad_file.csv", false);

        InputStream bagStream = bag.stream();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(100000000);
        assertThrows(IOException.class, () -> {
            IOUtils.copyLarge(bagStream, outputStream);
        });
    }
}

