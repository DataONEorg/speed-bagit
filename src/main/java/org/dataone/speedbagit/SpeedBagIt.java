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
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

    // Containers for keeping track of tag & data files, keyed off of
    // their relative file path
    private HashMap<String, SpeedFile> dataFiles;
    private HashMap<String, SpeedFile> tagFiles;
    
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
     */
    public void addFile(InputStream file, String bagPath, MessageDigest checksum, boolean isTagFile)
            throws SpeedBagException {
        logger.debug(String.format("Adding %s to the bag", bagPath));
        // Check to see if there's a path conflict
        if (this.hasPathCollisions(bagPath, isTagFile)) {
            throw new SpeedBagException(
                    String.format("The tag file with path %s conflicts with another file.", bagPath)
            );
        }
        SpeedFile newFile = new SpeedFile(new SpeedStream(file, checksum), bagPath, isTagFile);
            if (isTagFile) {
                this.tagFiles.put(bagPath, newFile);
            } else {
                this.dataFiles.put(bagPath, newFile);
            }
        }

        /**
         * Checks whether two paths collide, based on their file type (tag vs data file).
         *
         * @param path:  Path being checked against the previously added files
         * @param isTagFile: A flag whether the file is a data file or not (otherwise it will be a tag)
         */
        private boolean hasPathCollisions(String path, boolean isTagFile) {
            if (isTagFile) {
                return this.tagFiles.containsKey(path);
            } else {
                return this.dataFiles.containsKey(path);
            }
        }


    /**
     * Adds a stream of data to the bag.
     *
     * @param file:      The stream representing a file or data that will be placed in the bag
     * @param bagPath:      The path, relative to the bag root where the file belongs
     * @param isTagFile: Boolean set to True when the file is a tag file
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
    public String generateBagInfoTxt(String payloadOxum, int bagSize) {
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
     *
     * @param path:     The path the the file, relative to the bag root
     * @param checksum: The checksum of the file
     */
    public void writeToTagManifest(String path, String checksum) {
        logger.debug(String.format("Writing line to the tag-manifest %s %s", path, checksum));
        // Check to see if it doesn't exist (so we don't write null)
        tagManifestFile.put(checksum, path);
    }

    /**
     * Writes a line to the manifest file describing the data files. The line conforms to the
     * "<path> <checksum>" format specified by BagIt.
     *
     * @param path:     The path the the file, relative to the bag root
     * @param checksum: The checksum of the file
     */
    public void writeToDataManifest(String path, String checksum) {
        logger.debug(String.format("Writing line to the data manifest %s %s", path, checksum));
        dataManifestFile.put(checksum, path);
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
     * Writes the files to a stream under the BagIt specification. The manifest, bagit.txt,
     * and bag.info are generated inside.
     *
     * @throws IOException Throws when something went wrong with streaming the bag
     * @throws NoSuchAlgorithmException Thrown when an unsupported checksum algorithm is used
     */
    public InputStream stream()
            throws IOException, NoSuchAlgorithmException {
        PipedOutputStream ps = new PipedOutputStream();
        PipedInputStream is = new PipedInputStream(ps);
        ZipOutputStream zos = new ZipOutputStream(ps);

        executor.execute(
            new Runnable() {
                public void run() {
                    try {
                        String timeStamp = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
                        logger.info(String.format("Streaming bag at %s", timeStamp));
                        int totalSize = 0;
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
                        String payloadOxum =  String.format("%s.%s",totalSize, dataFiles.size());
                        // Generate and add the bagit.txt file
                        InputStream bagTextStream = new ByteArrayInputStream(generateBagitTxt().getBytes(StandardCharsets.UTF_8));
                        String bagitFileName = properties.getProperty("bagit.file.name");
                        addFile(bagTextStream, bagitFileName, MessageDigest.getInstance(checksumAlgorithm), true);


                        // Generate and add the bag-info.txt file
                        String bagInfoFile = generateBagInfoTxt(payloadOxum, totalSize);
                        InputStream fileStream = new ByteArrayInputStream(bagInfoFile.getBytes(StandardCharsets.UTF_8));
                        String bagitInfoFileName = properties.getProperty("bag.info.file.name");
                        addFile(fileStream, bagitInfoFileName, MessageDigest.getInstance(checksumAlgorithm), true);

                        // BagIt requires checksum filenames to be lower cased and without dashes
                        String sanitizedChecksum = checksumAlgorithm.toLowerCase();
                        sanitizedChecksum = sanitizedChecksum.replaceAll("[^A-Za-z0-9]", "");
                        // Generate and add the data manifest file
                        String dataManifest = bagFileToString(dataManifestFile);
                        String fileName = String.format("manifest-%s.txt", sanitizedChecksum);
                        fileStream = new ByteArrayInputStream(dataManifest.getBytes(StandardCharsets.UTF_8));
                        addFile(fileStream, fileName, MessageDigest.getInstance(checksumAlgorithm), true);

                        // Write all the tag files
                        for (SpeedFile streamingFile : tagFiles.values()) {
                            try {
                                streamFile(zos, streamingFile);
                                String checksum = streamingFile.getStream().getChecksum();
                                writeToTagManifest(streamingFile.getPath(), checksum);
                            } finally {
                                streamingFile.getStream().close();
                            }
                        }

                        // Create the tag manifest and stream it
                        String tagMannifest = bagFileToString(tagManifestFile);
                        fileStream = new ByteArrayInputStream(tagMannifest.getBytes(StandardCharsets.UTF_8));
                        fileName = String.format("tagmanifest-%s.txt", sanitizedChecksum);
                        SpeedFile tagManifestStreamFile = new SpeedFile(new SpeedStream(fileStream,
                                MessageDigest.getInstance(checksumAlgorithm)), fileName, true);
                        try {
                            streamFile(zos, tagManifestStreamFile);
                        } finally {
                            tagManifestStreamFile.getStream().close();
                        }
                        zos.close();
                        timeStamp = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
                        logger.info(String.format("Finished streaming bag at %s", timeStamp));
                        
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        return is;
    }

    /**
     * Returns the number of data files in the bag
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

    public static String bagFileToString(Map<String, String> mapFile) {
        StringBuilder builder = new StringBuilder();
        for(Map.Entry<String, String> e : mapFile.entrySet())
        {
            String key = e.getKey();
            String value = e.getValue();
            builder.append(key);
            builder.append(' ');
            builder.append(value);
            builder.append(System.getProperty("line.separator"));
        }
        return builder.toString();
    }
}

