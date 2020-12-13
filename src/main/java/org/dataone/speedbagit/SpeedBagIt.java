package org.dataone.speedbagit;


import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.logging.Logger;
import java.io.InputStream;
import java.io.IOException;
import java.io.ByteArrayInputStream;

/**
 *  The main interface for creating a BagIt compliant zip file. The SpeedBagIt class
 *  manages SpeedFile objects, which represent files that will be written to the output
 *  stream.
 *  This class is also responsible for generating the tagmangifest and manfiest files along
 *  with the default BagIt files: bagit.txt & bag-info.txt.
 *
 */
public class SpeedBagIt {
    private final static Logger logger = Logger.getLogger("SpeedBagIt");

    // Version that the bag is (0.97, 1.0, etc)
    public double version;
    // Contents of tagmanifest-{algo}.txt file
    public String tagManifestFile;
    // Contents of manifest-{algo}.txt file
    public String dataManifestFile;
    // The name of the algorithm. Should be compatible with the MessageDigest class
    public String checksumAlgorithm;
    // Map of key-values that go in the bagit.txt file
    public Map<String, String> bagitMetadata;

    // A list holding all of the files in the bag
    private List<SpeedFile> dataFiles;
    private List<SpeedFile> tagFiles;

    /**
     * Creates a new instance of a SpeedBagIt. This constructor supports adding
     * additional metadata to the bagit.txt file, when created.
     * @param version:           The bag version (0.97, 1.0, etc)
     * @param checksumAlgorithm: The name of the algorithm used to checksum the files
     * @param bagitMetadata:     A key-value mapping of metadata that belongs in bagit.txt
     */
    public SpeedBagIt(double version,
                    String checksumAlgorithm,
                    Map<String, String> bagitMetadata) {
        this.version = version;
        this.checksumAlgorithm = checksumAlgorithm;
        this.dataFiles = new ArrayList<>();
        this.tagFiles = new ArrayList<>();
        this.bagitMetadata = bagitMetadata;
        this.dataManifestFile="";
        this.tagManifestFile="";
    }

    /**
     * Creates a new SpeedBagIt object. This constructor requires the bare
     * minimum arguments to make a valid bag.
     * @param version:           The bag version (0.97, 1.0, etc)
     * @param checksumAlgorithm: The name of the algorithm used to checksum the files
     */
    public SpeedBagIt(double version,
                      String checksumAlgorithm) {
        this.version = version;
        this.checksumAlgorithm = checksumAlgorithm;
        this.dataFiles = new ArrayList<>();
        this.tagFiles = new ArrayList<>();
        this.bagitMetadata = new HashMap<> ();
        this.dataManifestFile="";
        this.tagManifestFile="";
    }

    /**
     * Adds a stream of data to the bag.
     *
     * @param file: The stream representing a file or data that will be placed in the bag
     * @param bagPath: The path, relative to the bag root where the file belongs
     * @param checksum: A MessageDigest object of the desired file checksum
     * @param isTagFile: Boolean set to True when the file is a tag file
     */
    public void addFile(InputStream file, String bagPath, MessageDigest checksum, boolean isTagFile) {
        SpeedFile newFile = new SpeedFile(new SpeedStream(file, checksum), bagPath, isTagFile);

        if (isTagFile) {
            this.tagFiles.add(newFile);
        } else {
            this.dataFiles.add(newFile);
        }
    }

    /**
     * Generates a valid bagit.txt file. Additional parameters
     * can be added to the file through `this.bagitMetadata`.
     *
     * @return A string representing the bagit.txt file.
     */
    public String generateBagitTxt() {
        String bagitFile = "";
        for (Map.Entry<String, String> entry : this.bagitMetadata.entrySet()) {
            if(bagitFile != null) {
                bagitFile = String.format("%s%s: %s\n", bagitFile, entry.getKey(), entry.getValue());
            } else {
                bagitFile = String.format("%s: %s\n", entry.getKey(), entry.getValue());
            }
        }
        bagitFile = String.format("%sBagIt-Version: %s\n", bagitFile, version);
        bagitFile = String.format("%sTag-File-Character-Encoding: UTF-8\n", bagitFile);
        return bagitFile;
    }

    /**
     * Takes a size and returns B, KB, Mb, GB, etc. Taken from
     * https://stackoverflow.com/questions/3758606/how-can-i-convert-byte-size-into-a-human-readable-format-in-java
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

        LocalDateTime dateTime = LocalDateTime.now();
        DateTimeFormatter formmat1 = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH);
        String bagInfo = String.format("Bagging-Date: %s\n", formmat1.format(dateTime));
        bagInfo = String.format("%sPayload-Oxum: %s\n", bagInfo, payloadOxum);
        bagInfo = String.format("%sBag-Size: %s\n", bagInfo, formatSize(bagSize));
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
        // Check to see if it doesn't exist (so we don't write null)
        if(this.tagManifestFile != null) {
            this.tagManifestFile += String.format("%s %s\n", path, checksum);
        } else {
            this.tagManifestFile = String.format("%s %s\n", path, checksum);
        }
    }

    /**
     * Writes a line to the manifest file describing the data files. The line conforms to the
     * "<path> <checksum>" format specified by BagIt.
     *
     * @param path:     The path the the file, relative to the bag root
     * @param checksum: The checksum of the file
     */
    public void writeToDataManifest(String path, String checksum) {
        if(!this.dataManifestFile.equals("")) {
            this.dataManifestFile += String.format("%s %s\n", path, checksum);
        } else
        {
            this.dataManifestFile = String.format("%s %s\n", path, checksum);
        }
    }

    private void streamFile(ZipOutputStream zos, SpeedFile streamingFile) throws IOException {
        try {
            ZipEntry entry = new ZipEntry(streamingFile.getPath());
            zos.putNextEntry(entry);

            SpeedStream fileStream = streamingFile.getStream();
            fileStream.transferTo(zos);
        } finally {
            zos.closeEntry();
        }
    }

    /**
     * Writes the files to a stream under the BagIt specification. The manifest, bagit.txt,
     * and bag.info are generated inside.
     *
     * @param zos: The output stream that represents the streaming bag
     * @throws IOException Throws when something went wrong with streaming the bag
     * @throws NoSuchAlgorithmException Thrown when an unsupported checksum algorithm is used
     */
    public void stream(ZipOutputStream zos)
            throws IOException, NoSuchAlgorithmException {
        int totalSize = 0;
        // Stream all of the files in the root 'data' directory
        for (SpeedFile streamingFile : this.dataFiles) {
            this.streamFile(zos, streamingFile);

            String checksum = new String(streamingFile.getStream().getChecksum());
            this.writeToDataManifest(streamingFile.getPath(), checksum);
            totalSize += streamingFile.getStream().getSize();
        }
        String payloadOxum =  String.format("%s.%s",totalSize, this.dataFiles.size());
        // Generate and add the bagit.txt file
        InputStream bagTextStream = new ByteArrayInputStream(this.generateBagitTxt().getBytes(StandardCharsets.UTF_8));
        this.addFile(bagTextStream, "bagit.txt", MessageDigest.getInstance(checksumAlgorithm), true);


        // Generate and add the bag-info.txt file
        String bagInfoFile = generateBagInfoTxt(payloadOxum, totalSize);
        InputStream fileStream = new ByteArrayInputStream(bagInfoFile.getBytes(StandardCharsets.UTF_8));
        this.addFile(fileStream, "bag-info.txt", MessageDigest.getInstance(checksumAlgorithm), true);

        // Generate and add the data manifest file
        String fileName = String.format("manifest-%s.txt", this.checksumAlgorithm);
        fileStream = new ByteArrayInputStream(this.dataManifestFile.getBytes(StandardCharsets.UTF_8));
        this.addFile(fileStream, fileName, MessageDigest.getInstance(checksumAlgorithm), true);

        // Write all of the tag files
        for (SpeedFile streamingFile : this.tagFiles) {
            this.streamFile(zos, streamingFile);
            String checksum = new String(streamingFile.getStream().getChecksum());
            this.writeToTagManifest(streamingFile.getPath(), checksum);
        }

        // Create the SpeedFile and stream it
        fileStream = new ByteArrayInputStream(this.tagManifestFile.getBytes(StandardCharsets.UTF_8));
        fileName = String.format("tagmanifest-%s.txt", this.checksumAlgorithm);
        SpeedFile tagManifestStreamFile = new SpeedFile(new SpeedStream(fileStream,
                MessageDigest.getInstance(this.checksumAlgorithm)), fileName, true);
        this.streamFile(zos, tagManifestStreamFile);

        zos.close();
    }

    /**
     * Returns the number of data files in the bag
     *
     * @return The number of data files
     */
    public int getPayloadFileCount() {
        return this.dataFiles.size();
    }

}
