# SpeedBagIt
[![Build Status](https://github.com/DataONEorg/speed-bagit/actions/workflows/java-build-and-test.yaml/badge.svg)](https://github.com/DataONEorg/speed-bagit/actions/workflows/java-build-and-test.yaml)

- **Authors**: Thomas Thelen, Jing Tao ([NCEAS](https://www.nceas.ucsb.edu)), Matthew B. Jones ([NCEAS](https://www.nceas.ucsb.edu))

- **License**: [Apache 2](https://opensource.org/licenses/Apache-2.0)
- [**Submit Bugs and feature requests**](https://github.com/DataONEorg/speed-bagit/issues)

A fast, minimal embedded BagIt library that serves bags without touching the filesystem. SpeedBagIt was built for systems that create and deliver BagIt compliant archives at scale. 

## Using

SpeedBagIt can be embedded in applications as a Maven dependency or with the jar in the release bundle.

Add the DataONE Maven repository and SpeedBagIt library to `pom.xml`.
```xml
<repository>
    <id>dataone.org</id>
    <url>https://maven.dataone.org/</url>
    <releases>
        <enabled>true</enabled>
    </releases>
    <snapshots>
        <enabled>true</enabled>
    </snapshots>
</repository>
```

```xml
<dependency>
    <groupId>org.dataone</groupId>
    <artifactId>speedbagit</artifactId>
    <version>1.0.4</version>
</dependency>
```

The example below adds a payload file and a tag file, and copies the bag's zip stream to an output stream.
```java
SpeedBagIt bag = new SpeedBagIt(1.0, "SHA-256");
bag.addFile(new FileInputStream("results.csv"), "data/results.csv", false);
bag.addFile(new FileInputStream("methods.txt"), "metadata/methods.txt", true);
InputStream bagStream = bag.stream();
// Copy the stream to a response object, etc
bagStream.close();
```


## Acknowledgments
Work on this package was supported by:

- NSF DIBBS grant [#1541450](https://www.nsf.gov/awardsearch/showAward?AWD_ID=1541450) to B. Ludaescher, V. Stodden, N. Gaffney, M. Turk, and K. Chard

Additional support was provided for working group collaboration by the National Center for Ecological Analysis and Synthesis, a Center funded by the University of California, Santa Barbara, and the State of California.

[![DataONE_footer](https://user-images.githubusercontent.com/6643222/162324180-b5cf0f5f-ae7a-4ca6-87c3-9733a2590634.png)](https://dataone.org)

[![nceas_footer](https://www.nceas.ucsb.edu/sites/default/files/2020-03/NCEAS-full%20logo-4C.png)](https://www.nceas.ucsb.edu)
