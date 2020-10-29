package org.example;

// https://projectelectron.rockarch.org/rac-bagit-spec/
// https://wikispaces.psu.edu/download/attachments/231670283/MetaArchiveBagItUsageInstructions.pdf?version=1&modificationDate=1410969194000&api=v2

import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.Calendar;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.logging.Logger;
import java.io.InputStream;
import java.io.IOException;
import java.io.ByteArrayInputStream;
/**
 *
 * DEVNOTE: I only have this working for CRC32 checksums?
 */
public class SpeedBag {
    private final static Logger logger = Logger.getLogger("SpeedyBag");

    public int bagSize;
    // Version the bag is (0.97, 1.0, etc)
    public double version;
    // Contents of tagmanifest-{algo}.txt
    public String tagManifestFile;
    // Contents of manifest-{algo}.txt
    public String dataManifestFile;
    // Contents of bag-info.txt
    public String bagInfoFile;
    // Contents of bagit.txt
    public String bagitFile;
    // The name of the algorithm. Should be compatible with org.dataone.service.types.v1.Checksum
    public String checksumAlgorithm;
    public int payloadOxum;

    // A list holding all of the files in the bag
    private List<SpeedFile> dataFiles;
    private List<SpeedFile> tagFiles;


    /**
     * @param version:           The bag version (0.97, 1.0, etc)
     * @param checksumAlgorithm: The name of the algorithm used to checksum the files
     * @param bagitMetadata:     An optional key-value mapping of metadata that belongs in bagit.txt.
     */
    public SpeedBag(double version,
                    String checksumAlgorithm,
                    Map<String, String> bagitMetadata) throws SpeedBagException, NoSuchAlgorithmException {
        //logger.info("Creating SpeedyBag instance");
        this.version = version;
        this.payloadOxum = 0;
        this.checksumAlgorithm = checksumAlgorithm;
        this.dataFiles = new ArrayList<>();
        this.tagFiles = new ArrayList<>();

        // Create the bagit.txt file
        this.generateBagitTxt(bagitMetadata);
        // Add a stream to bagit.txt and its full path to the bag
        InputStream fileStream = new ByteArrayInputStream(this.bagitFile.getBytes(StandardCharsets.UTF_8));
        this.addFile(fileStream, "bagit.txt", MessageDigest.getInstance(checksumAlgorithm), true);
        this.bagInfoFile = String.format("Bagging-Date: %s\n", Calendar.getInstance().getTime().toString());
    }


    /**
     * Adds a stream of data to the bag.
     *
     * @param file:      The stream representing a file or data that will be placed in the bag
     * @param bagPath:      The path, relative to the bag root where the file belongs
     * @param checksum:
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
     * Generates a bagit.txt file.
     *
     * @param bagitMetadata: A map of key-values that will be placed in the file as key: value
     */
    public void generateBagitTxt(Map<String, String> bagitMetadata) {
        for (Map.Entry<String, String> entry : bagitMetadata.entrySet()) {
            //logger.info(String.format("Adding %s %s to the bagit.txt file", entry.getKey(), entry.getValue()));
            if(this.bagitFile != null) {
                this.bagitFile = String.format("%s%s: %s\n", this.bagitFile, entry.getKey(), entry.getValue());
            } else {
                this.bagitFile = String.format("%s: %s\n", entry.getKey(), entry.getValue());
            }
        }
        this.bagitFile = String.format("%sBagIt-Version: %s\n", this.bagitFile, version);
        this.bagitFile = String.format("%sTag-File-Character-Encoding: UTF-8\n", this.bagitFile);
    }


    /**
     * Writes a line to the tag manifest file
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
     * Writes a line to the manifest file describing the data files
     *
     * @param path:     The path the the file, relative to the bag root
     * @param checksum: The checksum of the file
     */
    public void writeToDataManifest(String path, String checksum) {
        if(this.dataManifestFile != null) {
            this.dataManifestFile += String.format("%s %s\n", path, checksum);
        } else
        {
            this.dataManifestFile = String.format("%s %s\n", path, checksum);
        }
    }


    /**
     * @param zos: The output stream that represents the streaming bag
     * @throws IOException
     */
    public void stream(ZipOutputStream zos)
            throws IOException, NoSuchAlgorithmException {

        logger.info("stream called!");
        for (SpeedFile streamingFile : this.dataFiles) {
            logger.info(String.format("Preparing to write %s", streamingFile.getPath()));
            ZipEntry entry = new ZipEntry(streamingFile.getPath());
            zos.putNextEntry(entry);

            SpeedStream fileStream = streamingFile.getStream();
            fileStream.transferTo(zos);

            String checksum = new String(streamingFile.getStream().getChecksum());
            logger.info(checksum);
            logger.info(String.valueOf(streamingFile.getStream().getSize()));
            this.writeToDataManifest(streamingFile.getPath(), checksum);
            this.payloadOxum += streamingFile.getStream().getSize();
            zos.closeEntry();
        }

        // Now that payload-oxum is computed, add it to the bag-info.txt file
        this.bagInfoFile = String.format("%spayload-oxum: %s\n", this.bagInfoFile, payloadOxum);
        InputStream fileStream = new ByteArrayInputStream(this.bagInfoFile.getBytes(StandardCharsets.UTF_8));
        this.addFile(fileStream, "bag-info.txt", MessageDigest.getInstance("MD5"), true);
        String fileName = String.format("manifest-%s.txt", this.checksumAlgorithm);
        fileStream = new ByteArrayInputStream(this.dataManifestFile.getBytes(StandardCharsets.UTF_8));
        this.addFile(fileStream, fileName, MessageDigest.getInstance("MD5"), true);

        // Write all of the tag files
        for (SpeedFile streamingFile : this.tagFiles) {
            ZipEntry entry = new ZipEntry(streamingFile.getPath());
            zos.putNextEntry(entry);

            fileStream = streamingFile.getStream();
            fileStream.transferTo(zos);

            String checksum = new String(streamingFile.getStream().getChecksum());
            this.writeToTagManifest(streamingFile.getPath(), checksum);
            this.payloadOxum += streamingFile.getStream().getSize();
            zos.closeEntry();
        }

        // Write the tag-manifest
        fileStream = new ByteArrayInputStream(this.tagManifestFile.getBytes(StandardCharsets.UTF_8));
        //this.addFile(fileStream, "tagmanifest-md5.txt", MessageDigest.getInstance("MD5"), true);
        fileName = String.format("tagmanifest-%s.txt", this.checksumAlgorithm);
        SpeedFile newFile = new SpeedFile(new SpeedStream(fileStream,  MessageDigest.getInstance(this.checksumAlgorithm)), fileName, true);

        ZipEntry entry = new ZipEntry(newFile.getPath());
        zos.putNextEntry(entry);
        fileStream = newFile.getStream();
        fileStream.transferTo(zos);
        zos.closeEntry();

        zos.close();
    }


    public String checksum(String content) throws SpeedBagException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(this.checksumAlgorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new SpeedBagException("Failed to get an instance of the checksummer. Please ensure that" +
                    "the checksum supplied is listed in the Java Security Standard Algorithm Names", e);
        }
        byte[] hash;
        // Go ahead and checksum the file
        hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
        if (hash != null) {
            return new String(hash, StandardCharsets.UTF_8);
        }
        return null;
    }

}
