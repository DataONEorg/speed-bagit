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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
        String notesFile = "Collected at the north site";
        String metadataFile = "extra metadata";
        InputStream notesFileStream = new ByteArrayInputStream(notesFile.getBytes(StandardCharsets.UTF_8));
        InputStream metadataFile2Stream = new ByteArrayInputStream(metadataFile.getBytes(StandardCharsets.UTF_8));
        bag.addFile(notesFileStream, "notes.txt", true);
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
     * Test helper that decodes percent-encoded percent signs, carriage returns and line feeds
     * in a manifest path. This test is derived from the behavior outlined in RFC 8493.
     *
     * @param manifestPath The path as it is written in a manifest
     * @return The path of the file
     */
    private static String decodeManifestPath(String manifestPath) {
        Matcher encoded = Pattern.compile("%(25|0[dD]|0[aA])").matcher(manifestPath);
        StringBuffer path = new StringBuffer();
        while (encoded.find()) {
            String character = String.valueOf((char) Integer.parseInt(encoded.group(1), 16));
            encoded.appendReplacement(path, Matcher.quoteReplacement(character));
        }
        encoded.appendTail(path);
        return path.toString();
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
            String path = decodeManifestPath(checksumPath[1]);
            assertNull(checksums.put(path, checksumPath[0]), manifestName + " lists " + path + " twice");
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
        expectedMetadataPaths.add("notes.txt");
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
     * InvalidBagPathException is thrown.
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
        assertThrows(InvalidBagPathException.class, () -> {
            bag.addFile(dataFile2Stream, path, isTagFile);
        });
    }

    /**
     * Test helper that returns file names in the bag root that the BagIt specification reserves.
     * Names that start with manifest-* or tagmanifest-* are reserved.
     *
     * @return The reserved file names
     */
    static String[] reservedFileNames() {
        return new String[] {"bagit.txt", "bag-info.txt", "fetch.txt", "manifest-md5.txt",
                "manifest-sha256.txt", "manifest-md5.txt.bak", "tagmanifest-md5.txt",
                "tagmanifest-sha512.txt", "tagmanifest-sha1.txt.old"};
    }

    /**
     * Test helper that returns each reserved file name as a data file and as a tag file.
     *
     * @return The reserved file name and isTagFile flag of each case
     */
    static Stream<Arguments> reservedFileNameParameters() {
        List<Arguments> cases = new ArrayList<>();
        for (String reservedName : reservedFileNames()) {
            cases.add(Arguments.of(reservedName, false));
            cases.add(Arguments.of(reservedName, true));
        }
        return cases.stream();
    }

    /**
     * Test helper that returns each reserved file name under the data directory as a data file,
     * and under a tag directory and a nested tag directory as a tag file.
     *
     * @return The path and isTagFile flag of each case
     */
    static Stream<Arguments> reservedNameInSubdirectoryParameters() {
        List<Arguments> cases = new ArrayList<>();
        for (String reservedName : reservedFileNames()) {
            cases.add(Arguments.of("data/" + reservedName, false));
            cases.add(Arguments.of("metadata/" + reservedName, true));
            cases.add(Arguments.of("metadata/nested/" + reservedName, true));
        }
        return cases.stream();
    }

    /**
     * Test helper that adds one file to a BagIt 1.0, MD5 bag, streams the bag and validates the zip.
     *
     * @param path The path of the file, relative to the bag root
     * @param isTagFile True when the file is a tag file
     */
    private void addFileAndValidateBag(String path, boolean isTagFile)
            throws IOException, NoSuchAlgorithmException, SpeedBagException {
        addFileAndValidateBag(path, isTagFile, "MD5", "md5", "MD5");
    }

    /**
     * Test helper that adds one file to a BagIt 1.0 bag with a MessageDigest of the given algorithm,
     * streams the bag and validates the zip.
     *
     * @param path The path of the file, relative to the bag root
     * @param isTagFile True when the file is a tag file
     * @param bagAlgorithm The checksum algorithm of the bag
     * @param manifestAlgorithmName The checksum algorithm name in the manifest file names, e.g. sha256
     * @param digestAlgorithm The algorithm of the MessageDigest that the file is added with
     */
    private void addFileAndValidateBag(String path, boolean isTagFile, String bagAlgorithm,
                                       String manifestAlgorithmName, String digestAlgorithm)
            throws IOException, NoSuchAlgorithmException, SpeedBagException {
        SpeedBagIt bag = new SpeedBagIt(1.0, bagAlgorithm);
        String contents = "user content";
        bag.addFile(new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8)), path,
                MessageDigest.getInstance(digestAlgorithm), isTagFile);
        Map<String, String> dataFiles = new HashMap<>();
        Map<String, String> tagFiles = new HashMap<>();
        if (isTagFile) {
            tagFiles.put(path, contents);
        } else {
            dataFiles.put(path, contents);
        }
        streamAndValidateBag(bag, bagAlgorithm, manifestAlgorithmName, dataFiles, tagFiles);
    }

    /**
     * Test helper that streams a BagIt 1.0 bag to a zip file and validates the zip.
     *
     * @param bag The bag being streamed
     * @param bagAlgorithm The checksum algorithm of the bag
     * @param manifestAlgorithmName The checksum algorithm name in the manifest file names, e.g. sha256
     * @param dataFiles The contents of each data file added to the bag, keyed by path
     * @param tagFiles The contents of each tag file added to the bag, keyed by path
     */
    private void streamAndValidateBag(SpeedBagIt bag, String bagAlgorithm, String manifestAlgorithmName,
                                      Map<String, String> dataFiles, Map<String, String> tagFiles)
            throws IOException, NoSuchAlgorithmException {
        LocalDate startDate = LocalDate.now();
        Path bagFilePath = directory.resolve("bag.zip");
        try (FileOutputStream fos = new FileOutputStream(bagFilePath.toFile())) {
            IOUtils.copy(bag.stream(), fos);
        }
        try (ZipFile zipFile = new ZipFile(bagFilePath.toFile())) {
            validateBag(zipFile, 1.0, bagAlgorithm, manifestAlgorithmName, dataFiles, tagFiles, startDate);
        }
    }

    /**
     * Tests that a file with a reserved name in a subdirectory is added and streamed into a valid bag.
     */
    @ParameterizedTest
    @MethodSource("reservedNameInSubdirectoryParameters")
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void testReservedNameInSubdirectory(String path, boolean isTagFile)
            throws IOException, NoSuchAlgorithmException, SpeedBagException {
        addFileAndValidateBag(path, isTagFile);
    }

    /**
     * Tests that a file whose path is a reserved file name in the bag root is rejected.
     */
    @ParameterizedTest
    @MethodSource("reservedFileNameParameters")
    public void testAddFileReservedName(String path, boolean isTagFile) throws IOException {
        SpeedBagIt bag = new SpeedBagIt(1.0, "MD5");
        InputStream file = new ByteArrayInputStream("contents".getBytes(StandardCharsets.UTF_8));
        assertThrows(InvalidBagPathException.class, () -> bag.addFile(file, path, isTagFile));
        assertEquals(0, bag.getDataFiles().size());
        assertEquals(0, bag.getTagFiles().size());
    }

    /**
     * Test helper that returns tag file paths in a directory whose name is a reserved file name.
     *
     * @return The tag file paths
     */
    static Stream<String> reservedNameAsDirectoryParameters() {
        List<String> paths = new ArrayList<>();
        for (String reservedName : reservedFileNames()) {
            paths.add(reservedName + "/notes.txt");
            paths.add(reservedName + "/nested/notes.txt");
        }
        return paths.stream();
    }

    /**
     * Tests that a tag file in a directory whose name is a reserved file name is rejected.
     */
    @ParameterizedTest
    @MethodSource("reservedNameAsDirectoryParameters")
    public void testAddFileReservedNameAsDirectory(String path) throws IOException {
        SpeedBagIt bag = new SpeedBagIt(1.0, "MD5");
        InputStream file = new ByteArrayInputStream("contents".getBytes(StandardCharsets.UTF_8));
        assertThrows(InvalidBagPathException.class, () -> bag.addFile(file, path, true));
        assertEquals(0, bag.getTagFiles().size());
    }

    /**
     * Tests that a file is rejected when its path is a directory of an added file, or when a parent
     * directory of its path is an added file.
     */
    @ParameterizedTest
    @CsvSource({
            "data/a, false, data/a/b.csv, false",
            "data/a/b.csv, false, data/a, false",
            "data/a, false, data/a/b/c.csv, false",
            "data/a/b/c.csv, false, data/a, false",
            "data/a/b/c.csv, false, data/a/b, false",
            "metadata, true, metadata/notes.txt, true",
            "metadata/notes.txt, true, metadata, true",
            "metadata/notes, true, metadata/notes/nested.txt, true",
            "metadata/notes/nested.txt, true, metadata/notes, true"})
    public void testAddFileDirectoryConflict(String firstPath, boolean isFirstTagFile, String secondPath,
                                             boolean isSecondTagFile)
            throws IOException, NoSuchAlgorithmException, SpeedBagException {
        SpeedBagIt bag = new SpeedBagIt(1.0, "MD5");
        bag.addFile(new ByteArrayInputStream("first".getBytes(StandardCharsets.UTF_8)), firstPath, isFirstTagFile);
        InputStream secondFile = new ByteArrayInputStream("second".getBytes(StandardCharsets.UTF_8));
        assertThrows(InvalidBagPathException.class, () -> bag.addFile(secondFile, secondPath, isSecondTagFile));
        assertEquals(1, bag.getDataFiles().size() + bag.getTagFiles().size());
    }

    /**
     * Tests that two files whose paths share a prefix without a directory conflict are added and
     * streamed into a valid bag.
     */
    @ParameterizedTest
    @CsvSource({
            "data/a, false, data/ab/c.csv, false",
            "data/ab/c.csv, false, data/a, false",
            "data/a.csv, false, data/a/b.csv, false",
            "data/a/b.csv, false, data/a/b.csv.d/c.csv, false",
            "metadata, true, metadata2/notes.txt, true",
            "metadata.txt, true, metadata/notes.txt, true",
            "data/notes, false, notes/nested.txt, true"})
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void testAddFileSharedPrefix(String firstPath, boolean isFirstTagFile, String secondPath,
                                        boolean isSecondTagFile)
            throws IOException, NoSuchAlgorithmException, SpeedBagException {
        SpeedBagIt bag = new SpeedBagIt(1.0, "MD5");
        Map<String, String> dataFiles = new HashMap<>();
        Map<String, String> tagFiles = new HashMap<>();
        // The contents of each file are its path, so the two checksums differ and each is listed in the manifest
        bag.addFile(new ByteArrayInputStream(firstPath.getBytes(StandardCharsets.UTF_8)), firstPath, isFirstTagFile);
        bag.addFile(new ByteArrayInputStream(secondPath.getBytes(StandardCharsets.UTF_8)), secondPath, isSecondTagFile);
        (isFirstTagFile ? tagFiles : dataFiles).put(firstPath, firstPath);
        (isSecondTagFile ? tagFiles : dataFiles).put(secondPath, secondPath);
        streamAndValidateBag(bag, "MD5", "md5", dataFiles, tagFiles);
    }

    /**
     * Tests that a tag file whose name differs only in case from a reserved name is added and
     * streamed into a valid bag.
     */
    @ParameterizedTest
    @ValueSource(strings = {"BagIt.txt", "BAG-INFO.TXT", "Fetch.txt", "Manifest-SHA256.txt",
            "Manifest-MD5.txt", "TagManifest-MD5.txt"})
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void testAddFileReservedNameDifferentCase(String path)
            throws IOException, NoSuchAlgorithmException, SpeedBagException {
        addFileAndValidateBag(path, true);
    }

    /**
     * Tests that a tag file in the bag root whose name contains a reserved name is added and
     * streamed into a valid bag.
     */
    @ParameterizedTest
    @ValueSource(strings = {"manifest.txt", "tagmanifest.txt", "my-manifest-md5.txt", "bagit.txt.old",
            "bag-info.csv", "fetch.txt.orig"})
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void testAddFileSimilarName(String path)
            throws IOException, NoSuchAlgorithmException, SpeedBagException {
        addFileAndValidateBag(path, true);
    }

    /**
     * Test helper that returns paths that cannot be written to a bag. The idea here
     * is to build a small catalouge of behavior that shouldn't be supported.
     *
     * @return The path and isTagFile flag of each case
     */
    static Stream<Arguments> invalidPathParameters() {
        return Stream.of(
                Arguments.of(null, false),
                Arguments.of(null, true),
                Arguments.of("", false),
                Arguments.of("", true),
                Arguments.of("/data/file.csv", false),
                Arguments.of("/metadata/file.csv", true),
                Arguments.of("data\\file.csv", false),
                Arguments.of("metadata\\file.csv", true),
                Arguments.of("data/../file.csv", false),
                Arguments.of("data/./file.csv", false),
                Arguments.of("./data/file.csv", false),
                Arguments.of("data//file.csv", false),
                Arguments.of("data/..", false),
                Arguments.of("..", true),
                Arguments.of("data/directory/", false),
                Arguments.of("data/file.csv/", false),
                Arguments.of("metadata/../data/file.csv", true),
                Arguments.of("metadata/./notes.txt", true),
                Arguments.of("metadata//notes.txt", true),
                Arguments.of("file.csv", false),
                Arguments.of("metadata/file.csv", false),
                Arguments.of("datafile.csv", false),
                Arguments.of("data", false),
                Arguments.of("data/file.csv", true),
                Arguments.of("data/nested/file.csv", true),
                Arguments.of("data", true));
    }

    /**
     * Tests that adding a file with a path that cannot be written to a bag throws InvalidBagPathException.
     */
    @ParameterizedTest
    @MethodSource("invalidPathParameters")
    public void testAddFileInvalidPath(String path, boolean isTagFile) throws IOException {
        SpeedBagIt bag = new SpeedBagIt(1.0, "MD5");
        InputStream fileStream = new ByteArrayInputStream("user content".getBytes(StandardCharsets.UTF_8));

        assertThrows(InvalidBagPathException.class, () -> bag.addFile(fileStream, path, isTagFile));
        assertTrue(bag.getDataFiles().isEmpty());
        assertTrue(bag.getTagFiles().isEmpty());
    }

    /**
     * Tests that a file with a path that is *close* to an invalid path but is still valid. This
     * is to help find/prevent under/over fitting to the Bagit RFC filepath constraints.
     */
    @ParameterizedTest
    @CsvSource({
            "data/.keep, false",
            "data/..hidden.csv, false",
            "data/file..csv, false",
            "data/a b.csv, false",
            "data/é.csv, false",
            "data/deeply/nested/directory/file.csv, false",
            "data/data/file.csv, false",
            "metadata/.hidden, true",
            "metadata/data/notes.txt, true",
            "database/notes.txt, true"})
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void testAddFileValidPath(String path, boolean isTagFile)
            throws IOException, NoSuchAlgorithmException, SpeedBagException {
        addFileAndValidateBag(path, isTagFile);
    }

    /**
     * Test helper that returns each pair of different checksum algorithms, for a data file and a tag file.
     *
     * @return The bag algorithm, MessageDigest algorithm and isTagFile flag of each case
     */
    static Stream<Arguments> otherChecksumAlgorithmParameters() {
        String[] algorithms = {"MD5", "SHA-1", "SHA-256", "SHA-512"};
        List<Arguments> cases = new ArrayList<>();
        for (String bagAlgorithm : algorithms) {
            for (String digestAlgorithm : algorithms) {
                if (!bagAlgorithm.equals(digestAlgorithm)) {
                    cases.add(Arguments.of(bagAlgorithm, digestAlgorithm, false));
                    cases.add(Arguments.of(bagAlgorithm, digestAlgorithm, true));
                }
            }
        }
        return cases.stream();
    }

    /**
     * Tests that adding a file with a MessageDigest of another checksum algorithm than the bag's
     * throws SpeedBagException.
     */
    @ParameterizedTest
    @MethodSource("otherChecksumAlgorithmParameters")
    public void testAddFileOtherChecksumAlgorithm(String bagAlgorithm, String digestAlgorithm, boolean isTagFile)
            throws IOException, NoSuchAlgorithmException {
        SpeedBagIt bag = new SpeedBagIt(1.0, bagAlgorithm);
        InputStream fileStream = new ByteArrayInputStream("user content".getBytes(StandardCharsets.UTF_8));
        MessageDigest digest = MessageDigest.getInstance(digestAlgorithm);
        String path = isTagFile ? "metadata/file.csv" : "data/file.csv";

        SpeedBagException exception = assertThrows(SpeedBagException.class,
                () -> bag.addFile(fileStream, path, digest, isTagFile));
        assertEquals(SpeedBagException.class, exception.getClass());
        assertTrue(bag.getDataFiles().isEmpty());
        assertTrue(bag.getTagFiles().isEmpty());
    }

    /**
     * Test helper that returns MessageDigest names that are other spellings of a bag's checksum
     * algorithm, for a data file and a tag file.
     *
     * @return The bag algorithm, manifest algorithm name, MessageDigest algorithm and isTagFile flag of each case
     */
    static Stream<Arguments> sameChecksumAlgorithmParameters() {
        String[][] spellings = {
                {"MD5", "md5", "md5"},
                {"SHA-1", "sha1", "SHA1"},
                {"SHA-256", "sha256", "SHA256"},
                {"SHA-256", "sha256", "sha-256"},
                {"SHA-512", "sha512", "SHA512"},
                {"SHA-512", "sha512", "sha-512"}};
        List<Arguments> cases = new ArrayList<>();
        for (String[] spelling : spellings) {
            cases.add(Arguments.of(spelling[0], spelling[1], spelling[2], false));
            cases.add(Arguments.of(spelling[0], spelling[1], spelling[2], true));
        }
        return cases.stream();
    }

    /**
     * Tests that a file added with a MessageDigest whose name is another spelling of the bag's
     * checksum algorithm is streamed into a valid bag.
     */
    @ParameterizedTest
    @MethodSource("sameChecksumAlgorithmParameters")
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void testAddFileSameChecksumAlgorithm(String bagAlgorithm, String manifestAlgorithmName,
                                                 String digestAlgorithm, boolean isTagFile)
            throws IOException, NoSuchAlgorithmException, SpeedBagException {
        String path = isTagFile ? "metadata/file.csv" : "data/file.csv";
        addFileAndValidateBag(path, isTagFile, bagAlgorithm, manifestAlgorithmName, digestAlgorithm);
    }

    /**
     * Test helper that returns paths with characters that RFC 8493 section 2.1.3 does and does not
     * percent-encode, with the form of each path in a manifest.
     *
     * @return The path, isTagFile flag and manifest path of each case
     */
    static Stream<Arguments> percentEncodedPathParameters() {
        return Stream.of(
                Arguments.of("data/100%.csv", false, "data/100%25.csv"),
                Arguments.of("data/%start.csv", false, "data/%25start.csv"),
                Arguments.of("data/end%", false, "data/end%25"),
                Arguments.of("data/line\nfeed.csv", false, "data/line%0Afeed.csv"),
                Arguments.of("data/carriage\rreturn.csv", false, "data/carriage%0Dreturn.csv"),
                Arguments.of("data/windows\r\nline.csv", false, "data/windows%0D%0Aline.csv"),
                Arguments.of("data/%\r\n.csv", false, "data/%25%0D%0A.csv"),
                Arguments.of("data/already%0A.csv", false, "data/already%250A.csv"),
                // Only %, CR and LF are encoded, whic means spaces and a tabs are written as-is
                Arguments.of("data/a b.csv", false, "data/a b.csv"),
                Arguments.of("data/tab\t.csv", false, "data/tab\t.csv"),
                Arguments.of("metadata/50%.txt", true, "metadata/50%25.txt"),
                Arguments.of("metadata/line\nfeed.txt", true, "metadata/line%0Afeed.txt"));
    }

    /**
     * Tests that a percent sign, carriage return or line feed in a path is percent-encoded in the
     * manifest, that no other character is encoded, and that the bag is valid.
     */
    @ParameterizedTest
    @MethodSource("percentEncodedPathParameters")
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void testPercentEncodedPath(String path, boolean isTagFile, String manifestPath)
            throws IOException, NoSuchAlgorithmException, SpeedBagException {
        addFileAndValidateBag(path, isTagFile);

        String manifestName = isTagFile ? "tagmanifest-md5.txt" : "manifest-md5.txt";
        try (ZipFile zipFile = new ZipFile(directory.resolve("bag.zip").toFile())) {
            String manifest = new String(readEntries(zipFile).get(manifestName), StandardCharsets.UTF_8);
            assertTrue(manifest.contains(" " + manifestPath + "\n"), manifest);
        }
    }

    /**
     * Test helper that returns pairs of paths that differ only in case.
     *
     * @return The path and isTagFile flag of each file in each case
     */
    static Stream<Arguments> pathsDifferInCaseParameters() {
        return Stream.of(
                Arguments.of("data/results.csv", false, "data/Results.csv", false),
                Arguments.of("data/dir/a.csv", false, "data/Dir/a.csv", false),
                Arguments.of("metadata/notes.txt", true, "metadata/Notes.txt", true),
                Arguments.of("metadata/notes.txt", true, "Metadata/notes.txt", true),
                Arguments.of("data/a.csv", false, "Data/a.csv", true));
    }

    /**
     * Tests that two files whose paths differ only in case are added and streamed into a valid bag.
     */
    @ParameterizedTest
    @MethodSource("pathsDifferInCaseParameters")
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void testAddFilePathsDifferInCase(String firstPath, boolean firstIsTagFile,
                                             String secondPath, boolean secondIsTagFile)
            throws IOException, NoSuchAlgorithmException, SpeedBagException {
        SpeedBagIt bag = new SpeedBagIt(1.0, "MD5");
        Map<String, String> dataFiles = new HashMap<>();
        Map<String, String> tagFiles = new HashMap<>();
        String[][] files = {{firstPath, String.valueOf(firstIsTagFile), "1, 2, 3"},
                {secondPath, String.valueOf(secondIsTagFile), "4, 5, 6"}};
        for (String[] file : files) {
            boolean isTagFile = Boolean.parseBoolean(file[1]);
            bag.addFile(new ByteArrayInputStream(file[2].getBytes(StandardCharsets.UTF_8)), file[0], isTagFile);
            if (isTagFile) {
                tagFiles.put(file[0], file[2]);
            } else {
                dataFiles.put(file[0], file[2]);
            }
        }

        streamAndValidateBag(bag, "MD5", "md5", dataFiles, tagFiles);
    }

    /**
     * Tests that a tag file whose first directory differs only in case from data/ is added and
     * streamed into a valid bag.
     */
    @ParameterizedTest
    @ValueSource(strings = {"Data/notes.txt", "DATA/nested/notes.txt", "Data"})
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void testAddFileTagFileInPayloadDirectoryDifferentCase(String path)
            throws IOException, NoSuchAlgorithmException, SpeedBagException {
        addFileAndValidateBag(path, true);
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

    /**
     * Tests that each manifest line ends in a line feed when the platform line separator is different.
     *
     * @param lineSeparator The value of the line.separator system property during the test
     */
    @ParameterizedTest
    @ValueSource(strings = {"\r\n", "\r"})
    public void testBagFileToStringLineFeed(String lineSeparator) {
        Map<String, String> manifest = new LinkedHashMap<>();
        manifest.put("abc", "data/a.csv");
        manifest.put("def", "data/b.csv");
        String originalLineSeparator = System.getProperty("line.separator");
        System.setProperty("line.separator", lineSeparator);
        try {
            assertEquals("abc data/a.csv\ndef data/b.csv\n", SpeedBagIt.bagFileToString(manifest));
        } finally {
            System.setProperty("line.separator", originalLineSeparator);
        }
    }

    /**
     * Test helper that reads a bag stream to the end.
     *
     * @param bag The bag that is streamed
     * @return The bytes of the bag
     * @throws IOException Thrown when the bag could not be streamed
     * @throws NoSuchAlgorithmException Thrown when the bag's checksum algorithm is unsupported
     */
    private static byte[] readBag(SpeedBagIt bag) throws IOException, NoSuchAlgorithmException {
        try (InputStream bagStream = bag.stream()) {
            return IOUtils.toByteArray(bagStream);
        }
    }

    /**
     * Tests that a bag cannot be streamed a second time.
     */
    @Test
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void testStreamTwice() throws IOException, NoSuchAlgorithmException, SpeedBagException {
        SpeedBagIt bag = new SpeedBagIt(1.0, "MD5");
        bag.addFile(new ByteArrayInputStream("contents".getBytes(StandardCharsets.UTF_8)), "data/a.csv", false);
        readBag(bag);

        assertThrows(IllegalStateException.class, bag::stream);
    }

    /**
     * Test helper that returns a stream which records when it is closed.
     *
     * @param contents The bytes that the stream returns
     * @param isClosed Set to true when the stream is closed
     * @return The stream
     */
    private static InputStream closeRecordingStream(byte[] contents, AtomicBoolean isClosed) {
        return new ByteArrayInputStream(contents) {
            @Override
            public void close() throws IOException {
                isClosed.set(true);
                super.close();
            }
        };
    }

    /**
     * Test helper that returns a stream which throws on every read and records when it is closed.
     *
     * @param isClosed Set to true when the stream is closed
     * @return The stream
     */
    private static InputStream failingStream(AtomicBoolean isClosed) {
        return new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("simulated read failure");
            }

            @Override
            public void close() {
                isClosed.set(true);
            }
        };
    }

    /**
     * Tests that the input stream of every file is closed when streaming fails, including the files
     * that were not written.
     *
     * @param isFailingFileTagFile True when the file whose stream fails is a tag file
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void testStreamFailureClosesInputStreams(boolean isFailingFileTagFile)
            throws IOException, NoSuchAlgorithmException, SpeedBagException {
        SpeedBagIt bag = new SpeedBagIt(1.0, "MD5");
        Map<String, AtomicBoolean> isClosedByPath = new HashMap<>();
        for (int i = 0; i < 3; i++) {
            String dataPath = "data/file" + i + ".csv";
            String tagPath = "metadata/file" + i + ".txt";
            isClosedByPath.put(dataPath, new AtomicBoolean());
            isClosedByPath.put(tagPath, new AtomicBoolean());
            byte[] contents = ("contents " + i).getBytes(StandardCharsets.UTF_8);
            bag.addFile(closeRecordingStream(contents, isClosedByPath.get(dataPath)), dataPath, false);
            bag.addFile(closeRecordingStream(contents, isClosedByPath.get(tagPath)), tagPath, true);
        }
        String failingPath = isFailingFileTagFile ? "metadata/failing.txt" : "data/failing.csv";
        isClosedByPath.put(failingPath, new AtomicBoolean());
        bag.addFile(failingStream(isClosedByPath.get(failingPath)), failingPath, isFailingFileTagFile);

        assertThrows(IOException.class, () -> readBag(bag));
        for (Map.Entry<String, AtomicBoolean> entry : isClosedByPath.entrySet()) {
            assertTrue(entry.getValue().get(), "The input stream of " + entry.getKey() + " is open");
        }
    }

    /**
     * Tests that a bag whose streaming fails has no zip end of central directory record, so the
     * partial bag cannot be read as a complete zip.
     */
    @Test
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void testStreamFailureWritesNoCentralDirectory()
            throws IOException, NoSuchAlgorithmException, SpeedBagException {
        SpeedBagIt bag = new SpeedBagIt(1.0, "MD5");
        bag.addFile(new ByteArrayInputStream("contents".getBytes(StandardCharsets.UTF_8)), "metadata/a.txt", true);
        bag.addFile(failingStream(new AtomicBoolean()), "data/failing.csv", false);

        ByteArrayOutputStream partialBag = new ByteArrayOutputStream();
        InputStream bagStream = bag.stream();
        assertThrows(IOException.class, () -> IOUtils.copy(bagStream, partialBag));

        byte[] endOfCentralDirectory = {0x50, 0x4b, 0x05, 0x06};
        byte[] bytes = partialBag.toByteArray();
        for (int i = 0; i + endOfCentralDirectory.length <= bytes.length; i++) {
            assertFalse(Arrays.equals(endOfCentralDirectory, Arrays.copyOfRange(bytes, i, i + 4)),
                    "The partial bag contains an end of central directory record at byte " + i);
        }
    }

    /**
     * Tests that when an input stream throws an unchecked exception from close(), the reader receives an
     * IOException caused by that exception and every other input stream is closed.
     */
    @Test
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void testStreamFailureWithUncheckedCloseException()
            throws IOException, NoSuchAlgorithmException, SpeedBagException {
        SpeedBagIt bag = new SpeedBagIt(1.0, "MD5");
        InputStream closeFailingStream = new ByteArrayInputStream("contents".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public void close() {
                throw new IllegalStateException("simulated close failure");
            }
        };
        bag.addFile(closeFailingStream, "data/a.csv", false);
        AtomicBoolean isTagFileClosed = new AtomicBoolean();
        byte[] contents = "tag contents".getBytes(StandardCharsets.UTF_8);
        bag.addFile(closeRecordingStream(contents, isTagFileClosed), "metadata/notes.txt", true);

        IOException exception = assertThrows(IOException.class, () -> readBag(bag));
        assertInstanceOf(IllegalStateException.class, exception.getCause());
        assertTrue(isTagFileClosed.get(), "The input stream of metadata/notes.txt is open");
    }
}
