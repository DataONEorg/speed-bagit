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
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Enumeration;
import java.util.zip.ZipFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

import org.junit.jupiter.api.io.TempDir;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
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
    Path directory;

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
     * Creates a BagIt 1.0, MD5 bag with two data files and two tag files.
     *
     * @return The bag
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

        validateBagitFile(bagitTxtFile.getBytes(StandardCharsets.UTF_8), bagVersion);
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

        List<String> lines = tagFileLines(bagitTxtFile.getBytes(StandardCharsets.UTF_8));
        int customFieldCount = bagMetadata.size();
        assertEquals(customFieldCount + 2, lines.size(), bagitTxtFile);
        assertEquals("BagIt-Version: " + bagVersion, lines.get(customFieldCount));
        assertEquals("Tag-File-Character-Encoding: UTF-8", lines.get(customFieldCount + 1));

        // The custom fields are written in HashMap order, so they are compared as a map
        Map<String, String> writtenMetadata = new HashMap<>();
        for (String line : lines.subList(0, customFieldCount)) {
            String[] keyValue = line.split(": ", 2);
            assertEquals(2, keyValue.length, line);
            writtenMetadata.put(keyValue[0], keyValue[1]);
        }
        assertEquals(bagMetadata, writtenMetadata);
    }

    /**
     * Test helper that reads every entry of a zip file and returns a map of the entries -> bytes.
     *
     * @param zipFile The zip file to read
     * @return The bytes of each entry, keyed by entry name
     */
    private Map<String, byte[]> readEntries(ZipFile zipFile) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();
        while (zipEntries.hasMoreElements()) {
            ZipEntry entry = zipEntries.nextElement();
            try (InputStream entryStream = zipFile.getInputStream(entry)) {
                byte[] previous = entries.put(entry.getName(), IOUtils.toByteArray(entryStream));
                assertNull(previous, "The bag has two entries named " + entry.getName());
            }
        }
        return entries;
    }

    /**
     * Test helper that splits a tag file into lines
     * and asserts that the last line ends with a line break.
     *
     * @param contents The bytes of the tag file
     * @return The lines of the tag file, without their line breaks
     */
    private List<String> tagFileLines(byte[] contents) {
        String text = new String(contents, StandardCharsets.UTF_8);
        if (text.isEmpty()) {
            return new ArrayList<>();
        }
        assertTrue(text.endsWith("\n") || text.endsWith("\r"), "The last line has no line break: " + text);
        // BagIt allows for LF, CR and CRLF line break sytles
        return Arrays.asList(text.split("\\r\\n|\\n|\\r"));
    }

    /**
     * Validates that bagit.txt contains only the version line and the encoding line.
     *
     * @param contents The bytes of bagit.txt
     * @param bagVersion The version of the bag
     */
    private void validateBagitFile(byte[] contents, double bagVersion) {
        List<String> lines = tagFileLines(contents);
        assertEquals(2, lines.size(), lines.toString());
        assertEquals("BagIt-Version: " + bagVersion, lines.get(0));
        assertEquals("Tag-File-Character-Encoding: UTF-8", lines.get(1));
    }

    /**
     * Validates that bag-info.txt contains the bagging date, payload oxum and bag size.
     *
     * @param contents The bytes of bag-info.txt
     * @param dataFiles The contents of each data file added to the bag, keyed by path
     * @param startDate The date before the bag was streamed
     */
    private void validateBagInfoFile(byte[] contents, Map<String, String> dataFiles, LocalDate startDate) {
        Map<String, String> fields = new HashMap<>();
        for (String line : tagFileLines(contents)) {
            String[] keyValue = line.split(": ", 2);
            assertEquals(2, keyValue.length, line);
            assertNull(fields.put(keyValue[0], keyValue[1]), "bag-info.txt has two " + keyValue[0] + " fields");
        }
        assertEquals(new HashSet<>(Arrays.asList("Bagging-Date", "Payload-Oxum", "Bag-Size")), fields.keySet());

        long payloadBytes = 0;
        for (String dataFile : dataFiles.values()) {
            payloadBytes += dataFile.getBytes(StandardCharsets.UTF_8).length;
        }
        assertEquals(payloadBytes + "." + dataFiles.size(), fields.get("Payload-Oxum"));

        // The bag is dated when it is streamed. This is an issue if the test runs over midnight because they days
        // will be different. Set the date here...
        LocalDate baggingDate = LocalDate.parse(fields.get("Bagging-Date"));
        assertTrue(baggingDate.equals(startDate) || baggingDate.equals(startDate.plusDays(1)),
                "Bagging-Date " + baggingDate + " is not " + startDate + " or the day after");
    }

    /**
     * Validates that a manifest lists each expected path once, with the checksum of that path's bag entry.
     *
     * @param entries The bytes of each bag entry, keyed by entry name
     * @param manifestName The name of the manifest entry
     * @param expectedPaths The paths the manifest lists
     * @param checksumAlgorithm The MessageDigest algorithm of the manifest checksums
     */
    private void validateManifest(Map<String, byte[]> entries, String manifestName, Set<String> expectedPaths,
                                  String checksumAlgorithm) throws NoSuchAlgorithmException {
        Map<String, String> checksums = new HashMap<>();
        for (String line : tagFileLines(entries.get(manifestName))) {
            String[] checksumPath = line.split("\\s+", 2);
            assertEquals(2, checksumPath.length, line);
            assertNull(checksums.put(checksumPath[1], checksumPath[0]),
                    manifestName + " lists " + checksumPath[1] + " twice");
        }
        assertEquals(expectedPaths, checksums.keySet(), manifestName);

        for (Map.Entry<String, String> listed : checksums.entrySet()) {
            byte[] digest = MessageDigest.getInstance(checksumAlgorithm).digest(entries.get(listed.getKey()));
            assertEquals(Hex.encodeHexString(digest), listed.getValue(), manifestName + ": " + listed.getKey());
        }
    }

    /**
     * Validates a streamed bag against the data files and tag files that were added to it.
     *
     * @param zipFile The bag being validated
     * @param bagVersion The version of the bag
     * @param checksumAlgorithm The MessageDigest algorithm the bag was created with
     * @param manifestAlgorithmName The checksum algorithm name in the manifest file names, e.g. sha256
     * @param dataFiles The contents of each data file added to the bag, keyed by path
     * @param tagFiles The contents of each tag file added to the bag, keyed by path
     * @param startDate The date before the bag was streamed
     */
    private void validateBag(ZipFile zipFile, double bagVersion, String checksumAlgorithm,
                             String manifestAlgorithmName, Map<String, String> dataFiles,
                             Map<String, String> tagFiles, LocalDate startDate)
            throws IOException, NoSuchAlgorithmException {
        Map<String, byte[]> entries = readEntries(zipFile);
        String manifestName = "manifest-" + manifestAlgorithmName + ".txt";
        String tagManifestName = "tagmanifest-" + manifestAlgorithmName + ".txt";

        // The tag manifest lists every tag file except itself
        Set<String> tagManifestPaths = new HashSet<>(tagFiles.keySet());
        tagManifestPaths.addAll(Arrays.asList("bagit.txt", "bag-info.txt", manifestName));
        Set<String> expectedEntries = new HashSet<>(dataFiles.keySet());
        expectedEntries.addAll(tagManifestPaths);
        expectedEntries.add(tagManifestName);
        assertEquals(expectedEntries, entries.keySet());

        Map<String, String> addedFiles = new HashMap<>(dataFiles);
        addedFiles.putAll(tagFiles);
        for (Map.Entry<String, String> addedFile : addedFiles.entrySet()) {
            assertEquals(addedFile.getValue(), new String(entries.get(addedFile.getKey()), StandardCharsets.UTF_8),
                    addedFile.getKey());
        }

        validateBagitFile(entries.get("bagit.txt"), bagVersion);
        validateBagInfoFile(entries.get("bag-info.txt"), dataFiles, startDate);
        validateManifest(entries, manifestName, dataFiles.keySet(), checksumAlgorithm);
        validateManifest(entries, tagManifestName, tagManifestPaths, checksumAlgorithm);
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
    // SEPARATE_THREAD fails the test even when a read blocks and ignores the interrupt
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void testEmptyBag(String checksumAlgorithm, String manifestAlgorithmName, double bagVersion)
            throws IOException, NoSuchAlgorithmException {
        SpeedBagIt bag = new SpeedBagIt(bagVersion, checksumAlgorithm);

        LocalDate startDate = LocalDate.now();
        Path bagFilePath = directory.resolve("emptyBag.zip");
        try (FileOutputStream fos = new FileOutputStream(bagFilePath.toFile())) {
            IOUtils.copy(bag.stream(), fos);
        }
        try (ZipFile zipFile = new ZipFile(bagFilePath.toFile())) {
            validateBag(zipFile, bagVersion, checksumAlgorithm, manifestAlgorithmName,
                    new HashMap<>(), new HashMap<>(), startDate);
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
    // SEPARATE_THREAD fails the test even when a read blocks and ignores the interrupt
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void testBagExport(String checksumAlgorithm, String manifestAlgorithmName, double bagVersion,
                              boolean isTagFile) throws IOException, NoSuchAlgorithmException, SpeedBagException {
        SpeedBagIt bag = new SpeedBagIt(bagVersion, checksumAlgorithm);

        // Tag files are placed outside data/, which is reserved for the payload
        String directoryName = isTagFile ? "metadata" : "data";
        String[] fileContents = {"1234, 9876, 3845", "1234, 9876, 38", "1234, 9876"};
        Map<String, String> dataFiles = new HashMap<>();
        Map<String, String> tagFiles = new HashMap<>();
        for (int i = 0; i < fileContents.length; i++) {
            String path = String.format("%s/file%d.csv", directoryName, i + 1);
            InputStream fileStream = new ByteArrayInputStream(fileContents[i].getBytes(StandardCharsets.UTF_8));
            bag.addFile(fileStream, path, MessageDigest.getInstance(checksumAlgorithm), isTagFile);
            if (isTagFile) {
                tagFiles.put(path, fileContents[i]);
            } else {
                dataFiles.put(path, fileContents[i]);
            }
        }

        LocalDate startDate = LocalDate.now();
        Path bagFilePath = directory.resolve("bag.zip");
        try (FileOutputStream fos = new FileOutputStream(bagFilePath.toFile())) {
            IOUtils.copy(bag.stream(), fos);
        }
        try (ZipFile zipFile = new ZipFile(bagFilePath.toFile())) {
            validateBag(zipFile, bagVersion, checksumAlgorithm, manifestAlgorithmName,
                    dataFiles, tagFiles, startDate);
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
    // SEPARATE_THREAD fails the test even when a read blocks and ignores the interrupt
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
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
    // SEPARATE_THREAD fails the test even when a read blocks and ignores the interrupt
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
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

