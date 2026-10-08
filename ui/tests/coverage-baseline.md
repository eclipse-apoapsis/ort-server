# UI test coverage baseline

This snapshot supports the test-quality review in #6133 and establishes the baseline requested by #6181. It is not a coverage target or a continuously maintained scoreboard.

## Reproduction

Complete the normal UI setup described in [the README](../README.md), including generated API and router sources, then run from the repository root:

```shell
pnpm -C ui test --run
pnpm -C ui test:coverage
```

The coverage command runs the existing unit/component suite with V8 coverage enabled. It does not require a live backend, Docker services, or Playwright browsers.
Open `ui/coverage/index.html` for source-level results; aggregate and per-file totals are in `ui/coverage/coverage-summary.json`, and LCOV is in `ui/coverage/lcov.info`.
Reports are regenerated on each run and are not committed.

## Recorded environment

| Item                            | Value                                                                                                        |
| ------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| Date                            | 2026-10-08                                                                                                   |
| Source revision                 | `7a20e20fce33ff37e97f48b3e24927c6c4d99034`                                                                   |
| Working tree before measurement | Clean                                                                                                        |
| Revision context                | GitButler workspace commit including coverage tooling and the React Compiler default-mode changes from #6178 |
| React Compiler mode             | `infer`; compiler runs in jsdom tests, not all test environments                                             |
| React Compiler version          | 1.0.0                                                                                                        |
| Node version used by pnpm       | 24.21.0                                                                                                      |
| pnpm version                    | 12.8.2                                                                                                       |
| Vitest version                  | 5.0.3                                                                                                        |
| Coverage provider               | `@vitest/coverage-v8` 5.0.3                                                                                  |
| Command                         | `pnpm -C ui test:coverage`                                                                                   |
| Result                          | 137 test files and 978 tests passed                                                                          |
| Reported source files           | 389                                                                                                          |

This records the complete workspace revision rather than only the coverage-tooling commit, because compiler adoption changes are also present. PR #6178 merged on 2026-10-08 at `11a5288053c3c73596105502834df6ddc868a367`, making React Compiler's `infer` mode the default. Its final UI production sources and unit tests match those used for this measurement, and the compiler configuration is unchanged. The recorded totals therefore remain applicable; the source revision above remains the actual measurement revision, not the merge revision.

## Aggregate results

These values come from the JSON summary's `total` entry and agree with the terminal summary.

| Metric     | Covered |  Total | Coverage |
| ---------- | ------: | -----: | -------: |
| Statements |  11,700 | 17,515 |   66.79% |
| Branches   |   8,981 | 15,449 |   58.13% |
| Functions  |   1,626 |  3,069 |   52.98% |
| Lines      |   7,763 | 11,608 |   66.87% |

### Results by source area

Each row sums covered and total counts from the JSON summary for files under the corresponding `src` directory. Percentages are calculated from those sums, not averaged from individual file percentages.

| Area                       | Statements | Branches | Functions |  Lines |
| -------------------------- | ---------: | -------: | --------: | -----: |
| Components                 |     65.91% |   54.08% |    60.57% | 71.59% |
| Helpers                    |     81.45% |   71.53% |    91.18% | 80.89% |
| Hooks                      |     87.18% |   81.15% |    80.58% | 89.90% |
| Library utilities          |     79.84% |   58.04% |    84.06% | 81.22% |
| Providers                  |     93.81% |   76.60% |    91.84% | 93.65% |
| Routes                     |     64.27% |   58.85% |    40.45% | 60.78% |
| Schemas                    |     89.55% |   84.00% |    63.64% | 89.55% |
| Stores                     |     82.76% |  100.00% |    75.00% | 84.78% |
| Files directly under `src` |     17.65% |    8.70% |     0.00% | 18.00% |

## Scope and exclusions

The configuration includes `src/**/*.{ts,tsx}`, including files not imported by any test. For example, `src/app.tsx`, `src/main.tsx`, and `src/components/plugin-availability-toggle.tsx` appear with zero statement coverage in this snapshot.

| Exclusion                                       | Reason                                            |
| ----------------------------------------------- | ------------------------------------------------- |
| `src/api/**`                                    | Generated API client and related generated code   |
| `src/routeTree.gen.ts`                          | Generated route tree                              |
| `src/**/*.d.ts`                                 | Type declarations, not executable production code |
| Tests, fixtures, dependencies, and build output | Outside the source inclusion pattern              |

Production route files, providers, entry points, and environment configuration remain included. They are not excluded because they are difficult to test.
The included-file lists were checked to confirm that generated API/router code, declarations, tests, and fixtures are absent.

## Interpretation and limitations

- Coverage measures execution, not the usefulness of assertions. A covered line can still lack a meaningful behavior test.
- Mocked API calls and child components limit integration confidence. These figures do not measure backend behavior or complete user workflows.
- Limited UI integration depth is deliberate: expanding Playwright coverage across the deeply nested route hierarchy would be disproportionately costly. Playwright coverage is not included here.
- Original-source paths and line ranges were inspected for `src/helpers/capitalize.ts` and `src/components/data-table/data-table-header.tsx` in HTML and LCOV. Locations map to the source files rather than requiring readers to inspect generated JavaScript. Existing compiler tests also pass with coverage enabled.
- Source mapping does not make function and branch counts equivalent to handwritten source constructs. For example, the table header report contains both `DataTableHeader` and `DataTableHeader_2` entries at the same source line. Transformed variants and compiler-generated control flow can affect the counts; do not interpret every uncovered branch as a missing product behavior test or compare totals across compiler modes without review.
- Two successful tooling-validation runs had the same file scope and denominators but slightly different covered counts in `src/components/run-duration.tsx`, which reads the current time. Line coverage was unchanged. Small timing-dependent changes are possible; this snapshot is not a strict equality check.
- Two repeat coverage runs during tooling validation encountered intermittent UI test timeouts before a subsequent run passed. Coverage adds runtime overhead; investigate repeated failures rather than weakening assertions or introducing retries solely to obtain a report.

## Candidates for focused follow-up

Use the HTML report and known regressions to select a bounded batch for #6185. These are investigation candidates, not requirements to maximize percentages:

- Route-specific callbacks and state transitions: routes have lower function coverage than the logic/helper areas.
- Uncovered interactive components, such as `plugin-availability-toggle.tsx`: first identify important observable behavior and existing tests at other layers.
- Resolution components under `src/components/resolutions/`: check which meaningful interactions are already covered by mocked or route-level tests before adding new tests.

Shared-table consolidation (#6183), rendering-test pruning (#6184), and compiler-test sanitization (#6186) should judge test value separately from coverage totals. PR reporting and any future coverage service belong to #6182.
