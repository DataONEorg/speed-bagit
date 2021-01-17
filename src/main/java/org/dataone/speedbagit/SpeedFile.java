package org.dataone.speedbagit;

/**
 * The SpeedFile class represents a file that will be placed in a BagIt archive. It holds
 * a stream to the data that's written to the file in the archive and the location of the file.
 */
public class SpeedFile {
    private String bagPath;
    private SpeedStream stream;
    private boolean isTagFile;

    /**
     * Constructs a new SpeedFile instance.
     *
     * @param stream A SpeedStream representing the bytes of the file
     * @param bagPath The path, relative to the bag root that the file is written to
     * @param isTagFile A boolean that represents whether the file is a BagIt Tag file
     */
    public SpeedFile(SpeedStream stream, String bagPath, boolean isTagFile) {
        this.stream = stream;
        this.bagPath = bagPath;
        this.isTagFile = isTagFile;
    }

    /**
     * Gets the path, relative to the bag root that the file
     * is written to
     * @return The path of the file
     */
    public String getPath() {
        return this.bagPath;
    }

    /**
     * Gets the underlying stream to the file
     *
     * @return The stream of the file's bytes
     */

    public SpeedStream getStream() {
        return this.stream;
    }

    /**
     * Gets the flag representing the Tag file status
     *
     * @return Whether or not the file is a BagIt Tag file
     */
    public boolean isTagFile() {
        return this.isTagFile;
    }
}
