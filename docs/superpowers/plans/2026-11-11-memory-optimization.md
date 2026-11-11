# QuickShop memory optimization implementation plan

**Goal:** Deliver 6.3.0.4-2 with the three verified allocation optimizations and regression evidence on the supplied Shiroha core.

**Architecture:** Read chunk coordinates without materializing a chunk wrapper, respecting a previously decoded wrapper. Cache each shop item and each group representative only for the duration of one grouping call. Capture each player's position on its entity owner once per display sweep and reuse those snapshots, then apply display changes on the shop region. Existing matcher/event order and configuration semantics are retained.

**Tech Stack:** Existing Gradle wrapper and Java toolchain, PacketEvents 2.13.0, native Folia schedulers, existing JUnit/Mockito, Shiroha 26.2-DEV-96ca96b / Java 25.0.3.

**Spec:** The user approved implementation after the 2026-11-11 memory audit and independent verification. This plan applies those three controls to the real plugin entry points. No production deployment is requested. Reuse the current checkout on a new feature branch and preserve the previous release. No new product configuration keys or dependencies.

## Tasks

- [ ] 1. Chunk packet reader. Add regression tests for signed coordinates, preserved reader index, and cached wrapper coordinates. Implement a package-visible primitive coordinate reader in PacketFactoryv1_21 and use it from onPacketSend. Verify on real packets and the actual listener.
- [ ] 2. Grouping. Add a regression checking one getItem call per shop and unchanged ordered membership/statistics, then change MarketUtils.groupShopsByItem to hold method-local item snapshots and representatives. Repeat native grouping comparison with NBTAPI and matching events.
- [ ] 3. Display sweep. Test owner-context reads, one snapshot per player, world/range decisions, unloaded/disabled shops, retirement, overlapping sweeps and stop while callbacks are queued. Implement bounded per-sweep capture and shop-region application. Follow virtual display recipient discovery to owner callbacks so display spawning does not reintroduce cross-owner position reads. Verify separated regions, movement, quit, display spawn/remove, and stop.
- [ ] 4. Clean core-module checks and Shadow build, structural JAR verification, retained functional/configuration/data regressions, integrated allocation measurement and one fresh final code review. Commit, archive the matching source, deliver JAR/report/checksums, and publish the source branch to the existing user fork.

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
