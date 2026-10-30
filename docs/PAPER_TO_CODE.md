# How the code follows the article

This document maps the article (Ashraf et al., *Computers & Security* 132 (2023) 103360) to the code. It lists
where each definition, equation and line of Algorithms 1-3 is implemented, the choices the article leaves open,
and the worked example of Section 5 as computed by the code. Classes are in `src/main/java/eg/edu/asu/ppum`.

## Definitions and equations

| Article | Meaning | Code |
|---|---|---|
| Definition 1 | u(x, T) = iu(x, T) × eu(x) | `data/Transaction.utilityOf(int, UtilityTable)` |
| Definition 2 | u(X, T), the utility of an itemset in a transaction that contains it | `data/Transaction.utilityOf(Itemset, UtilityTable)` |
| Definition 3 | u(X), the utility of an itemset in the database | `data/Itemset.utility()`, read from the HUI file and checked against the database by `Experiment.load` |
| Definition 4 | TU(T), the transaction utility | `data/Transaction.transactionUtility` |
| Definition 5 | high-utility itemset: u(X) ≥ minUtil | `Experiment.load`, `Experiment.parseMinUtil` |
| Definition 6 | sensitive (SHI) and non-sensitive (NHI) high-utility itemsets | `algorithms/HidingProblem` |
| Definition 7 | victim item and victim transaction | `RisuBasedAlgorithm.chooseVictims`, `HidingLoop` |
| Definitions 8 and 10 | SC(x) and NSC(x): the SHIs and NHIs that contain an item | `algorithms/ItemCovers` |
| Definitions 9 and 11 | SC(T) and NSC(T): the SHIs and NHIs that a transaction contains | `algorithms/RisuCalculator.itemsetsIn`, `algorithms/WeightedSorting.sort` |
| Definition 12, Eq. 1 | Wt(T) = SC(T) / (NSC(T) + 1) | `algorithms/WeightedSorting.weight` |
| Definition 13 | Weighted Sorting: sensitive transactions by decreasing weight | `algorithms/WeightedSorting.sort` |
| Definition 14, Eq. 2 | RISU(x): the sum of u(x, T) over the sensitive transactions | `algorithms/RisuCalculator.scan` |
| Eqs. 3-8 | HF, AC, MC, IUS, DUS, TMR | `evaluation/SideEffects` |
| Section 6.1, metric 7 | runtime | `Experiment.run` (the sanitization alone) |

## Algorithms 1-3, line by line

The three algorithms run the same steps in `algorithms/RisuBasedAlgorithm.sanitize` and differ only in
`chooseVictims`.

| Algorithms 1 and 2 | Algorithm 3 | Step | Code |
|---|---|---|---|
| Line 1 | Line 1 | one scan: sensitive transactions and RISU | `RisuCalculator.scan` |
| Lines 2-4 | Lines 2-10 | one victim item per sensitive itemset | `SMRF.chooseVictims`, `SLRF.chooseVictims`, `SDIF.chooseVictims` |
| Lines 5-7 | Lines 11-13 | weight of every sensitive transaction | `WeightedSorting.sort`, `WeightedSorting.weight` |
| Line 8 | Line 14 | Weighted Sorting | `WeightedSorting.sort` |
| Line 9 | Line 15 | order of the sensitive itemsets: decreasing RISU of their victims | `RisuBasedAlgorithm.orderSensitiveItemsets` |
| Line 10 | Line 16 | hide the sensitive itemsets one by one | `HidingLoop.hide` |
| Line 11 | Line 17 | diff = u(S) − minUtil + 1, with the current utility of S | `HidingLoop.hideItemset` |
| Lines 12-15 | Lines 18-21 | visit the sorted transactions that support S while diff > 0 | `HidingLoop.hideItemset` |
| Lines 16-19 | Lines 22-25 | delete the victim and update the other sensitive itemsets | `HidingLoop.deleteVictim` |
| Lines 21-25 | Lines 27-31 | lower the quantity of the victim by ⌈diff / eu⌉ and update | `HidingLoop.reduceVictim` |
| Lines 29-30 | Lines 35-36 | stop when diff is no longer positive | `HidingLoop.hideItemset` |
| Lines 34-35 | Line 40 | the sanitized database | the database given to `sanitize`, written by `Runner` |

The victim rules, as implemented:

- **SMRF** (Line 3): the item of the itemset with the largest RISU.
- **SLRF** (Line 3): the item with the smallest RISU.
- **SDIF** (Lines 3-9): the candidates are the items that have both the smallest NSC and the largest SC of the
  itemset. If there are candidates, the victim is the candidate with the smallest RISU. If there are none, the
  victim is the item with the smallest RISU among those with the smallest NSC.

## Choices the article leaves open

1. **diff after a deletion.** Lines 18 (24 in Algorithm 3) write diff = diff − u(I_vic, T_vic). The worked example
   of Section 5 lowers diff by the utility that the sensitive itemset loses in the transaction, u(S, T_vic): after
   E is deleted from T4, u({B, C, E}) falls from 106 to 74, so 5 remains to be removed, and E is then lowered from 6
   to 4 in T6. With the printed formula diff would still be 25 and E would be deleted from T6. The code follows the
   worked example: once the victim leaves T, S no longer appears in T and loses all of u(S, T).
2. **A reduction that removes the item.** When ⌈diff / eu⌉ equals the quantity of the victim, lowering the quantity
   removes the item, so the code treats it as a deletion: every sensitive itemset that contained the victim in T
   loses all of its utility in T.
3. **Processing order of SDIF.** Algorithm 3 (Line 15) and Section 4.3 order the sensitive itemsets of SDIF by the
   RISU of their victim items, as SMRF and SLRF do, and the code follows Algorithm 3. The worked example of
   Section 5 processes them in decreasing order of utility ({B, C, E}, {A, B, E}, {A, B, C, D, E}), the order also
   used for the SDIF experiments of Section 6. Both orders choose the same victims; they differ in the order the
   itemsets are hidden, and so sometimes in the transactions that are modified. To use the order of decreasing
   utility, override `orderSensitiveItemsets` in a subclass of `RisuBasedAlgorithm`.
4. **Ties.** The article leaves ties open ("any one of them can be selected"). In the code:
   - between items of one itemset, the item written first wins (after RISU in SDIF);
   - between sensitive itemsets whose victims have the same RISU, the itemset listed first is hidden first;
   - between transactions with the same weight, the one that comes first in the database comes first.
5. **diff ≤ 0.** As in Line 13, an itemset whose current utility is already below minUtil is not processed, and no
   transaction is changed for it.
6. **minUtil as a percentage** of the total utility is rounded down to a whole number, as in the experiments: 21 %
   of chess gives 7,922,902.
7. **Random sensitive itemsets.** As in Section 6, they are drawn among the high-utility itemsets with at least
   two items. The candidates are sorted by their items before the draw, so a seed gives the same itemsets whatever
   miner wrote the HUI file.
8. **Metrics without a second mining.** The utility of every original HUI in the sanitized database is its original
   utility, minus its utility in the modified transactions before sanitization, plus its utility there after
   sanitization. Because the three algorithms only delete items and lower quantities, no new HUI can appear, so
   these utilities give HF, AC, MC and IUS exactly; DUS and TMR are computed directly.

## The worked example of Section 5

The files in `examples/paper-example` encode Tables 2 and 3 with the items A, B, C, D, E, F written as 1 to 6.
`huis.txt` holds the high-utility itemsets at minUtil = 70 and `shis.txt` the three sensitive itemsets of
Table 5. All values below are computed from Tables 2 and 3.

Some values printed in Section 5 differ from those computed. The example files use the computed values:

| Value | Printed | Computed from Tables 2 and 3 |
|---|---|---|
| u({A, B}) | 72 (Table 4) | 64, as in the example of Definition 3, so {A, B} is not a high-utility itemset |
| u({A, E}) | 87 (Table 4) | 75 |
| u({B, E}) | 102 (Table 4) | 106 |
| u({A, B, E}) | 90 (Table 4) | 82 |
| NSC(A), NSC(B) | 2 (Table 6) | 1 |
| NSC(T1), NSC(T4) | 3 (Table 7) | 2 |
| RISU(A) | 48 (Table 8) | 36 |
| RISU(B) | 48 (Table 8) | 52 |

The other values agree: u(F) = 72, u({E, F}) = 105, u({B, C, E}) = 106, u({A, B, C, D, E}) = 72, the sensitive
covers, the weights (T4 ≺ T6 ≺ T1 ≺ T5), RISU(C) = 18, RISU(D) = 10 and RISU(E) = 54. The victim items are those of
Section 5: E, E and E for SMRF; A, C and D for SLRF; B, C and D for SDIF.

What each algorithm does with the computed values (`java -jar target/risu-ppum-1.0.0.jar` prints the same):

**SMRF.** All three victims are E, so the itemsets keep their file order.
1. {A, B, E}, u = 82, diff = 13: in T4, u(E, T4) = 12 ≤ 13, so E is deleted. {B, C, E} falls to 74 and
   {A, B, C, D, E} to 0.
2. {B, C, E}, u = 74, diff = 5: in T6, u(E, T6) = 18 > 5, so E is lowered by ⌈5 / 3⌉ = 2, from 6 to 4.
3. {A, B, C, D, E} is already hidden.

The changes are those of Section 5. {A, E} is lost (75 → 33): MC = 25 %, IUS = 40.29 %, DUS = 94.04 %,
TMR = 2/7.

**SLRF.** Victims A (36), C (18) and D (10) give the order {A, B, E}, {B, C, E}, {A, B, C, D, E}.
1. {A, B, E}, diff = 13: in T4, u(A, T4) = 30 > 13, so A is lowered by ⌈13 / 6⌉ = 3, from 5 to 2. (Section 5
   lowers it to 1, from its printed utility of 90.)
2. {B, C, E}, diff = 37: C is deleted from T4 (diff falls by 32 to 5), then from T6, where ⌈5 / 2⌉ = 3 is its whole
   quantity.
3. {A, B, C, D, E} is already hidden.

{A, E} is lost (75 → 57): MC = 25 %, IUS = 45.79 %, DUS = 90.73 %, TMR = 2/7.

**SDIF.** Victims B (52), C (18) and D (10) give the order {A, B, E}, {B, C, E}, {A, B, C, D, E} (Algorithm 3,
Line 15).
1. {A, B, E}, diff = 13: in T4, u(B, T4) = 16 ≥ 13, so B is deleted. {B, C, E} falls to 74 and {A, B, C, D, E}
   to 0.
2. {B, C, E}, diff = 5: C is deleted from T6, where ⌈5 / 2⌉ = 3 is its whole quantity.
3. {A, B, C, D, E} is already hidden.

No non-sensitive itemset is lost: MC = 0 %, IUS = 53.40 %, DUS = 92.72 %, TMR = 2/7. With the order of Section 5,
C is deleted from T4 and T6 and then B from T4; again no non-sensitive high-utility itemset is lost, since {A, B}
is not one.
