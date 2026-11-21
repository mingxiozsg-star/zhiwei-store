# 知微应用商店 v1.0.0

把官方商店、第三方商店和开源平台的下载源汇聚到一处：一次搜索，看全部来源、全部版本。

## 更新要点

- **多源聚合搜索**：F-Droid、IzzyOnDroid、GitHub Releases、Aptoide、APKPure、APKCombo、酷安、应用宝，八个源并发查询，同一个应用自动合并成一条，详情页可切换来源与版本。
- **开源优先，国内可达**：F-Droid 索引走本地 SQLite，首次同步后可离线搜索；清华 / 中科大 / 北外 / 阿里云四个镜像自动测速选最快，官方源直连超时会自动回落。
- **GitHub Releases 也能装**：直接解析仓库 Releases 里的 APK 资产，识别 arm64 / armv7 / x86 架构，每条资产给「官方直连」和加速镜像两条线路。
- **内置下载器**：进度可观察、可取消、可重试，仅 Wi-Fi 下载开关，完成后自动唤起安装器。
- **跨源合并开关**：不想要合并结果时可以拆开，按源分别查看。

## 下载

正式版由 GitHub Actions 构建，从本页 Assets 下载 `app-release.apk` 安装即可。

## 官方渠道

- 邮箱：ailixiya@qycxkjit.wecom.work
- QQ 群：1131325287

---

# Zhiwei Store v1.0.0

One app store that aggregates download sources from official stores, third-party stores and open-source platforms — one search, every source, every version.

## Highlights

- **Aggregated search across 8 sources**: F-Droid, IzzyOnDroid, GitHub Releases, Aptoide, APKPure, APKCombo, Coolapk, Tencent MyApp. Results are queried concurrently and merged by package name, with a source switcher on the detail page.
- **Open source first, reachable in mainland China**: F-Droid index is stored in local SQLite for offline search; four China mirrors are speed-tested automatically with fallback.
- **GitHub Releases as a source**: parses APK assets from releases, detects arm64 / armv7 / x86, and offers both direct and mirror download links.
- **Built-in downloader**: observable progress, cancel, retry, Wi-Fi-only mode, auto install after download.
- **Merge toggle**: turn off cross-source merging to browse results source by source.

## Download

Release builds are produced by GitHub Actions. Grab `app-release.apk` from the Assets section.

## Contact

- Email: ailixiya@qycxkjit.wecom.work
- QQ group: 1131325287