# RISU-PPUM: SMRF, SLRF, and SDIF algorithms

Official implementation of the three privacy-preserving utility mining (PPUM) algorithms of the article

> Mohamed Ashraf, Sherine Rady, Tamer Abdelkader, Tarek F. Gharib.
> **Efficient privacy preserving algorithms for hiding sensitive high utility itemsets.**
> *Computers & Security* 132 (2023) 103360. <https://doi.org/10.1016/j.cose.2023.103360>

Given a quantitative transaction database, the unit profits of its items, a minimum utility threshold and the
sensitive high-utility itemsets to protect, each algorithm changes the database so that none of the sensitive
itemsets is a high-utility itemset anymore, while trying to keep the other high-utility itemsets. The three
algorithms share two ideas of the article:

- **Real Item Sensitive Utility (RISU, Eq. 2).** The RISU of an item is its total utility in the transactions
  that contain a sensitive itemset. It is used to give every sensitive itemset one definite victim item before
  the hiding starts and can be easily adapted to the other HUPM extensions.
- **Weighted Sorting (Eq. 1).** The sensitive transactions are sorted by SC(T) / (NSC(T) + 1), so the
  transactions that support many sensitive and few non-sensitive itemsets are changed first.

They differ in the choice of the victim item:

| Algorithm | Victim item of each sensitive itemset |
|---|---|
| **SMRF** (Algorithm 1) | the item with the largest RISU |
| **SLRF** (Algorithm 2) | the item with the smallest RISU |
| **SDIF** (Algorithm 3) | the most desirable item, with the largest sensitive cover and the smallest non-sensitive cover; otherwise the item with the smallest non-sensitive cover; ties go to the smallest RISU |

The repository also serves as a small framework for comparing PPUM algorithms. Every algorithm runs on its own copy of
the same inputs and is measured in the same way: hiding failure, artificial cost, missing cost, itemset utility
similarity, dataset utility similarity, transaction modification ratio, runtime, and peak memory. Adding your own
algorithm takes one class.

## Contents

- [Requirements and build](#requirements-and-build)
- [Quick start: the worked example of the article](#quick-start-the-worked-example-of-the-article)
- [Sanitizing your own database](#sanitizing-your-own-database)
- [Settings](#settings)
- [Input formats](#input-formats)
- [Output and metrics](#output-and-metrics)
- [Adding your own algorithm](#adding-your-own-algorithm)
- [Using the code as a library](#using-the-code-as-a-library)
- [How the code follows the article](#how-the-code-follows-the-article)
- [Citation](#citation)
- [License](#license)
- [Contact](#contact)

## Requirements and build

Java 8 or later. Building needs a JDK; there are no other dependencies.

```sh
./build.sh          # Linux and macOS
build.bat           # Windows
mvn package         # or with Maven
```

Each command produces `target/risu-ppum-1.0.0.jar`.

## Quick start: the worked example of the article

```sh
java -jar target/risu-ppum-1.0.0.jar
```

Without options, the program uses the settings written in `Runner.ideSettings()`. As delivered, they run the
three algorithms on the worked example of the article: the database of Tables 2 and 3 with items A to F written
as 1 to 6, minUtil = 70, and the three sensitive itemsets of Section 5. The program prints a report after each
algorithm and a comparison at the end. Runtime and memory vary from machine to machine.

```text
SDIF
  Hiding failure (HF)                        0.00 %   0 of 3 sensitive itemsets are still high-utility
  Artificial cost (AC)                       0.00 %   only deletions and decreases, so no new high-utility itemsets
  Missing cost (MC)                          0.00 %   0 of 4 non-sensitive high-utility itemsets lost
  Itemset utility similarity (IUS)          53.40 %
  Dataset utility similarity (DUS)          92.72 %
  Transaction modification ratio (TMR)      28.57 %   2 of 7 transactions modified
  Runtime                                   2.75 ms
  Peak memory                               1.46 MB
  Sanitized database                     results/paper-example/SDIF/sanitized.txt

Comparison
  Algorithm     HF %     AC %     MC %    IUS %    DUS %    TMR %    Time (ms)  Memory (MB)
  SMRF          0.00     0.00    25.00    40.29    94.04    28.57         7.54         1.53
  SLRF          0.00     0.00    25.00    45.79    90.73    28.57         1.75         1.48
  SDIF          0.00     0.00     0.00    53.40    92.72    28.57         2.75         1.46
```

From an IDE, open the folder as a Maven project (or add `src/main/java` as a source folder) and run
`eg.edu.asu.ppum.Runner`. To run your own experiment from the IDE, edit `Runner.ideSettings()`.

## Sanitizing your own database

1. **Write the database and the unit profits** in the [input formats](#input-formats).

2. **Mine the high-utility itemsets (HUIs)** with any high-utility itemset mining algorithm and any
   implementation you trust. No miner is bundled, so you can use the newest or fastest one. Most miners read the
   SPMF format, which the program can write for you; it also prints the absolute threshold to give the miner:

   ```sh
   java -jar target/risu-ppum-1.0.0.jar --db data.txt --profits profits.txt --min-util 21% --export-spmf data_spmf.txt
   ```

   For example, with the EFIM algorithm from the [SPMF library](https://www.philippe-fournier-viger.com/spmf/), using the
   threshold printed by the previous command:

   ```sh
   java -jar spmf.jar run EFIM data_spmf.txt huis.txt 7922902
   ```

3. **Choose the sensitive itemsets.** Either copy the lines of the sensitive itemsets from `huis.txt` into a file,
   or let the program draw them at random among the HUIs with at least two items, as in the experiments of the
   article.

4. **Run the algorithms:**

   ```sh
   java -jar target/risu-ppum-1.0.0.jar --db data.txt --profits profits.txt --huis huis.txt --min-util 21% --shis shis.txt
   java -jar target/risu-ppum-1.0.0.jar --db data.txt --profits profits.txt --huis huis.txt --min-util 21% --random-shis 100 --seed 1
   ```

Before running, the program checks that the inputs belong together. Every item of the database must have a unit
profit. The utility written for every itemset must equal its utility in the database, which shows that the HUIs
were mined from this database and this profit file; mine them at minUtil or at a lower threshold. Every sensitive
itemset must be a high-utility itemset. A problem stops the run with a message that names the file and the line.

On large databases, give Java more memory if needed, for example `java -Xmx8g -jar ...`.

## Settings

The same settings can be written in `Runner.ideSettings()` or given on the command line. When the command line
has options, the IDE settings are not used.

| Option | Meaning | Default |
|---|---|---|
| `--db FILE` | the transactions | required |
| `--profits FILE` | the unit profits | required |
| `--huis FILE` | the high-utility itemsets of the database, in SPMF format | required |
| `--min-util VALUE` | the minimum utility: a percentage of the total utility (`21%`) or an absolute value (`70`) | required |
| `--shis FILE` | the sensitive itemsets to hide, in SPMF format | give `--shis` or `--random-shis` |
| `--random-shis N` | draw N sensitive itemsets at random among the HUIs with at least two items | |
| `--seed S` | the seed of the random draw | `1` |
| `--algorithms LIST` | comma-separated names: `SMRF`, `SLRF`, `SDIF`, or the full class names of other algorithms | `SMRF,SLRF,SDIF` |
| `--out FOLDER` | the output folder | `results` |
| `--export-spmf FILE` | only write the database in SPMF format, to mine it | |
| `--help` | list the options | |

The same seed always draws the same sensitive itemsets, in the same order, from the same set of HUIs, whichever
miner wrote the HUI file.

## Input formats

**Database**: one transaction per line, written as `item:quantity` pairs separated by spaces, the format of the
original code (`item,quantity` is also accepted). Items are non-negative integers, quantities are positive
integers, and an item appears at most once in a line. An empty line is an empty transaction. The worked example
(`examples/paper-example/transactions.txt`) starts with:

```text
1:1 2:3 5:2
3:4 4:5
5:2 6:3
```

**Unit profits**: one `item, profit` pair per line; profits are positive integers.

```text
1, 6
2, 4
3, 2
```

**Itemsets** (the HUIs and the sensitive itemsets): the SPMF format written by most miners, one itemset per line,
its items separated by spaces followed by `#UTIL:` and its utility in the database. Other fields that some miners
add, such as `#SUP:`, are ignored.

```text
1 2 5 #UTIL: 82
2 3 5 #UTIL: 106
```

The order of the sensitive itemset file matters only for ties: when two sensitive itemsets have the same
priority, the one listed first is hidden first. Likewise, when two items of an itemset tie in the victim rule,
the item written first wins.

**Minimum utility**: either a percentage of the total utility of the database, such as `21%`, rounded down to a
whole number as in the experiments of the article (21 % of chess gives 7,922,902), or an absolute value such as
`70`. Itemsets of the HUI file below the threshold are ignored, so a file mined at a lower threshold can serve
several higher ones.

## Output and metrics

After each algorithm the program prints its report, and after the last one a comparison table. It writes into
the output folder:

| File | Content |
|---|---|
| `sensitive_itemsets.txt` | the sensitive itemsets that were hidden, in SPMF format; give it to `--shis` to repeat the experiment |
| `SMRF/sanitized.txt`, ... | the sanitized database of each algorithm, in the input format and with one line per original transaction (a transaction that lost all its items becomes an empty line) |
| `SMRF/report.txt`, ... | the report printed for that algorithm |
| `results.csv` | one row per run with the inputs, every metric and the counts behind them; rows are appended, so several experiments can share one file |

The metrics are those of Section 6.1 of the article, plus the peak memory:

| Metric | Meaning |
|---|---|
| Hiding failure, HF (Eq. 3) | the fraction of sensitive itemsets that are still high-utility after sanitization |
| Artificial cost, AC (Eq. 4) | the fraction of the HUIs of the sanitized database that were not HUIs of the original one |
| Missing cost, MC (Eq. 5) | the fraction of the non-sensitive HUIs that are no longer high-utility |
| Itemset utility similarity, IUS (Eq. 6) | the total utility of the HUIs of the sanitized database divided by that of the HUIs of the original one |
| Dataset utility similarity, DUS (Eq. 7) | the total utility of the sanitized database divided by that of the original one |
| Transaction modification ratio, TMR (Eq. 8) | the fraction of transactions that were changed |
| Runtime | the time taken by the sanitization alone, in milliseconds; reading the inputs and measuring the side effects are not included |
| Peak memory | the largest amount of Java heap in use while the algorithm ran, in megabytes, including the loaded data, as SPMF reports memory |

All metrics are exact and need no second mining. The HUIs of the original database are known, so their utilities
in the sanitized database are recomputed directly. SMRF, SLRF and SDIF only delete items and lower quantities,
so no itemset can gain utility: no fake HUI can appear (AC = 0), and the HUIs of the sanitized database are
exactly the original HUIs that are still above the threshold. If an algorithm adds items or raises quantities,
new HUIs could appear that only mining would find; AC and IUS are then shown as `n/a`, and the other metrics stay
exact.

## Adding your own algorithm

Implement `eg.edu.asu.ppum.algorithms.SanitizationAlgorithm`: a name for the reports and a method that sanitizes
the database in place. The framework gives each algorithm its own copy of the database and a `HidingProblem` with
the unit profits, minUtil, the sensitive itemsets and the non-sensitive HUIs, each with its utility in the
original database. Change transactions only through `Transaction.setQuantity` and `Transaction.remove`. A
deliberately simple example:

```java
package com.example;

import eg.edu.asu.ppum.algorithms.HidingProblem;
import eg.edu.asu.ppum.algorithms.SanitizationAlgorithm;
import eg.edu.asu.ppum.data.Itemset;
import eg.edu.asu.ppum.data.Transaction;
import eg.edu.asu.ppum.data.TransactionDatabase;

/** Hides each sensitive itemset by deleting its first item from the transactions that support it. */
public class DeleteFirstItem implements SanitizationAlgorithm {

    @Override
    public String name() {
        return "DeleteFirstItem";
    }

    @Override
    public void sanitize(TransactionDatabase database, HidingProblem problem) {
        for (Itemset itemset : problem.sensitiveItemsets()) {
            long utility = 0;
            for (Transaction transaction : database) {
                utility += transaction.utilityOf(itemset, problem.profits());
            }
            for (Transaction transaction : database) {
                if (utility < problem.minUtil()) {
                    break;
                }
                if (transaction.containsAll(itemset)) {
                    utility -= transaction.utilityOf(itemset, problem.profits());
                    transaction.remove(itemset.get(0));
                }
            }
        }
    }
}
```

Compile it against the jar and give its class name with the built-in algorithms. Use `-cp` (with `;` instead of
`:` on Windows), because `java -jar` ignores the class path:

```sh
javac -cp target/risu-ppum-1.0.0.jar -d my-classes DeleteFirstItem.java
java -cp target/risu-ppum-1.0.0.jar:my-classes eg.edu.asu.ppum.Runner \
     --db data.txt --profits profits.txt --huis huis.txt --min-util 21% --random-shis 100 \
     --algorithms SMRF,SLRF,SDIF,com.example.DeleteFirstItem
```

From an IDE, add the class to your project and its name to the `algorithms` list of `Runner.ideSettings()`.

To try another victim rule with the RISU, Weighted Sorting and hiding steps of the article, extend
`RisuBasedAlgorithm` instead and implement `name()` and `chooseVictims()`; `SMRF.java` is a short example. You can
also override `orderSensitiveItemsets()` to try another processing order of the sensitive itemsets.

## Using the code as a library

```java
import eg.edu.asu.ppum.Experiment;
import eg.edu.asu.ppum.algorithms.SDIF;
import eg.edu.asu.ppum.data.Itemset;
import eg.edu.asu.ppum.evaluation.RunResult;

Experiment experiment = Experiment.load(Paths.get("data.txt"), Paths.get("profits.txt"),
        Paths.get("huis.txt"), "21%");
List<Itemset> sensitive = experiment.drawSensitiveItemsets(100, 1);
RunResult result = experiment.run(new SDIF(), sensitive);
result.sanitizedDatabase().write(Paths.get("sanitized.txt"));
System.out.print(result.report("sanitized.txt"));
```

`experiment.readSensitiveItemsets(path)` reads the sensitive itemsets from a file instead. `result.sideEffects()`
gives every metric and count as numbers. `Experiment.run` never changes the loaded database, so several algorithms
can be run one after another on the same experiment.

## How the code follows the article

The source code is organized in four packages under `src/main/java/eg/edu/asu/ppum`:

| Package | Classes |
|---|---|
| `eg.edu.asu.ppum` | `Runner` (settings, command line, reports, result files) and `Experiment` (checked inputs, sensitive itemsets, measured runs) |
| `algorithms` | `SanitizationAlgorithm`, `HidingProblem`, `Algorithms`, `RisuBasedAlgorithm`, `SMRF`, `SLRF`, `SDIF` and the steps they share |
| `data` | the database, transactions, unit profits, itemsets, their file formats, and a prefix tree for fast itemset look-ups |
| `evaluation` | `SideEffects` (the metrics), `MemoryMonitor` and `RunResult` |

## Citation

If you use this code, please cite the article:

```bibtex
@article{Ashraf2023RISU,
  author  = {Ashraf, Mohamed and Rady, Sherine and Abdelkader, Tamer and Gharib, Tarek F.},
  title   = {Efficient privacy preserving algorithms for hiding sensitive high utility itemsets},
  journal = {Computers \& Security},
  volume  = {132},
  pages   = {103360},
  year    = {2023},
  doi     = {10.1016/j.cose.2023.103360}
}
```

GitHub shows the same reference under "Cite this repository", from [`CITATION.cff`](CITATION.cff).

## License

Copyright (C) 2023-2026 Mohamed Ashraf.

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General
Public License version 3 as published by the Free Software Foundation. It is distributed in the hope that it
will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
PARTICULAR PURPOSE. See [`LICENSE`](LICENSE) for the full terms. If you distribute a program that includes or
modifies this code, you must license it under the same terms, make its source code available and keep this
copyright notice.

The first version of this code was written within the PPUM part of
[PPSF](http://ppsf.ikelab.net), the Privacy-Preserving and Security Framework (copyright Jerry Chun-Wei Lin and
contributors, GNU GPL version 3; Lin et al., ICDM Workshops 2018,
<https://doi.org/10.1109/ICDMW.2018.00208>). This release is a rewrite and keeps that license.

## Contact

Mohamed Ashraf, Information Systems Department, Faculty of Computer and Information Sciences, Ain Shams
University, Cairo, Egypt: <Mohamed.a.a.h.is@gmail.com>. Questions and bug reports are welcome as GitHub issues.
