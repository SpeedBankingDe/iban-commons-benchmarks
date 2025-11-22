# IBAN Commons Benchmarks

A performance comparison module using the [**Java Microbenchmark Harness (JMH)**](https://github.com/openjdk/jmh) to evaluate the throughput and latency of different IBAN validation libraries in the Java ecosystem.

## 🎯 Benchmark Scope

The benchmark evaluates the performance of three different IBAN validation approaches:

| Benchmark | Library | Description |
| :-------- | :------ | :---------- |
| `bm1x`    | [`iban-commons` (de.speedbanking)](https://github.com/SpeedBankingDe/iban-commons) | Validation and object creation (using `tryParse`) |
| `bm2x`    | [`iban4j` (org.iban4j)](https://iban4j.org/)                                       | Validation and object creation (consuming exceptions on failure) |
| `bm3a`    | [`Apache Commons Validator`](https://commons.apache.org/proper/commons-validator/) | IBAN validation only (boolean result) |

The data generation process in `IbanBenchmarks.java` ensures a realistic mix of **normalized**, **formatted** (with spaces), **valid**, and **invalid** random IBAN strings to prevent over-optimization by JVMs.

## ⚙️ Requirements

* [Java Development Kit (JDK) **11 or higher**](https://adoptium.net/de/temurin/releases)

* [Apache Maven 3.x](https://maven.apache.org/)

## 🚀 Building and Execution

The project uses the `maven-shade-plugin` to create a single, executable JAR file containing all necessary dependencies, including the **JMH runner** `org.openjdk.jmh.Main` itself.

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

You can pass standard JMH options as arguments to the JAR. For example, to run only the iban-commons benchmarks (bm1x) with more measurement iterations:

```bash
java -jar target/iban-commons-benchmarks-1.0.0-SNAPSHOT.jar bm1.* -i 10 -r 5s
```
