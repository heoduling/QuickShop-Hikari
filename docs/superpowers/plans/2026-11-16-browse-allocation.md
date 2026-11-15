# Browse allocation implementation plan

**Goal** Deliver QuickShop-Hikari 6.3.0.4-3 with verified menu allocation reductions on mizuki 26.2-DEV-59603df.

**Spec** The user-provided memory-audit-20261116 report supplies two scoped candidate designs. The user's implementation request authorizes verification, integration, building and isolated runtime tests. Implement P2-A and P1 after checking their real entry points.

**Architecture** GroupedItemPage uses a package-local material getter that optimizes exactly ContainerShop. MarketUtils requests a call-local comparison context only from the exact BukkitItemMatcherImpl class. This context owns lazy normalized copies and shopId results. Public ItemMatcher and ordinary matches calls retain their contracts. Every match event gets fresh copies.

**Constraints** Java 21 product bytecode, existing pinned API and dependencies, exact supplied Java 25/core runtime, loopback H2 fixtures. Version 6.3.0.4-3. Scope is the browse page and per-grouping comparison. Existing source checkout is clean at 16a8ec340, use a new feature branch.

## Tasks

- [x] 1. Add material lookup regressions for ContainerShop, subclass and generic Shop. Observe RED. Implement exact-class fast path in MarketUtils and use it from GroupedItemPage. Observe GREEN and commit.
- [ ] 2. Add grouping context regressions for null IDs, fresh event clones, early acceptance, per-call cache lifetime and exact-matcher fallback. Observe RED. Add a grouping comparison factory to BukkitItemMatcherImpl and select it only in MarketUtils. Observe GREEN, bump version and commit.
- [ ] 3. Adapt the audit probe to compare the original -2 algorithms against actual integrated -3 entry points. Verify 400 boundary pairs, events and mutation, custom matchers/shops, NBTAPI enabled/disabled, real sample groups and allocations. Build a separate loopback server with the supplied current core. Exercise real browse GUI sorting, filtering, stock toggle, pagination and item selection through packet clients and actual inventory events. Repeat retained transfer/close/display regressions where applicable.
- [ ] 4. Run one fresh whole-diff review, resolve material findings with RED/GREEN tests, clean core-module check/shadow build, validate JAR structure, source/version/hash matching, configuration/persistence compatibility. Deliver JAR, source, Chinese report and sanitized evidence, publish matching branch to the existing user fork.

## Review focus

- Exact class guards preserve third-party Shop and matcher subclasses.
- Null, blank and missing IDs, amount normalization and reverse isSimilar retain order and result.
- Events retain independent clones and every comparison's count/order. Cached copies never enter events.
- Cache lifetime ends with each group call. Ordinary transaction matching takes its original entry point.
- Page scan order and click-owned list copies retain existing semantics.

## Ledger

- Pre-flight. Tasks 1 and 2 share MarketUtils but use independent helpers. Task 3 invokes both real integrated helpers and native GUI. Existing audit data is input evidence, and its candidate implementation is not an integrated product test.
- Implementation choice. Put grouping preparation next to the built-in matcher in a factory returning BiPredicate. Its cache is held only by the predicate created in groupShopsByItem. Ordinary matches is kept intact, avoiding a second product algorithm in the menu class.
- Implementation choice. Use the minimal P2-A direct material read. The P2-B material index changes read timing and shared list ownership beyond the verified scope.

- Task 1 complete. Three exact-class/generic material regressions reached RED for the absent helper and GREEN after integration. The focused grouping suite passes 5/5. A nested Mockito stubbing fixture failure was corrected separately and retained in material-fixture-stubbing.log.
