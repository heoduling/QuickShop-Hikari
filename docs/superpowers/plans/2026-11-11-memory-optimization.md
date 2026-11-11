# QuickShop memory optimization implementation plan

**Goal:** Deliver 6.3.0.4-2 with the three verified allocation optimizations and regression evidence on the supplied Shiroha core.

**Architecture:** Read chunk coordinates without materializing a chunk wrapper, respecting a previously decoded wrapper. Cache each shop item and each group representative only for the duration of one grouping call. Capture each player's position on its entity owner once per display sweep and reuse those snapshots, then apply display changes on the shop region. Existing matcher/event order and configuration semantics are retained.

**Tech Stack:** Existing Gradle wrapper and Java toolchain, PacketEvents 2.13.0, native Folia schedulers, existing JUnit/Mockito, Shiroha 26.2-DEV-96ca96b / Java 25.0.3.

**Spec:** The user approved implementation after the 2026-11-11 memory audit and independent verification. This plan applies those three controls to the real plugin entry points. No production deployment is requested. Reuse the current checkout on a new feature branch and preserve the previous release. No new product configuration keys or dependencies.

## Tasks

- [x] 1. Chunk packet reader. Add regression tests for signed coordinates, preserved reader index, and cached wrapper coordinates. Implement a package-visible primitive coordinate reader in PacketFactoryv1_21 and use it from onPacketSend. Verify on real packets and the actual listener.
- [x] 2. Grouping. Add a regression checking one getItem call per shop and unchanged ordered membership/statistics, then change MarketUtils.groupShopsByItem to hold method-local item snapshots and representatives. Repeat native grouping comparison with NBTAPI and matching events.
- [x] 3. Display sweep. Test owner-context reads, one snapshot per player, world/range decisions, unloaded/disabled shops, retirement, overlapping sweeps and stop while callbacks are queued. Implement bounded per-sweep capture and shop-region application. Follow virtual display recipient discovery to owner callbacks so display spawning does not reintroduce cross-owner position reads. Verify separated regions, movement, quit, display spawn/remove, and stop.
- [x] 4. Clean core-module checks and Shadow build, retained functional/configuration/data regressions, integrated allocation measurement and one fresh final code review. Final packaging archives the matching Git commit, verifies class-byte identity against both native fixtures, and publishes the source branch to the existing user fork.

## Review Focus

- PacketEvents cached wrapper and re-encode semantics, byte order, and actual listener allocation.
- Matcher extension events, NBT shop identifiers, mutation boundaries, order and method-local snapshot lifetime.
- Entity/region ownership in the full display path, stale generation callbacks, retirement/rejection and stop.
- Avoid introducing per-shop per-player scheduler fan-out or an unbounded overlap of sweep tasks.
- Final artifact preserves the prior transfer/close fixes, server customizations and configuration/database format.

## Decisions and evidence

- The audited report and independent rerun provide the design accepted by the user's implementation request. Continue inline without another design approval cycle.
- Existing checkout is clean at 196e2fd2b. Work on codex/memory-6.3.0.4-2, preserving the previous branch.
- Player positions are captured once per sweep on their owners. This is necessary to cover the audit's unverified cross-region case. A sweep remains bounded while its callbacks are pending; lifecycle generations reject stale callbacks.
- No production database connection or restart is part of these tests. Production SQL may only be restored to isolated instances if a persistence regression requires it.

## Verification results

- Final implementation commit before release metadata is 30ba30bf5. All 38 JUnit tests pass. Eighteen added regressions cover coordinate reads, grouping snapshot counts, bounded owner capture and display recipient lifecycle.
- Exact supplied Shiroha 26.2-DEV-96ca96b, Java 25.0.3, PacketEvents 2.13.0 and NBTAPI 2.16.0 pass 373 native assertions. Two actual region identities differ. Display visibility, packet refresh, move-away destruction, return respawn, quit recipient cleanup and clean shutdown pass.
- Retained native transfer/close/menu suite passes 42 assertions. Foreign global-thread inventory close still reproduces three independent core exceptions. The listener cleanup assertions pass in all three trials.
- A fresh reviewer found repeated-remove destroy cancellation and an unchecked final recipient world change. Three deterministic regressions failed against a349cf40e and pass after 30ba30bf5. The remaining focused review found no demonstrated packet/grouping/watcher defect. Evidence is in review-lifecycle-red.log and review-lifecycle-green.log under the implementation audit directory.
- Packet destruction and replacement delivery always enter the player's EntityScheduler queue. This adds a player scheduler tick and preserves destruction/spawn ordering even when the caller already owns the player. Queued spawns recheck world, range and generation. Required destruction survives repeated removal.
- Integrated coordinate reading has median 0 additional allocated bytes versus 15,224 bytes for full chunk-wrapper decoding over 64 real packets. The real listener leaves all 64 observed wrappers undecoded. This measurement covers the coordinate path.
- NBTAPI synthetic 512 variants plus 32 duplicate entries allocate 534,303,152 versus 496,492,344 bytes per invocation, a 7.0767% reduction. All 12 grouping comparisons preserve membership and statistics.
- Of 512 sampled production item records, 444 decode with the fixture's registry and dependencies. All 273 groups and representatives agree. Three-round median allocation is 2,700,800 versus 2,321,448 bytes, a 14.0459% reduction. The other 68 records remain outside this grouping comparison.
- All 80 packaged YAML resources match the prior release. Persistence, API and platform classes match byte for byte. ContainerShop differs only by the displayItem field's volatile access flag. Full database evidence is inherited from the prior MySQL 8.0.46 and MariaDB 12.3.2 upgrade/restart comparison. Current runtime fixtures use isolated H2.
- Failed fixture runs are retained. Initial display setup used disableDisplay=true, an initial functional client lacked protocol assets, and a repeated native run inherited a dead test player. Saved NBT proves Health=0 and teleport=false for that player. The final fixture resets test player data, makes test players invulnerable and verifies owner-context teleport destinations. A stale result-file runner guard was corrected. The functional fixture now persists newly constructed shop IDs through the normal registration entry before testing history, avoiding an invalid second registration.
- Measurements represent local allocation controls and native functional behavior. Production TPS, GC, full custom-item dependencies and rendered client appearance require production-equivalent workload acceptance.
