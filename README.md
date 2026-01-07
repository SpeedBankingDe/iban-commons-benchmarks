# IBAN Commons Benchmarks

A performance comparison module using the [**Java Microbenchmark Harness (JMH)**](https://github.com/openjdk/jmh) to evaluate throughput and latency 
of different IBAN validation libraries in the Java ecosystem.

If you feel other libraries should be included, or have suggestions for this suite, please get in touch.

## 🎯 Benchmark Scope

The benchmark evaluates the performance of three different IBAN validation approaches:

| Benchmark | Library                                                                            | Description                                       |
|:----------|:-----------------------------------------------------------------------------------|:--------------------------------------------------|
| `bm1x`    | [`iban-commons` (de.speedbanking)](https://github.com/SpeedBankingDe/iban-commons) | Validation and object creation (using `tryParse`) |
| `bm2x`    | [`iban4j` (org.iban4j)](https://iban4j.org/)                                       | Validation and object creation                    |
| `bm3a`    | [`Apache Commons Validator`](https://commons.apache.org/proper/commons-validator/) | IBAN validation only (boolean result)             |
| `bm4b`    | [`garvelink iban` (nl.garvelink.oss)](https://github.com/barend/java-iban)         | Object creation                                   |

The data generation process in `IbanBenchmarks.java` ensures a realistic mix of **normalized**, **formatted** (with spaces), **valid**, and **invalid** 
random IBAN strings of random countries to prevent optimization by JVMs and libraries alike.

Iban object and exceptions on failure are consumed by JMH blackholes (`org.openjdk.jmh.infra.Blackhole`).

## ⚙️ Requirements

* [Java Development Kit (JDK) **11 or higher**](https://adoptium.net/de/temurin/releases) (iban4j requires Java 11 or above)

* [Apache Maven 3.x](https://maven.apache.org/)

## 🚀 Building and Execution

The project uses the `maven-shade-plugin` to create a single, executable JAR file containing all necessary dependencies, 
including the **JMH runner** `org.openjdk.jmh.Main` itself.

### 1. Build the Executable JAR
Run the following command from the root directory of this module:

```bash
mvn clean package
```

### 2. Run the Benchmarks

Execute the JAR using the Java runtime. The main class is configured to automatically find and run all benchmarks in the IbanBenchmarks class.

```bash
# example command to run the IBAN benchmarks (class IbanBenchmarks)
java -jar target/iban-commons-benchmarks-1.0.0-SNAPSHOT.jar IbanBenchmarks
```

### 3. Customize JMH Options (optional)

You can pass standard JMH options as arguments to the JAR.
For example, to run only the iban-commons benchmarks (bm1x) with more measurement iterations:

```bash
java -jar target/iban-commons-benchmarks-1.0.0-SNAPSHOT.jar bm1.* -i 10 -r 5s
```

### 4. Sample Output

```bash
date -I && mvn -v
```

```bash
2026-01-07
Apache Maven 3.9.10 (5f519b97e944483d878815739f519b2eade0a91d)
Maven home: /opt/maven
Java version: 21.0.7, vendor: Eclipse Adoptium, runtime: /opt/openJDK/jdk-21.0.7+6
Default locale: en_US, platform encoding: UTF-8
OS name: "linux", version: "6.8.0-60-generic", arch: "amd64", family: "unix"
```
--- 

```bash
java -jar target/iban-commons-benchmarks-1.0.0-SNAPSHOT.jar IbanBenchmarks -prof gc
```

```text
# Run complete. Total time: 00:01:47

REMEMBER: The numbers below are just data. [..]

NOTE: Current JVM experimentally supports Compiler Blackholes [..]

Benchmark                                                           Mode  Cnt           Score     Error   Units
IbanBenchmarks.bm1a_IbanCommons_IsValid                            thrpt    5           5.257 ±   0.138   ops/s
IbanBenchmarks.bm1a_IbanCommons_IsValid:gc.alloc.rate              thrpt    5         686.158 ±  18.013  MB/sec
IbanBenchmarks.bm1a_IbanCommons_IsValid:gc.alloc.rate.norm         thrpt    5   136888820.945 ±   5.010    B/op
IbanBenchmarks.bm1a_IbanCommons_IsValid:gc.count                   thrpt    5           6.000            counts
IbanBenchmarks.bm1a_IbanCommons_IsValid:gc.time                    thrpt    5         128.000                ms
IbanBenchmarks.bm1b_IbanCommons_ObjectCreation                     thrpt    5           2.993 ±   0.152   ops/s
IbanBenchmarks.bm1b_IbanCommons_ObjectCreation:gc.alloc.rate       thrpt    5         724.425 ±  36.865  MB/sec
IbanBenchmarks.bm1b_IbanCommons_ObjectCreation:gc.alloc.rate.norm  thrpt    5   253853885.257 ± 347.095    B/op
IbanBenchmarks.bm1b_IbanCommons_ObjectCreation:gc.count            thrpt    5           7.000            counts
IbanBenchmarks.bm1b_IbanCommons_ObjectCreation:gc.time             thrpt    5         147.000                ms
IbanBenchmarks.bm2a_Iban4j_IsValid                                 thrpt    5           0.814 ±   0.050   ops/s
IbanBenchmarks.bm2a_Iban4j_IsValid:gc.alloc.rate                   thrpt    5        1262.685 ±  77.279  MB/sec
IbanBenchmarks.bm2a_Iban4j_IsValid:gc.alloc.rate.norm              thrpt    5  1627346303.200 ±  38.352    B/op
IbanBenchmarks.bm2a_Iban4j_IsValid:gc.count                        thrpt    5          13.000            counts
IbanBenchmarks.bm2a_Iban4j_IsValid:gc.time                         thrpt    5         202.000                ms
IbanBenchmarks.bm2b_Iban4j_ObjectCreation                          thrpt    5           0.754 ±   0.096   ops/s
IbanBenchmarks.bm2b_Iban4j_ObjectCreation:gc.alloc.rate            thrpt    5        1174.963 ± 149.812  MB/sec
IbanBenchmarks.bm2b_Iban4j_ObjectCreation:gc.alloc.rate.norm       thrpt    5  1633744840.000 ±   0.001    B/op
IbanBenchmarks.bm2b_Iban4j_ObjectCreation:gc.count                 thrpt    5          13.000            counts
IbanBenchmarks.bm2b_Iban4j_ObjectCreation:gc.time                  thrpt    5         205.000                ms
IbanBenchmarks.bm3a_ApacheCommons_IsValid                          thrpt    5           4.786 ±   0.518   ops/s
IbanBenchmarks.bm3a_ApacheCommons_IsValid:gc.alloc.rate            thrpt    5        1491.181 ± 161.336  MB/sec
IbanBenchmarks.bm3a_ApacheCommons_IsValid:gc.alloc.rate.norm       thrpt    5   326760291.200 ±   0.001    B/op
IbanBenchmarks.bm3a_ApacheCommons_IsValid:gc.count                 thrpt    5          13.000            counts
IbanBenchmarks.bm3a_ApacheCommons_IsValid:gc.time                  thrpt    5         193.000                ms
IbanBenchmarks.bm4b_garvelink_ObjectCreation                       thrpt    5           0.863 ±   0.047   ops/s
IbanBenchmarks.bm4b_garvelink_ObjectCreation:gc.alloc.rate         thrpt    5         926.411 ±  49.976  MB/sec
IbanBenchmarks.bm4b_garvelink_ObjectCreation:gc.alloc.rate.norm    thrpt    5  1126050312.000 ±   0.001    B/op
IbanBenchmarks.bm4b_garvelink_ObjectCreation:gc.count              thrpt    5           9.000            counts
IbanBenchmarks.bm4b_garvelink_ObjectCreation:gc.time               thrpt    5         178.000                ms
```
