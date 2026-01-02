# About

This is a fork of a repository: https://github.com/soiaf/Java-Apple-Lossless-decoder

What changed:

1. Heavily refactored code (made it more Java like, not C/C++ like)
2. Added few tests
3. Added facade `AlacDecoder` for easy usage
4. Added publication to Maven Central

# Usage

https://central.sonatype.com/artifact/com.github.curious-odd-man/alac2wav/versions

Maven:
```
<dependency>
    <groupId>com.github.curious-odd-man</groupId>
    <artifactId>alac2wav</artifactId>
    <version>1.0</version>
</dependency>

```

Gradle:
```
implementation("com.github.curious-odd-man:alac2wav:1.0")
```

## See sample code
[DecoderDemo.java](src/main/java/DecoderDemo.java)

or 

```java
public static void main(String[] args) {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    AlacDecoder
            .decode(WavFormat.RAW_PCM)
            .fromFile(Path.of("<<path to ALAC file>>"))
            // OR .fromStream(() -> new ByteArrayInputStream())
            .toStream(byteArrayOutputStream)
            // OR .toFile(Path.of(<<<path to WAV file>>>))
            .execute();
}
```

# Original repo description

```
////////////////////////////////////////////////////////////////////////////
// Java Implementation of Apple Lossless Decoder //
// Copyright (c) 2011-2014 Peter McQuillan //
// All Rights Reserved. //
// Distributed under the BSD Software License (see license.txt)      //
////////////////////////////////////////////////////////////////////////////

This package contains a Java implementation of an Apple Lossless decoder.
It is ported from v0.2.0 of the Apple Lossless decoder written by David Hammerton.
This code supports both 16-bit and 24-bit Apple Lossless files.

It is packaged with a demo command-line program that accepts a
Apple Lossless audio file as input and output a RIFF wav file.

The Java source code files can be compiled to class files very simply by going
to the directory where you have downloaded the .java files and running

javac *.java

To run the demo program, use the following command

java DecoderDemo <input.m4a> <output.wav>

where input.m4a is the name of the Apple Lossless file you wish to decode to a WAV file.

This code is ported from v0.2.0 of the Apple Lossless decoder written by David Hammerton.
However there are also some extra changes, for example:

* The original code to read the hdlr atom was capable of generating a minus value seek
  after reading strlen - this causes problems if there is poor or non-existent seeking
  support
* The stream handling code is now written so that it keeps track of where in the input
  stream it is - this is needed for handling the case where the mdat atom comes before the
  moov atom (and you have poor/non-existent seeking support)
* The stsz atom handling code assumed variable sample sizes, it now also handles fixed
  sample sizes.

Thanks to Denis Tulskiy for the contributions he made to the code.

Please direct any questions or comments to beatofthedrum@gmail.com
```