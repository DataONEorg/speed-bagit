# SpeedBagIt
[![Build Status](https://travis-ci.com/DataONEorg/speed-bagit.svg?branch=main)](https://travis-ci.com/DataONEorg/speed-bagit)

- **Authors**: Thomas Thelen ([NCEAS](http://www.nceas.ucsb.edu))
- **License**: [Apache 2](http://opensource.org/licenses/Apache-2.0)
- [**Submit Bugs and feature requests**](https://github.com/DataONEorg/speed-bagit/issues)

A fast, minimal BagIt library that serves bags without touching the filesystem. Ever.
This library was designed for servers serving zip files that conform 
to the BagIt specification. This differs from other BagIt libraries in that it doesn't 
create a temporary zip file of the bag on disk. Instead, it performs checksumming and size calculations 
on the fly _as the bag is being served_. This avoids unnecessarily copying data files to disk which 
is a common bottleneck for delivering content.

### Installing

#### Maven
To use SpeedBagIt in your project, first add the DataONE maven repository to your project's `pom.xml` file.
```
<repository>
    <id>dataone.org</id>
    <url>http://maven.dataone.org/</url>
    <releases>
        <enabled>true</enabled>
    </releases>
    <snapshots>
        <enabled>true</enabled>
    </snapshots>
</repository>
```
Then, add the SpeedBagIt dependency to the `pom.xml` file.
```
<dependency>
    <groupId>org.dataone</groupId>
    <artifactId>speedbagit</artifactId>
    <version>1.0.5</version>
</dependency>
```

#### Compiling & Including the Jar
SpeedBagIt can also be installed by cloning this repository and including the 
resulting jar file in your project.

```
git clone https://github.com/DataONEorg/speed-bagit.git
cd speed-bagit
mvn install
```

### Quick Start


#### Customizing bagit.txt

Staying true to the BagIt specification, SpeedBagIt supports user defined key-value pairs in `bagit.txt`.  

Creating the key-value pairs,
```java
Map<String, String> bagMetadata = new HashMap<>();
bagMetadata.put("description", "This bag contains information about polar ice caps.");
bagMetadata.put("Contact-Email", "admin@dataone.org");
bagMetadata.put("External-Identifier", doi:xx.1234);
```

Pass the key-value pairs to the SpeedBagIt constructor.
```java
try {
    bag = new SpeedBagIt(1.0, "MD-5", bagMetadata);
} catch (SpeedBagException | NoSuchAlgorithmException e) {
    ...
}
```

### Contributing

The takeaway for contributing is that feature branches are created off of the `develop` branch and pull requests should be made 
into the `develop` branch rather than `master`. 

For example, the workflow to create a pull request for a feature that adds support for fetch.txt follows

- Create an issue describing your planned changes, or add a comment to an existing relevant issue
- Checkout the `develop` branch, followed by `git checkout -b feature_support_fetch_file`
- Commit your changes to the `feature_support_fetch_file` branch
- Create a pull request from `feature_support_fetch_file` into `develop` and outline the code changes and how to test it
- Once the code is reviewed, our team will merge in your changes and you're done!

#### Code Style
This project conforms to the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html) and new 
contributions should follow suite.

#### Unit Tests
Unit tests should be created for all new classes and if possible, public methods. When writing unit tests, keep in mind that 
the points are 
 - After your contribution is merged, no one accidentally breaks it in the future
 - Early validation of potential bugs in your code
 - Examples for future developers and users to see how the classes and methods can be used

## Acknowledgments
Work on this package was supported by:

- NSF DIBBS grant [#1541450](https://www.nsf.gov/awardsearch/showAward?AWD_ID=1541450) to B. Ludaescher, V. Stodden, N. Gaffney, M. Turk, and K. Chard

Additional support was provided for working group collaboration by the National Center for Ecological Analysis and Synthesis, a Center funded by the University of California, Santa Barbara, and the State of California.

[![nceas_footer](https://www.nceas.ucsb.edu/sites/default/files/2020-03/NCEAS-full%20logo-4C.png)](http://www.nceas.ucsb.edu)

[![dataone_footer](https://www.dataone.org/sites/all/images/DataONE_LOGO.jpg)](http://dataone.org)

