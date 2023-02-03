# Cloudproof Java Library

![workflow](https://github.com/Cosmian/cloudproof_java/actions/workflows/maven.yml/badge.svg)

The Cloudproof Java library provides a Java-friendly API to [Cosmian's Cloudproof Encryption](https://docs.cosmian.com/cloudproof_encryption/use_cases_benefits/).

Cloudproof Encryption secures data repositories and applications in the cloud with advanced application-level encryption and encrypted search.

<!-- toc -->

- [Licensing](#licensing)
- [Getting started](#getting-started)
- [Benchmarks](#benchmarks)
- [Versions Correspondence](#versions-correspondence)
- [Using in Java projects](#using-in-java-projects)
  - [Download required native libraries](#download-required-native-libraries)
  - [Building the native libraries on your own](#building-the-native-libraries-on-your-own)

<!-- tocstop -->

## Licensing

The library is available under a dual licensing scheme Affero GPL/v3 and commercial. See [LICENSE.md](LICENSE.md) for details.

## Cryptographic primitives

The library is based on:

- [CoverCrypt](https://github.com/Cosmian/cover_crypt) algorithm which allows
creating ciphertexts for a set of attributes and issuing user keys with access
policies over these attributes. `CoverCrypt` offers Post-Quantum resistance.

- [Findex](https://github.com/Cosmian/findex) which is a cryptographic protocol designed to securely make search queries on
an untrusted cloud server. Thanks to its encrypted indexes, large databases can
securely be outsourced without compromising usability.

## Getting started

Please [check the online documentation](https://docs.cosmian.com/cloudproof_encryption/use_cases_benefits/) for details on using the CloudProof APIs

In addition, please have a look at the following tests for implementation examples:

- [TestCoverCrypt](./src/test/java/com/cosmian/TestKmsCoverCrypt.java) for using the CoverCrypt scheme with Cosmian KMS
- [TestFfiCoverCrypt](./src/test/java/com/cosmian/TestNativeCoverCrypt.java) for using the CoverCrypt scheme with the local native library
- [TestKmip](./src/test/java/com/cosmian/TestKmip.java) for using the KMIP 2.1 interface with the Cosmian KMS
- [TestSqliteFindex](./src/test/java/com/cosmian/findex/TestSqlite.java) for using the Encrypted Search Findex scheme using Sqlite (or other SQL DBs) as a backend
- [TestRedisFindex](./src/test/java/com/cosmian/findex/TestRedis.java) for using the Encrypted Search Findex scheme using Redis as a backend

## Benchmarks

The following benchmarks are obtained using an Intel(R) Xeon(R) Platinum 8171M CPU @ 2.60GHz.

```c
-----------------------------------------------------
 Benches CoverCrypt Encryption/Decryption With Cache
-----------------------------------------------------

Classic encryption
==================

Number of partitions: 1: Encrypted Header size: 131. Encryption average time: 262971ns (262µs). Decryption average time: 221256ns (221µs)
Number of partitions: 2: Encrypted Header size: 164. Encryption average time: 347328ns (347µs). Decryption average time: 257798ns (257µs)
Number of partitions: 3: Encrypted Header size: 197. Encryption average time: 431084ns (431µs). Decryption average time: 411985ns (411µs)
Number of partitions: 4: Encrypted Header size: 230. Encryption average time: 513750ns (513µs). Decryption average time: 473802ns (473µs)
Number of partitions: 5: Encrypted Header size: 263. Encryption average time: 599409ns (599µs). Decryption average time: 536418ns (536µs)

Hybridized encryption
======================

Number of partitions: 1: Encrypted Header size: 1187. Encryption average time: 320732ns (320µs). Decryption average time: 232779ns (232µs)
Number of partitions: 2: Encrypted Header size: 2276. Encryption average time: 467049ns (467µs). Decryption average time: 273931ns (273µs)
Number of partitions: 3: Encrypted Header size: 3365. Encryption average time: 611346ns (611µs). Decryption average time: 315654ns (315µs)
Number of partitions: 4: Encrypted Header size: 4454. Encryption average time: 754446ns (754µs). Decryption average time: 358415ns (358µs)
Number of partitions: 5: Encrypted Header size: 5543. Encryption average time: 900514ns (900µs). Decryption average time: 400720ns (400µs)
```

The version without using the cache adds the deserialization time for the
policy and public key during the encryption process, and the user decryption
key during the decryption process. This time may vary depending on the size of
these objects.

In the case of the above benchmark, deserializing the policy and the public key
takes about 200µs for sizes of 1037 and 18935 bytes respectively (the public
key is half hybridized).

## Versions Correspondence

This library uses the 2 native libraries CoverCrypt and Findex for performance and safe implementation reasons.

This table shows the compatible versions of the various components

| This lib | KMS Server | CoverCrypt | Findex |
|----------|------------|------------|--------|
| 3.0.0    | 4.0.1      | 8.0.1      | 1.0.1  |
| 3.0.2    | 4.0.1      | 8.0.1      | 2.0.0  |
| 3.0.3    | 4.0.1      | 8.0.2      | 2.0.0  |
| 4.0.0    | 4.2.0      | 10.0.0     | 2.0.1  |

## Using in Java projects

This library is open-source software and is available on Maven Central.

```xml
<dependency>
    <groupId>com.cosmian</groupId>
    <artifactId>cloudproof_java</artifactId>
    <version>4.0.0</version>
</dependency>
```

### Download required native libraries

The Cloudproof Java lib uses JNA to access functions of the following native cryptographic libraries:

- `CoverCrypt`
- `Findex`

Those libraries must be found either in the classpath or in subfolders of `src/main/resources/`

- src/main/resources/
  - `linux-x86-64` for Linux 64bit architecture
  - `darwin-x86-64` for MacOS Intel
  - `win32-x86-64` for windows

For tests, it is possible to override these libraries by placing them in the equivalent sub-folders of `src/test/resources`

To download them, please run the following script that will fetch the releases in the public URL [package.cosmian.com](https://package.cosmian.com):

```bash
python3 scripts/get_native_libraries.py
```

Otherwise, to build those libraries manually, please check the CoverCrypt and Findex projects on Github: their `build` directory contains instructions on how to build the native libraries for your system.

### Building the native libraries on your own

For `CoverCrypt`:

```bash
git clone https://github.com/Cosmian/cover_crypt.git
cargo build --release --features ffi
```

For `Findex`:

```bash
git clone https://github.com/Cosmian/findex.git
cargo build --release --features ffi
```

And copy the new binaries from `target/release/<.dylib,.so,.dll>` to `cloudproof_java` FFI directory: check the right platform/architecture directory in [Download required native libraries](#download-required-native-libraries).
