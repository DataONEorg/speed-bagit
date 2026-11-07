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
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 *  The main interface for creating a BagIt compliant zip file. The SpeedBagIt class
 *  manages SpeedFile objects, which represent files that will be written to the output
 *  stream.
 *  This class is also responsible for generating the tagmanifest and manfiest files along
 *  with the default BagIt files: bagit.txt & bag-info.txt.
 *
 */
public class SpeedBagIt {
    private final static Log logger = LogFactory.getLog(SpeedBagIt.class);
    // Regex that matches every file in the bag root that starts with manifest- or tagmanifest-.
    private final static Pattern MANIFEST_FILE_NAME = Pattern.compile("(tag)?manifest-[^/]*");
    // The directory that contains the payload files
    private final static String PAYLOAD_DIRECTORY = "data";

    // The properties file holding string constants
    private Properties properties;
    // Version that the bag is (0.97, 1.0, etc)
    public double version;
    // Contents of tagmanifest-{algo}.txt file
    public Map<String, String> tagManifestFile;
    // Contents of manifest-{algo}.txt file
    public Map<String, String> dataManifestFile;
    // The name of the algorithm. Should be compatible with the MessageDigest class
    public String checksumAlgorithm;
    // Map of key-values that go in the bagit.txt file
    public Map<String, String> bagitMetadata;

    // Every directory that contains an added file, e.g. data and data/nested for data/nested/a.csv
    private HashSet<String> directoryPaths;

    // Containers for keeping track of tag & data files, keyed off of
    // their relative file path
    private HashMap<String, SpeedFile> dataFiles;
    private HashMap<String, SpeedFile> tagFiles;
    // The path of each data and tag file, keyed by the path in lower case
    private HashMap<String, String> pathsByLowerCase;
    // True after stream() is called
    private boolean isStreamed;
    
    // An ExecutorService to run the piped stream in another thread
    private static ExecutorService executor = null;
    static {
        // use a shared executor service with nThreads == one less than available processors
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        int nThreads = availableProcessors * 1;
        nThreads--;
        nThreads = Math.max(1, nThreads);
        executor = Executors.newFixedThreadPool(nThreads);  
    }

    /**
     * Creates a new instance of a SpeedBagIt. This constructor supports adding
     * additional metadata to the bagit.txt file, when created.
     * @param version:           The bag version (0.97, 1.0, etc)
     * @param checksumAlgorithm: The name of the algorithm used to checksum the files
     * @param bagitMetadata:     A key-value mapping of metadata that belongs in bagit.txt
     */
    public SpeedBagIt(double version,
                    String checksumAlgorithm,
                    Map<String, String> bagitMetadata) throws IOException {
        this.version = version;
        this.checksumAlgorithm = checksumAlgorithm;
        this.dataFiles = new HashMap<>();
        this.tagFiles = new HashMap<>();
        this.pathsByLowerCase = new HashMap<>();
        this.directoryPaths = new HashSet<>();
        this.bagitMetadata = bagitMetadata;
        this.dataManifestFile = new HashMap<> ();
        this.tagManifestFile = new HashMap<> ();

        this.properties = new Properties();
        this.properties.load(Objects.requireNonNull(this.getClass().
                getClassLoader().getResourceAsStream("speed-bagit.properties")));
    }

    /**
     * Creates a new SpeedBagIt object. This constructor requires the bare
     * minimum arguments to make a valid bag.
     * @param version:           The bag version (0.97, 1.0, etc)
     * @param checksumAlgorithm: The name of the algorithm used to checksum the files
     */
    public SpeedBagIt(double version,
                      String checksumAlgorithm) throws IOException {
        this(version, checksumAlgorithm, new HashMap<>());
    }

    /**
     * Adds a stream of data to the bag.
     *
     * @param file:      The stream representing a file or data that will be placed in the bag
     * @param bagPath:   The path, relative to the bag root where the file belongs
     * @param checksum:  A MessageDigest object that will hold the checksum
     * @param isTagFile: Boolean set to True when the file is a tag file
     * @throws InvalidBagPathException Thrown when the path cannot be written to the bag, starts with a name
     *                                 reserved by the BagIt specification, was already added, or conflicts
     *                                 with the directory of another file
     * @throws SpeedBagException Thrown when the checksum algorithm differs from the bag's algorithm
     */
    public void addFile(InputStream file, String bagPath, MessageDigest checksum, boolean isTagFile)
            throws SpeedBagException {
        logger.debug(String.format("Adding %s to the bag", bagPath));
        this.validatePath(bagPath, isTagFile);
        // A reserved name used as a directory, e.g. bagit.txt/notes.txt, also prevents the reserved file
        // from being extracted
        String firstSegment = bagPath.split("/", -1)[0];
        if (this.isReservedFileName(firstSegment)) {
            throw new InvalidBagPathException(String.format(
                    "The path %s starts with %s, a file name reserved by the BagIt specification.",
                    bagPath, firstSegment));
        }
        if (this.dataFiles.containsKey(bagPath) || this.tagFiles.containsKey(bagPath)) {
            throw new InvalidBagPathException(
                    String.format("The file with path %s conflicts with another file.", bagPath)
            );
        }
        this.validateNoDirectoryConflict(bagPath);
        if (!manifestAlgorithmName(checksum.getAlgorithm()).equals(manifestAlgorithmName(this.checksumAlgorithm))) {
            throw new SpeedBagException(
                    String.format("The %s checksum algorithm of %s differs from the bag's checksum algorithm %s.",
                            checksum.getAlgorithm(), bagPath, this.checksumAlgorithm)
            );
        }

        String lowerCasePath = bagPath.toLowerCase(Locale.ROOT);
        if (this.isReservedFileName(firstSegment.toLowerCase(Locale.ROOT))) {
            logger.warn(String.format("The path %s starts with %s, which differs only in case from a file name "
                    + "reserved by the BagIt specification. On a case-insensitive file system, extracting the bag "
                    + "can replace the reserved file, and a validator can read this file in its place.",
                    bagPath, firstSegment));
        }
        if (isTagFile && firstSegment.equalsIgnoreCase(PAYLOAD_DIRECTORY)) {
            logger.warn(String.format("The tag file %s differs only in case from a path in the %s/ directory. "
                    + "On a case-insensitive file system, extracting the bag places it in the %s/ directory, "
                    + "where it is a payload file that the payload manifest does not list.",
                    bagPath, PAYLOAD_DIRECTORY, PAYLOAD_DIRECTORY));
        }
        String pathWithOtherCase = this.pathsByLowerCase.get(lowerCasePath);
        if (pathWithOtherCase != null) {
            logger.warn(String.format("The path %s differs only in case from the path %s. On a "
                    + "case-insensitive file system, extracting the bag replaces one file with the other.",
                    bagPath, pathWithOtherCase));
        }

        SpeedFile newFile = new SpeedFile(new SpeedStream(file, checksum), bagPath, isTagFile);
        if (isTagFile) {
            this.tagFiles.put(bagPath, newFile);
        } else {
            this.dataFiles.put(bagPath, newFile);
        }
        this.pathsByLowerCase.put(lowerCasePath, bagPath);
        this.directoryPaths.addAll(parentDirectories(bagPath));
    }

    /**
     * Checks that a path can be written to the bag as a data file or a tag file.
     *
     * @param path The path being checked, relative to the bag root
     * @param isTagFile True when the file is a tag file
     * @throws InvalidBagPathException Thrown when the path is empty or absolute, contains a backslash or
     *                                 an empty, "." or ".." segment, is a data file outside data/, or is
     *                                 a tag file in data/
     */
    private void validatePath(String path, boolean isTagFile) throws InvalidBagPathException {
        if (path == null || path.isEmpty()) {
            throw new InvalidBagPathException("The path of the file is empty.");
        }
        if (path.startsWith("/")) {
            throw new InvalidBagPathException(String.format(
                    "The path %s is absolute. Paths in a bag are relative to the bag root.", path));
        }
        if (path.contains("\\")) {
            throw new InvalidBagPathException(String.format(
                    "The path %s contains a backslash. Paths in a bag use / as the separator.", path));
        }
        // An empty segment also rejects a path that ends with / and so names a directory
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new InvalidBagPathException(String.format(
                        "The path %s contains an empty, . or .. segment.", path));
            }
        }
        boolean isInPayloadDirectory = path.startsWith(PAYLOAD_DIRECTORY + "/");
        if (!isTagFile && !isInPayloadDirectory) {
            throw new InvalidBagPathException(String.format(
                    "The data file %s is outside the %s/ directory.", path, PAYLOAD_DIRECTORY));
        }
        // A tag file named data would be a file with the same path as the payload directory
        if (isTagFile && (isInPayloadDirectory || path.equals(PAYLOAD_DIRECTORY))) {
            throw new InvalidBagPathException(String.format(
                    "The tag file %s is in the %s/ directory, which contains only data files.",
                    path, PAYLOAD_DIRECTORY));
        }
    }

    /**
     * Converts a checksum algorithm name to the form used in manifest file names, as RFC 8493
     * section 2.4 specifies.
     *
     * @param algorithm The checksum algorithm name, e.g. SHA-256
     * @return The name in lower case without non-alphanumeric characters, e.g. sha256
     */
    private static String manifestAlgorithmName(String algorithm) {
        return algorithm.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /**
     * Percent-encodes the percent signs, carriage returns and line feeds in a path, as RFC 8493
     * section 2.1.3 requires for a path in a manifest.
     *
     * @param path The path of a file, relative to the bag root
     * @return The path as it is written in a manifest
     */
    private static String encodeManifestPath(String path) {
        // The percent signs are encoded first, so the % of %0D and %0A is not encoded again
        return path.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A");
    }

    /**
     * Checks whether a name is a file name in the bag root that the BagIt specification reserves.
     *
     * @param name The name being checked
     * @return True when the name is bagit.txt, bag-info.txt, fetch.txt, or starts with manifest- or
     *         tagmanifest-
     */
    private boolean isReservedFileName(String name) {
        return name.equals(this.properties.getProperty("bagit.file.name"))
                || name.equals(this.properties.getProperty("bag.info.file.name"))
                || name.equals(this.properties.getProperty("fetch.file.name"))
                || MANIFEST_FILE_NAME.matcher(name).matches();
    }

    /**
     * Checks that a path is not a directory of an added file, and that no parent directory of the path
     * is an added file. A zip with both cannot be extracted.
     *
     * @param path The path being checked, relative to the bag root
     * @throws InvalidBagPathException Thrown when the path is a directory of an added file, or a parent
     *                                 directory of the path is an added file
     */
    private void validateNoDirectoryConflict(String path) throws InvalidBagPathException {
        if (this.directoryPaths.contains(path)) {
            throw new InvalidBagPathException(String.format(
                    "The path %s is a directory that contains another file in the bag.", path));
        }
        for (String directory : parentDirectories(path)) {
            if (this.dataFiles.containsKey(directory) || this.tagFiles.containsKey(directory)) {
                throw new InvalidBagPathException(String.format(
                        "The path %s is inside %s, which is a file in the bag.", path, directory));
            }
        }
    }

    /**
     * Returns the parent directories of a path.
     *
     * @param path The path of a file, relative to the bag root
     * @return The parent directories, e.g. data and data/nested for data/nested/a.csv
     */
    private static Set<String> parentDirectories(String path) {
        Set<String> directories = new HashSet<>();
        for (int i = path.indexOf('/'); i != -1; i = path.indexOf('/', i + 1)) {
            directories.add(path.substring(0, i));
        }
        return directories;
    }


    /**
     * Adds a stream of data to the bag.
     *
     * @param file:      The stream representing a file or data that will be placed in the bag
     * @param bagPath:      The path, relative to the bag root where the file belongs
     * @param isTagFile: Boolean set to True when the file is a tag file
     * @throws InvalidBagPathException Thrown when the path is reserved by the BagIt specification
     *                                 or was already added
     */
    public void addFile(InputStream file, String bagPath, boolean isTagFile) throws NoSuchAlgorithmException, SpeedBagException {
        logger.debug(String.format("Adding %s to the bag", bagPath));
        MessageDigest newDigest = MessageDigest.getInstance(this.checksumAlgorithm);
        this.addFile(file, bagPath, newDigest, isTagFile);
    }

    /**
     * Generates a bagit.txt file.
     *
     * @return A string representing the bagit.txt file.
     */
    public String generateBagitTxt() {
        logger.debug("Creating the bagit.txt file");
        String bagitFile = "";
        for (Map.Entry<String, String> entry : this.bagitMetadata.entrySet()) {
            if(bagitFile != null) {
                bagitFile = String.format("%s%s: %s\n", bagitFile, entry.getKey(), entry.getValue());
            } else {
                bagitFile = String.format("%s: %s\n", entry.getKey(), entry.getValue());
            }
        }
        String tagFileversion = this.properties.getProperty("tag.file.version");
        String tagFileCharacterEncodingName = this.properties.getProperty("tag.file.character.encoding.name");
        String tagFileCharacterEncodingValue = this.properties.getProperty("tag.file.character.encoding.value");

        bagitFile = String.format("%s%s: %s\n", bagitFile, tagFileversion, version);
        bagitFile = String.format("%s%s: %s\n", bagitFile, tagFileCharacterEncodingName, tagFileCharacterEncodingValue);
        return bagitFile;
    }

    /**
     * Takes a size and returns B, KB, Mb, GB, etc. Taken from
     * https://stackoverflow.com/questions/3758606/how-can-i-convert-byte-size-into-a-human-readable-format-in-java
     *
     * @param size: The size being converted
     * @return The size as 5 B, 5 KB, 5 GB, etc
     */
    public static String formatSize(long size) {
        if (size < 1024) return size + " B";
        int z = (63 - Long.numberOfLeadingZeros(size)) / 10;
        return String.format("%.1f %sB", (double)size / (1L << (z*10)), " KMGTPE".charAt(z));
    }

    /**
     *  Generates the bag-info.txt file contents.
     *
     * @param payloadOxum The payload oxum of the bag
     * @param bagSize: The size of the bag
     * @return A text string with the file contents
     */
    public String generateBagInfoTxt(String payloadOxum, long bagSize) {
        logger.debug("Generating bag-info.txt");
        LocalDateTime dateTime = LocalDateTime.now();
        DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH);
        String bagInfoDateKey = this.properties.getProperty("bag.info.date");
        String bagInfo = String.format("%s: %s\n", bagInfoDateKey, dateFormat.format(dateTime));
        String bagInfoPayloadOxum = this.properties.getProperty("bag.info.payloadOxum");
        bagInfo = String.format("%s%s: %s\n", bagInfo, bagInfoPayloadOxum, payloadOxum);
        String bagInfoBagSize = this.properties.getProperty("bag.info.bagSize");
        bagInfo = String.format("%s%s: %s\n", bagInfo, bagInfoBagSize, formatSize(bagSize));
        return bagInfo;
    }

    /**
     * Writes a line to the tag manifest file. The line conforms to the
     * <path> <checksum> format specified by BagIt.
     * The path is percent-encoded.
     *
     * @param path:     The path the the file, relative to the bag root
     * @param checksum: The checksum of the file
     */
    public void writeToTagManifest(String path, String checksum) {
        logger.debug(String.format("Writing line to the tag-manifest %s %s", path, checksum));
        tagManifestFile.put(checksum, encodeManifestPath(path));
    }

    /**
     * Writes a line to the manifest file describing the data files. The line conforms to the
     * "<path> <checksum>" format specified by BagIt.
     * The path is percent-encoded.
     *
     * @param path:     The path the the file, relative to the bag root
     * @param checksum: The checksum of the file
     */
    public void writeToDataManifest(String path, String checksum) {
        logger.debug(String.format("Writing line to the data manifest %s %s", path, checksum));
        dataManifestFile.put(checksum, encodeManifestPath(path));
    }

    /**
     * Streams an individual file
     *
     * @param zos The output stream that the file is being written to
     * @param streamingFile The file stream that's being written to the output stream
     * @throws IOException
     */
    private void streamFile(ZipOutputStream zos, SpeedFile streamingFile) throws IOException {
        try {
            ZipEntry entry = new ZipEntry(streamingFile.getPath());
            zos.putNextEntry(entry);

            SpeedStream fileStream = streamingFile.getStream();
            IOUtils.copy(fileStream, zos);
        } finally {
            zos.closeEntry();
        }
    }

    /**
     * Writes a tag file that SpeedBagIt generates to the zip and adds its checksum to the tag manifest.
     *
     * @param zos The output stream that the file is written to
     * @param path The path of the tag file, relative to the bag root
     * @param contents The contents of the tag file
     * @throws IOException Thrown when the file cannot be written to the output stream
     * @throws NoSuchAlgorithmException Thrown when an unsupported checksum algorithm is used
     */
    private void writeGeneratedTagFile(ZipOutputStream zos, String path, String contents)
            throws IOException, NoSuchAlgorithmException {
        InputStream contentStream = new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8));
        SpeedFile generatedFile = new SpeedFile(new SpeedStream(contentStream,
                MessageDigest.getInstance(checksumAlgorithm)), path, true);
        try {
            streamFile(zos, generatedFile);
            writeToTagManifest(path, generatedFile.getStream().getChecksum());
        } finally {
            generatedFile.getStream().close();
        }
    }

    /**
     * Writes the files to a stream under the BagIt specification. The manifest, bagit.txt,
     * and bag.info are generated inside. A bag can be streamed only once.
     *
     * @throws IOException Throws when something went wrong with streaming the bag
     * @throws NoSuchAlgorithmException Thrown when an unsupported checksum algorithm is used
     * @throws IllegalStateException Thrown when the bag was already streamed
     */
    public InputStream stream()
            throws IOException, NoSuchAlgorithmException {
        if (this.isStreamed) {
            throw new IllegalStateException("The bag was already streamed.");
        }
        this.isStreamed = true;
        PipedOutputStream ps = new PipedOutputStream();
        PipedInputStream is = new PipedInputStream(ps);
        ZipOutputStream zos = new ZipOutputStream(ps);
        AtomicReference<Exception> failure = new AtomicReference<>();

        executor.execute(
            new Runnable() {
                public void run() {
                    boolean isWritten = false;
                    try {
                        String timeStamp = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
                        logger.info(String.format("Streaming bag at %s", timeStamp));
                        long totalSize = 0;
                        // Stream all the files in the root 'data' directory

                        for (SpeedFile streamingFile : dataFiles.values()) {
                            try {
                                streamFile(zos, streamingFile);
                                String checksum = new String(streamingFile.getStream().getChecksum());
                                writeToDataManifest(streamingFile.getPath(), checksum);
                                totalSize += streamingFile.getStream().getSize();
                            } finally {
                                streamingFile.getStream().close();
                            }
                        }
                        // The tag files are written before the generated files. When a tag file name
                        // differs only in case from a generated file name, extraction on a case-insensitive
                        // file system writes the generated file last, so the generated contents are kept.
                        for (SpeedFile streamingFile : tagFiles.values()) {
                            try {
                                streamFile(zos, streamingFile);
                                String checksum = streamingFile.getStream().getChecksum();
                                writeToTagManifest(streamingFile.getPath(), checksum);
                            } finally {
                                streamingFile.getStream().close();
                            }
                        }

                        String payloadOxum =  String.format("%s.%s",totalSize, dataFiles.size());
                        writeGeneratedTagFile(zos, properties.getProperty("bagit.file.name"), generateBagitTxt());
                        writeGeneratedTagFile(zos, properties.getProperty("bag.info.file.name"),
                                generateBagInfoTxt(payloadOxum, totalSize));

                        String sanitizedChecksum = manifestAlgorithmName(checksumAlgorithm);
                        String fileName = String.format("manifest-%s.txt", sanitizedChecksum);
                        writeGeneratedTagFile(zos, fileName, bagFileToString(dataManifestFile));

                        // Create the tag manifest and stream it
                        String tagMannifest = bagFileToString(tagManifestFile);
                        InputStream fileStream = new ByteArrayInputStream(tagMannifest.getBytes(StandardCharsets.UTF_8));
                        fileName = String.format("tagmanifest-%s.txt", sanitizedChecksum);
                        SpeedFile tagManifestStreamFile = new SpeedFile(new SpeedStream(fileStream,
                                MessageDigest.getInstance(checksumAlgorithm)), fileName, true);
                        try {
                            streamFile(zos, tagManifestStreamFile);
                        } finally {
                            tagManifestStreamFile.getStream().close();
                        }
                        zos.close();
                        isWritten = true;
                        timeStamp = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
                        logger.info(String.format("Finished streaming bag at %s", timeStamp));

                    } catch (Exception e) {
                        logger.error("Error streaming bag", e);
                        failure.set(e);
                    } finally {
                        if (!isWritten) {
                            try {
                                closeFileStreams(dataFiles.values());
                                closeFileStreams(tagFiles.values());
                            } finally {
                                // Only the pipe is closed. Closing zos would write the zip's central directory,
                                // and the reader would receive a well-formed zip that is missing files.
                                try { ps.close(); } catch (IOException ignored) {}
                            }
                        }
                    }
                }
            });

        return new FilterInputStream(is) {
            @Override
            public int read() throws IOException {
                int b = super.read();
                if (b == -1) checkFailure();
                return b;
            }

            @Override
            public int read(byte[] buf, int off, int len) throws IOException {
                int n = super.read(buf, off, len);
                if (n == -1) checkFailure();
                return n;
            }

            private void checkFailure() throws IOException {
                Exception ex = failure.get();
                if (ex != null) {
                    throw new IOException("Bag streaming failed", ex);
                }
            }
        };
    }

    /**
     * Closes the input stream of each file.
     *
     * @param files The files whose input streams are closed
     */
    private static void closeFileStreams(Collection<SpeedFile> files) {
        for (SpeedFile file : files) {
            try {
                file.getStream().close();
            } catch (Exception e) {
                // An unchecked exception from one stream would leave the remaining streams open
                logger.warn(String.format("The input stream of %s could not be closed.", file.getPath()), e);
            }
        }
    }

    /**
     * Returns the number of data files in the bag.
     *
     * @return The number of data files
     */
    public int getPayloadFileCount() {
        return this.dataFiles.size();
    }
    /**
     * Returns all the tag files that have been added to
     * the bag.
     * @return HashMap of tag files
     */
    public HashMap<String, SpeedFile> getTagFiles() {
        return this.tagFiles;
    }

    /**
     * Returns a list of the data files that have been added
     * to the bag. These are the files that belong under data/
     * @return List of data files
     */
    public HashMap<String, SpeedFile> getDataFiles() {
        return this.dataFiles;
    }

    /**
     * Converts a manifest map to the contents of a manifest file, with each line ending in a line feed.
     *
     * @param mapFile The manifest entries, keyed by checksum
     * @return The manifest file contents
     */
    public static String bagFileToString(Map<String, String> mapFile) {
        StringBuilder builder = new StringBuilder();
        for(Map.Entry<String, String> e : mapFile.entrySet())
        {
            String key = e.getKey();
            String value = e.getValue();
            builder.append(key);
            builder.append(' ');
            builder.append(value);
            builder.append('\n');
        }
        return builder.toString();
    }
}

