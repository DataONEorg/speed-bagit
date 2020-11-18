package org.dataone.speedbagit;

public class SpeedFile {
    private String bagPath;
    private SpeedStream stream;
    private boolean isTagFile;


    public SpeedFile(SpeedStream stream, String bagPath, boolean isTagFile) {
        this.stream = stream;
        this.bagPath = bagPath;
        this.isTagFile = isTagFile;
    }

    public String getPath() {
        return this.bagPath;
    }

    public SpeedStream getStream() {
        return this.stream;
    }

    public boolean isTagFile() {
        return this.isTagFile;
    }
}
