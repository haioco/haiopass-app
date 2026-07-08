# Graph Report - .  (2026-07-05)

## Corpus Check
- Corpus is ~18,045 words - fits in a single context window. You may not need a graph.

## Summary
- 366 nodes · 673 edges · 22 communities (19 shown, 3 thin omitted)
- Extraction: 93% EXTRACTED · 7% INFERRED · 0% AMBIGUOUS · INFERRED: 49 edges (avg confidence: 0.8)
- Token cost: 20,377 input · 1,569 output

## Community Hubs (Navigation)
- [[_COMMUNITY_Gradle Dependency Accessors|Gradle Dependency Accessors]]
- [[_COMMUNITY_AppConfig & ConfigManager|AppConfig & ConfigManager]]
- [[_COMMUNITY_Trojan Config & Parsing|Trojan Config & Parsing]]
- [[_COMMUNITY_VPN Notification System|VPN Notification System]]
- [[_COMMUNITY_Gradle PluginBlock Accessors|Gradle PluginBlock Accessors]]
- [[_COMMUNITY_Library Accessors Overview|Library Accessors Overview]]
- [[_COMMUNITY_Library Accessors Detail|Library Accessors Detail]]
- [[_COMMUNITY_Domain Store|Domain Store]]
- [[_COMMUNITY_SingBoxManager|SingBoxManager]]
- [[_COMMUNITY_Domain Router & FakeDNS|Domain Router & FakeDNS]]
- [[_COMMUNITY_DomainFetcher|DomainFetcher]]
- [[_COMMUNITY_Domain List UI|Domain List UI]]
- [[_COMMUNITY_Status Card UI|Status Card UI]]
- [[_COMMUNITY_App Initialization|App Initialization]]
- [[_COMMUNITY_Build Scripts|Build Scripts]]
- [[_COMMUNITY_Proxy Toggle|Proxy Toggle]]
- [[_COMMUNITY_NoProxy Build Script|NoProxy Build Script]]
- [[_COMMUNITY_SingBox Binary|SingBox Binary]]

## God Nodes (most connected - your core abstractions)
1. `LibrariesForLibs` - 26 edges
2. `LibrariesForLibsInPluginsBlock` - 26 edges
3. `ConfigManager` - 14 edges
4. `DomainStore` - 14 edges
5. `SingBoxManager` - 10 edges
6. `VersionAccessors` - 9 edges
7. `ComposeLibraryAccessors` - 9 edges
8. `ComposeUiLibraryAccessors` - 9 edges
9. `VersionAccessors` - 9 edges
10. `ProxyManager` - 9 edges

## Surprising Connections (you probably didn't know these)
- `HaioBypassApp()` --calls--> `DomainStore`  [INFERRED]
  app/src/main/java/com/haio/bypass/ui/HaioBypassApp.kt → app/src/main/java/com/haio/bypass/domain/DomainStore.kt
- `HaioBypassApp()` --calls--> `DomainsScreen()`  [INFERRED]
  app/src/main/java/com/haio/bypass/ui/HaioBypassApp.kt → app/src/main/java/com/haio/bypass/ui/screens/DomainsScreen.kt
- `HaioBypassApp()` --calls--> `ConfigManager`  [INFERRED]
  app/src/main/java/com/haio/bypass/ui/HaioBypassApp.kt → app/src/main/java/com/haio/bypass/config/ConfigManager.kt
- `HaioBypassApp()` --calls--> `ConfigScreen()`  [INFERRED]
  app/src/main/java/com/haio/bypass/ui/HaioBypassApp.kt → app/src/main/java/com/haio/bypass/ui/screens/ConfigScreen.kt
- `HaioBypassApp()` --calls--> `MainScreen()`  [INFERRED]
  app/src/main/java/com/haio/bypass/ui/HaioBypassApp.kt → app/src/main/java/com/haio/bypass/ui/screens/MainScreen.kt

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **VPN Core Execution Flow** — app_src_main_java_com_haio_bypass_service_haiovpnservice, app_src_main_java_com_haio_bypass_dns_fakednsserver, app_src_main_java_com_haio_bypass_proxy_proxymanager [EXTRACTED 1.00]
- **Proxy Implementation Stack** — app_src_main_java_com_haio_bypass_proxy_xraymanager, app_src_main_java_com_haio_bypass_proxy_hevtunmanager, app_src_main_res_raw_libhev_socks5_tunnel_so, app_src_main_res_raw_xray_arm64_v8a [EXTRACTED 1.00]

## Communities (22 total, 3 thin omitted)

### Community 0 - "Gradle Dependency Accessors"
Cohesion: 0.05
Nodes (27): AbstractExternalDependencyFactory, DependencyNotationSupplier, ActivityLibraryAccessors, BundleAccessors, ComposeLibraryAccessors, ComposeUiLibraryAccessors, ComposeUiToolingLibraryAccessors, ComposeVersionAccessors (+19 more)

### Community 1 - "AppConfig & ConfigManager"
Cohesion: 0.05
Nodes (32): AppConfig, ConfigManager, SharedPreferences, StateFlow, Boolean, MainActivity, BootReceiver, Context (+24 more)

### Community 2 - "Trojan Config & Parsing"
Cohesion: 0.06
Nodes (25): android, TrojanConfig, String, TrojanUrlParser, HevTunManager, Int, Job, ProxyManager (+17 more)

### Community 3 - "VPN Notification System"
Cohesion: 0.15
Nodes (18): Context, VpnNotification, Deprecated, ActivityLibraryAccessors, ComposeLibraryAccessors, ComposeUiLibraryAccessors, ComposeUiToolingLibraryAccessors, CoreLibraryAccessors (+10 more)

### Community 4 - "Gradle PluginBlock Accessors"
Cohesion: 0.14
Nodes (15): BundleFactory, BundleAccessors, ComposeVersionAccessors, CapabilityNotationParser, ComposeVersionAccessors, DefaultVersionCatalog, ImmutableAttributesFactory, Inject (+7 more)

### Community 5 - "Library Accessors Overview"
Cohesion: 0.12
Nodes (12): ActivityLibraryAccessors, BundleAccessors, ComposeLibraryAccessors, CoreLibraryAccessors, KotlinxLibraryAccessors, LifecycleLibraryAccessors, NavigationLibraryAccessors, NonNullApi (+4 more)

### Community 6 - "Library Accessors Detail"
Cohesion: 0.13
Nodes (12): ActivityLibraryAccessors, BundleAccessors, ComposeLibraryAccessors, CoreLibraryAccessors, KotlinxLibraryAccessors, LifecycleLibraryAccessors, NavigationLibraryAccessors, NonNullApi (+4 more)

### Community 7 - "Domain Store"
Cohesion: 0.17
Nodes (11): DomainStore, Boolean, Int, List, SharedPreferences, StateFlow, String, DomainItem() (+3 more)

### Community 8 - "SingBoxManager"
Cohesion: 0.22
Nodes (7): Boolean, Int, Job, String, SingBoxManager, File, Process

### Community 9 - "Domain Router & FakeDNS"
Cohesion: 0.29
Nodes (6): FakeDnsServer, DomainRouter, Boolean, Int, List, String

### Community 10 - "DomainFetcher"
Cohesion: 0.43
Nodes (4): DomainFetcher, Boolean, List, String

### Community 11 - "Domain List UI"
Cohesion: 0.47
Nodes (5): DomainItem(), DomainList(), List, Modifier, String

### Community 12 - "Status Card UI"
Cohesion: 0.47
Nodes (5): InfoRow(), Int, Modifier, String, StatusCard()

### Community 14 - "Build Scripts"
Cohesion: 0.40
Nodes (4): ANDROID_HOME, GRADLE_OPTS, SDK_ROOT, build.sh script

### Community 15 - "Proxy Toggle"
Cohesion: 0.50
Nodes (3): Boolean, Modifier, ProxyToggle()

## Knowledge Gaps
- **7 isolated node(s):** `ProxyState`, `build.sh script`, `ANDROID_HOME`, `SDK_ROOT`, `GRADLE_OPTS` (+2 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **3 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `LibrariesForLibs` connect `Library Accessors Overview` to `Gradle Dependency Accessors`?**
  _High betweenness centrality (0.056) - this node is a cross-community bridge._
- **Why does `ConfigManager` connect `AppConfig & ConfigManager` to `Trojan Config & Parsing`?**
  _High betweenness centrality (0.046) - this node is a cross-community bridge._
- **Why does `LibrariesForLibsInPluginsBlock` connect `Library Accessors Detail` to `Gradle Dependency Accessors`, `VPN Notification System`, `Gradle PluginBlock Accessors`?**
  _High betweenness centrality (0.044) - this node is a cross-community bridge._
- **What connects `ProxyState`, `build.sh script`, `ANDROID_HOME` to the rest of the system?**
  _7 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Gradle Dependency Accessors` be split into smaller, more focused modules?**
  _Cohesion score 0.05213089802130898 - nodes in this community are weakly interconnected._
- **Should `AppConfig & ConfigManager` be split into smaller, more focused modules?**
  _Cohesion score 0.054693877551020405 - nodes in this community are weakly interconnected._
- **Should `Trojan Config & Parsing` be split into smaller, more focused modules?**
  _Cohesion score 0.05603864734299517 - nodes in this community are weakly interconnected._