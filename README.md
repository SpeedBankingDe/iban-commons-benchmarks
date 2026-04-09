# IBAN Commons Benchmarks

A performance comparison module using the [**Java Microbenchmark Harness (JMH)**](https://github.com/openjdk/jmh) to evaluate throughput and memory allocation of different IBAN libraries in the Java ecosystem.

If you feel other libraries should be included, or have suggestions for this suite, please get in touch.

## 🎯 Benchmark Scope

Two benchmark groups cover the full validation spectrum:

| Benchmark  | Library                                                                            | Description                                              |
|:----------:|:-----------------------------------------------------------------------------------|:---------------------------------------------------------|
|   `bmv1`   | [`iban-commons` (de.speedbanking)](https://github.com/SpeedBankingDe/iban-commons) | High-performance ASCII-math validation – **valid** IBANs |
|   `bmv2`   | [`iban4j` (org.iban4j)](https://iban4j.org/)                                       | Exception-based validation – **valid** IBANs             |
|   `bmv3`   | [`Apache Commons Validator`](https://commons.apache.org/proper/commons-validator/) | Regex-based IBAN validation – **valid** IBANs            |
|   `bmv4`   | [`garvelink iban` (nl.garvelink.oss)](https://github.com/barend/java-iban)         | Object-oriented parsing – **valid** IBANs                |
|   `bmv5`   | [`jbanking` (fr.marcwrobel)](https://github.com/marcwrobel/jbanking)               | Feature-rich banking toolkit – **valid** IBANs           |
|   `bmi1`   | `iban-commons`                                                                     | Same as bmv1 – **invalid** IBANs (rejection cost)        |
|   `bmi2`   | `iban4j`                                                                           | Same as bmv2 – **invalid** IBANs (rejection cost)        |
|   `bmi3`   | `Apache Commons Validator`                                                         | Same as bmv3 – **invalid** IBANs (rejection cost)        |
|   `bmi4`   | `garvelink iban`                                                                   | Same as bmv4 – **invalid** IBANs (rejection cost)        |
|   `bmi5`   | `jbanking`                                                                         | Same as bmv5 – **invalid** IBANs (rejection cost)        |

The test data uses a 50/50 mix of normalized and space-formatted IBAN strings across all supported countries, generated randomly per run to prevent JIT over-specialization.
Each invalid IBAN is derived from a valid one by applying one of six sabotage strategies with equal probability:
incrementing a check digit (triggering a Mod-97 failure), replacing the country code with the non-registered code XY, substituting a valid but mismatched ISO 3166 country code, injecting a letter into the numeric BBAN section, swapping two adjacent characters (transposition), or truncating the string below the minimum structural length.

### A note on `-XX:-StackTraceInThrowable`

All forks run with this JVM flag, which suppresses stack trace generation.
This isolates the **pure algorithmic cost** of validation and makes the comparison fair for libraries that use exceptions for control flow (notably `iban4j`).
It does **not** reflect default production behaviour.
For a production-realistic measurement, remove the flag from `@Fork` and re-run.

## ⚙️ Requirements & Environment

* **JDK** – [Java 21 or higher](https://adoptium.net/de/temurin/releases) (optimised for Generational ZGC)
* **Build Tool** – [Apache Maven 3.9+](https://maven.apache.org/)
* **OS** – Linux (recommended for CPU affinity via `taskset`) or Windows

## 🚀 Building and Execution

The project uses the `maven-shade-plugin` to produce a single executable JAR containing all dependencies including the JMH runner.

### 1. Build the Executable JAR

```bash
mvn clean package
# or simply (defaultGoal is clean package):
mvn
```

This produces `target/iban-commons-benchmarks.jar` and copies the run scripts to `target/`.

### 2. Run Automated Benchmarks

The provided scripts detect system information, configure Generational ZGC, and – on Linux – pin execution to a single CPU core to minimise measurement jitter.

**Linux:**
```bash
./target/run-benchmarks.sh
```

**Windows:**
```batch
target\run-benchmarks.cmd
```

Or run the JAR directly with standard JMH options:

```bash
# Run all benchmarks
java -jar target/iban-commons-benchmarks.jar IbanBenchmarks

# Run only the valid-IBAN group with custom iteration settings
java -jar target/iban-commons-benchmarks.jar "bm[1-5]" -i 10 -r 5s

# Run with GC profiling
java -jar target/iban-commons-benchmarks.jar IbanBenchmarks -prof gc
```

Results are written as `.log` and `.json` to `target/` and automatically archived to `benchmarks/history/` for regression tracking.

## 📊 Results & Visualization

To visualize results interactively:

1. Go to **[JMH Visualizer](https://jmh.morethan.io/)**.
2. Drag and drop the `.json` file from `target/` or `benchmarks/history/`.

### 📊 Latest Performance Snapshot (2026-04-09)

Measured on **Intel(R) Core(TM) i7-1165G7 @ 2.80GHz**, **OpenJDK 21.0.7**, Linux,
single core (`taskset -c 0`), Generational ZGC, `-XX:-StackTraceInThrowable`.
30 measurement iterations (3 forks × 10 iterations × 2 s each).

#### Valid IBANs (best-case / accept path)

|   #   | Library              |  Throughput (ops/s) |   ±Error |  Memory (B/op) | vs. iban-commons |
|:-----:|:---------------------|--------------------:|---------:|---------------:|:----------------:|
| bmv1  | 🌟 **iban-commons**  |       **7,207,452** | ±435,970 |       **47.9** |     baseline     |
| bmv5  | jbanking             |           6,108,009 | ±222,367 |          254.8 |   ~1.2× slower   |
| bmv3  | Apache Commons       |           5,243,764 | ±243,741 |          281.0 |   ~1.4× slower   |
| bmv2  | iban4j               |           1,948,100 |  ±92,893 |        1,340.6 |   ~3.7× slower   |
| bmv4  | Garvelink            |           1,292,532 |  ±44,896 |        1,063.0 |   ~5.6× slower   |

#### Invalid IBANs (rejection path)

|   #   | Library              |  Throughput (ops/s) |   ±Error |  Memory (B/op) | vs. iban-commons |
|:-----:|:---------------------|--------------------:|---------:|---------------:|:----------------:|
| bmi1  | 🌟 **iban-commons**  |      **11,986,711** | ±787,003 |       **39.9** |     baseline     |
| bmi5  | jbanking             |          10,080,195 | ±651,958 |          152.7 |   ~1.2× slower   |
| bmi3  | Apache Commons       |           9,853,673 | ±523,112 |          170.8 |   ~1.2× slower   |
| bmi2  | iban4j               |           1,992,301 |  ±69,978 |        1,098.7 |   ~6.0× slower   |
| bmi4  | Garvelink            |           1,508,470 | ±109,395 |          814.1 |   ~7.9× slower   |

### Key Takeaways

**iban-commons is consistently fastest** across both valid and invalid input.
Its rejection path is actually *faster* than its accept path (~12 M ops/s vs. ~7.2 M ops/s),
because many invalid IBANs are rejected early by length or country-code checks before the
full Mod-97 computation is reached.

**Memory allocation is dramatically lower** than all competing libraries — just ~48 B/op on
the accept path and ~40 B/op on the rejection path. The ASCII-math approach for Modulo 97
avoids the intermediate `String` and `BigInteger` allocations that drive up B/op figures in
`iban4j` (>1,340 B/op valid, >1,099 B/op invalid) and Garvelink (>1,063 B/op valid).

**jbanking** is the strongest new challenger, ranking second on both paths (~6.1 M ops/s valid,
~10.1 M ops/s invalid) at a moderate memory cost (~255 B/op valid, ~153 B/op invalid).

**Apache Commons** remains competitive on the rejection path (~9.9 M ops/s) because its
regex can short-circuit on structural failures before evaluating the full checksum. On the
valid path it ranks third (~5.2 M ops/s), just behind jbanking.

**iban4j and Garvelink** both incur significant allocation on the rejection path
because their exception-based API constructs full exception objects even when
`-XX:-StackTraceInThrowable` eliminates the stack trace overhead.
