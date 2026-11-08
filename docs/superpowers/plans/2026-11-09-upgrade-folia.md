# QuickShop 6.3.0.4 upgrade and compatibility plan

**Goal:** Deliver a source-built 6.3.0.4-based plugin preserving the supplied server modifications, fixing the demonstrated inventory-close listener failure, and validating old configuration and data offline.

**Architecture:** Merge the official 6.3.0.4 tag into server-6.3.0.1. Keep the shared history GUI, browse lifecycle page, price suggestion guards, silent lookup scheduling and inventory-holder optimization. Clean close tracking before asynchronous work, obtain player locale on its entity owner, and query/update the chest only on its region owner. The user narrowed scope to QuickShop only: leave ExcellentCrates and nightcore unchanged and report their failures separately.

**Tech Stack:** Existing Gradle wrapper, Java 21 compilation/Java 25 runtime, existing FoliaLib, Shiroha 26.2-DEV-96ca96b, JUnit/Mockito and native runtime probes.

**Constraints:** No production file replacement, restart, database connection or deployment. No new product dependency or unrelated behavior changes. Work on codex/upgrade-6.3.0.4-folia in the existing checkout; preserve server-6.3.0.1. Deliver a freshly built JAR, sanitized source archive, checksums and evidence. Test actual available historical CSV backup as a historical snapshot, never describe it as the current production database.

## Tasks

- [x] 1. Merge official tag; resolve build/history/keeper conflicts by retaining shared history helper and upstream empty-result behavior; baseline compilation and preserved custom tests.
- [x] 2. Close tests fail on the unchanged listener, then pass with the minimal owner scheduling and tracking cleanup. All preserved tests pass (full counts in XML); native owner/foreign callbacks and edge cases continue in the runtime suite.
- [x] 3. Clean core-module checks/shadow build and structural verification. Exact-core native probes pass 42 assertions: three transfer trials, three foreign-close trials, three owner-close controls, region transfer control, browse/history window smoke tests. Preserved suggestion/silent/browse behavior has unit coverage; this is not a full economy, multi-player history pagination or complete third-party opening test. Foreign native-close core failures remain separately recorded.
- [x] 4. Configuration compatibility: exact seven production YAML copies, with only local connection/telemetry overrides. Restart comparison preserves all existing values except config-version; three upstream defaults merge automatically.
- [x] 5. Data compatibility: isolated MySQL 8.0.46, original-version schema, historical CSV restore, old 19->20 baseline then repaired 20->21 upgrade and normal restart. All eleven non-metadata table fingerprints unchanged from baseline; Unicode metadata write survives restart. Old and new versions both load 3306/3569 shops and fail on the same 2702/17367 item records (identical failure-ID digest). This is a historical snapshot, not current production coverage.
- [x] 6. Fresh independent read-only review completed. Added a minimal guard and red/green regression for an upstream missing-UUID API null dereference; final 20 tests pass. Two pre-existing history menu defects are disclosed without expanding the targeted fix. Delivery contains source/artifact/checksums and sanitized evidence; preserve local branch without production deployment.

## Evidence and rulings

- The user approved implementation of the previously described upgrade/retained-customization plan; no additional design approval needed.
- The supplied configuration archive has a nested historical CSV backup without schema sidecars. Even the original importer expects sidecars. Therefore the fixture batch-imports the CSV into the original plugin-created schema, preserving column semantics and validating row counts; it does not claim that the product's backup-import command was tested.
- Upstream's per-shop transfer scheduling and trade amount validation are retained, not reimplemented.
- Build evidence: dependencies resolved through the existing local proxy. Gradle test class loading fails with the Chinese output path on Windows; the audit init script redirects output to E:/codex-build/quickshop-output-20261109. No source/dependency substitution was used.
- Task 2 consumes the merged PlayerListener/API from Task 1; runtime probes consume the actual built artifact from Task 3. Config/data tests use separate copies of the same artifact and no production endpoints.
- The final API guard changes no persistence/configuration code. Compatibility runs used the preceding repaired build; final delivery is checked against those classes, with only AbstractShopManager (the null guard) allowed to differ. Exact-core transfer/close/menu probes were repeated on the guarded build. The final post-commit rebuild must have identical class entries to that tested build.
- The old baseline already purges 606 expired messages under its retention policy, before the comparison baseline is captured. This is not an upgrade regression. No data repair or historical identifier rewrite is performed.
