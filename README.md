# SpeedBagIt
[![Build Status](https://travis-ci.org/NCEAS/eml.svg?branch=master)](https://travis-ci.org/NCEAS/eml)

A fast, minimal BagIt library that serves bags without touching the filesystem. Ever.

### Installing
To use SpeedBagIt in your project, add the following to your `pom.xml`.

```
<dependency>
    <groupId>org.dataone</groupId>
    <artifactId>SpeedBagIt</artifactId>
    <version>1.0</version>
</dependency>
```

### Usage

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
