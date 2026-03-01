# IBAN Commons Benchmarks

A performance comparison module using the [**Java Microbenchmark Harness (JMH)**](https://github.com/openjdk/jmh) to evaluate throughput and memory allocation of different IBAN libraries in the Java ecosystem.

If you feel other libraries should be included, or have suggestions for this suite, please get in touch.

## 🎯 Benchmark Scope

Two benchmark groups cover the full validation spectrum:

| Benchmark  | Library                                                                            | Description                                              |
|:----------:|:-----------------------------------------------------------------------------------|:---------------------------------------------------------|
|   `bm1`    | [`iban-commons` (de.speedbanking)](https://github.com/SpeedBankingDe/iban-commons) | High-performance ASCII-math validation – **valid** IBANs |
|   `bm2`    | [`iban4j` (org.iban4j)](https://iban4j.org/)                                       | Exception-based validation – **valid** IBANs             |
|   `bm3`    | [`Apache Commons Validator`](https://commons.apache.org/proper/commons-validator/) | Regex-based IBAN validation – **valid** IBANs            |
|   `bm4`    | [`garvelink iban` (nl.garvelink.oss)](https://github.com/barend/java-iban)         | Object-oriented parsing – **valid** IBANs                |
|   `bm5`    | `iban-commons`                                                                     | Same as bm1 – **invalid** IBANs (rejection cost)         |
|   `bm6`    | `iban4j`                                                                           | Same as bm2 – **invalid** IBANs (rejection cost)         |
|   `bm7`    | `Apache Commons Validator`                                                         | Same as bm3 – **invalid** IBANs (rejection cost)         |
|   `bm8`    | `garvelink iban`                                                                   | Same as bm4 – **invalid** IBANs (rejection cost)         |

The test data uses a 50/50 mix of normalized and space-formatted IBAN strings across all supported countries, generated randomly per run to prevent JIT over-specialisation.
Invalid IBANs are corrupted either by a random character swap (breaking the checksum) or by removing 1–3 characters (breaking the length).

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
java -jar target/iban-commons-benchmarks.jar "bm[1-4]" -i 10 -r 5s

# Run with GC profiling
java -jar target/iban-commons-benchmarks.jar IbanBenchmarks -prof gc
```

Results are written as `.log` and `.json` to `target/` and automatically archived to `benchmarks/history/` for regression tracking.

## 📊 Results & Visualization

To visualize results interactively:

1. Go to **[JMH Visualizer](https://jmh.morethan.io/)**.
2. Drag and drop the `.json` file from `target/` or `benchmarks/history/`.

### 📊 Latest Performance Snapshot (2026-03-01)

Measured on **Intel(R) Core(TM) i7-1165G7 @ 2.80GHz**, **OpenJDK 21.0.7**, Linux,
single core (`taskset -c 0`), Generational ZGC, `-XX:-StackTraceInThrowable`.
30 measurement iterations (3 forks × 10 iterations × 2 s each).

#### Valid IBANs (best-case / accept path)

|  #  | Library             |  Throughput (ops/s) |   ±Error |  Memory (B/op) | vs. iban-commons |
|:---:|:--------------------|--------------------:|---------:|---------------:|:----------------:|
| bm1 | 🌟 **iban-commons** |       **7,721,430** | ±449,089 |      **105.9** |     baseline     |
| bm3 | Apache Commons      |           4,081,226 | ±220,088 |          318.9 |   ~1.9× slower   |
| bm2 | iban4j              |           1,800,153 |  ±68,331 |        1,113.9 |   ~4.3× slower   |
| bm4 | Garvelink           |           1,608,650 |  ±44,688 |          881.7 |   ~4.8× slower   |

#### Invalid IBANs (rejection path)

|  #  | Library             |  Throughput (ops/s) |   ±Error |  Memory (B/op) | vs. iban-commons |
|:---:|:--------------------|--------------------:|---------:|---------------:|:----------------:|
| bm5 | 🌟 **iban-commons** |      **10,991,232** | ±333,974 |       **78.4** |     baseline     |
| bm7 | Apache Commons      |           9,166,116 | ±312,516 |          165.1 |   ~1.2× slower   |
| bm8 | Garvelink           |           1,721,277 |  ±55,096 |          689.3 |   ~6.4× slower   |
| bm6 | iban4j              |           1,502,218 | ±147,249 |          998.8 |   ~7.3× slower   |

### Key Takeaways

**iban-commons is consistently fastest** across both valid and invalid input.
Its rejection path is actually *faster* than its accept path (~11 M ops/s vs. ~7.7 M ops/s),
because many invalid IBANs are rejected early by length or country-code checks before the
full Mod-97 computation is reached.

**Memory allocation is 3×–10× lower** than competing libraries.
The ASCII-math approach for Modulo 97 avoids the intermediate `String` and `BigInteger`
allocations that account for the high B/op figures in `iban4j` (>1,100 B/op) and Garvelink (>880 B/op).

**Apache Commons** performs surprisingly well on the rejection path (~9.2 M ops/s)
because its regex can short-circuit on structural failures before evaluating the full checksum.
On the valid path it ranks second (~4.1 M ops/s) at a moderate memory cost (~320 B/op).

**iban4j and Garvelink** both incur significant allocation on the rejection path
because their exception-based API constructs full exception objects even when
`-XX:-StackTraceInThrowable` eliminates the stack trace overhead.
