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
Each invalid IBAN is derived from a valid one via `RandomIban.invalidString()`, which applies one of six sabotage strategies with equal probability:
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

### 📊 Latest Performance Snapshot (2026-04-19)

Measured on **Intel(R) Core(TM) i7-1165G7 @ 2.80GHz**, **OpenJDK 21.0.7**, Linux,
single core (`taskset -c 0`), ParallelGC, `-XX:-StackTraceInThrowable`.
2 forks × 4–5 iterations × 2 s each.

#### Valid IBANs (best-case / accept path)

|   #   | Library              |  Throughput (ops/s) |  Memory (B/op) | vs. iban-commons |
|:-----:|:---------------------|--------------------:|---------------:|:----------------:|
| bmv1  | 🌟 **iban-commons**  |       **5,591,566** |         **~0** |     baseline     |
| bmv5  | jbanking             |           4,278,475 |            298 |   ~1.3× slower   |
| bmv3  | Apache Commons       |           3,208,846 |            442 |   ~1.7× slower   |
| bmv2  | iban4j               |           2,870,107 |          1,133 |   ~1.9× slower   |
| bmv4  | Garvelink            |           2,269,465 |            867 |   ~2.5× slower   |

#### Invalid IBANs (rejection path)

|   #   | Library              |  Throughput (ops/s) |  Memory (B/op) | vs. iban-commons |
|:-----:|:---------------------|--------------------:|---------------:|:----------------:|
| bmi1  | 🌟 **iban-commons**  |      **10,740,280** |         **~0** |     baseline     |
| bmi5  | jbanking             |           7,880,633 |            154 |   ~1.4× slower   |
| bmi3  | Apache Commons       |           6,531,841 |            247 |   ~1.6× slower   |
| bmi4  | Garvelink            |           2,250,043 |            647 |   ~4.8× slower   |
| bmi2  | iban4j               |           1,801,759 |          1,156 |   ~6.0× slower   |

Memory figures (B/op) from JMH `gc.alloc.rate.norm` profiler.

### Key Takeaways

**iban-commons is consistently fastest** across both valid and invalid input.
Its rejection path is actually *faster* than its accept path (~10.7 M ops/s vs. ~5.6 M ops/s),
because many invalid IBANs are rejected early by length or country-code checks before the
full Mod-97 computation is reached.

**Memory allocation is effectively zero** — the `char[]`-based validation pipeline introduced in 1.8.5
eliminates all transient heap allocation (< 0.001 B/op measured). All competing libraries allocate
between 154 B/op (jbanking, rejection path) and 1,133 B/op (iban4j, valid path).

**jbanking** is the strongest challenger, ranking second on both paths (~4.3 M ops/s valid,
~7.9 M ops/s invalid) at a moderate memory cost (~298 B/op valid, ~154 B/op invalid).

**Apache Commons** ranks third on both paths (~3.2 M ops/s valid, ~6.5 M ops/s invalid).
Its regex can short-circuit on structural failures, keeping its rejection performance competitive.

**iban4j and Garvelink** both incur significant allocation on the rejection path
because their exception-based API constructs full exception objects even when
`-XX:-StackTraceInThrowable` eliminates the stack trace overhead.

> **Note:** Results from the previous snapshot (2026-04-09, Generational ZGC, 3 forks × 10 iter × 2 s)
> are not directly comparable to the current figures due to the GC configuration change (ParallelGC)
> and the near-zero allocation improvements introduced in iban-commons 1.8.5.
