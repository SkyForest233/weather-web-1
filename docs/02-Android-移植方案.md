# drizz.li → 原生 Android 移植方案

> 目标:把 drizz.li 的**全部功能**移植为原生 Android 应用,设计遵循 **Material Design 3(Material You)**。
> 设计规范依据:`.claude/skills/material-3/`(hamen/material-3-skill,已安装到本仓库)。

## 1. 技术选型

| 维度 | drizz.li(网站) | Android 移植 |
|---|---|---|
| 框架 | SvelteKit 5 | **Kotlin + Jetpack Compose + Material 3**(skill 指定的 Compose-first 路线) |
| 数据传输 | FlatBuffers SDK | **OkHttp + kotlinx.serialization(JSON API)** —— 同一数据、同一参数,移动端 JSON 更简单,无 FlatBuffers 工具链负担 |
| 状态 | svelte-persisted-store | **DataStore Preferences**(JSON 编码复杂对象)+ StateFlow |
| 图表 | 自研 Canvas 引擎 | **Compose Canvas 自研引擎**(1:1 移植特性集) |
| i18n | Paraglide JS(messages/*.json) | **Android strings.xml × 5 语言**(脚本从 messages/*.json 自动移植 408 键 × en/de/es/fr/it) |
| 架构 | — | MVVM:ViewModel + Repository + 单例 AppGraph(零 DI 框架依赖) |

版本基线:AGP 8.7.3 · Kotlin 2.0.21 · Compose BOM 2024.12.01 · minSdk 26 · target/compile 35。

## 2. 功能映射表(完整)

| drizz.li 功能 | Android 实现 | 文件 |
|---|---|---|
| 周预报(7 天→15 天,过去 3 天) | WeekScreen + WeekViewModel(forecastDays/pastDays 状态、签名去重) | `ui/week/` |
| 日卡片条/粘性日条 | LazyRow DayCard:今明昨标签、预警三角(阵风/降水/高低温阈值完整移植)、选中态、"+3 past/15 days" chips | `WeekScreen.kt` |
| "in words" 整句叙述 | Narrative.buildDayNarrative:四时段→同况合并→1/2/3+ 句型模板、FNV 逐日种子、语序轮换、UV≥6 收尾句 | `domain/Narrative.kt` |
| 日出/白昼/日照进度条/月相/UV | DaySummaryCard(sunshine/daylight 比、月相名+照亮 %、WHO UV 分档色) | `WeekScreen.kt` |
| 小时表(行开关+重排、1h/3h、now 列) | HourlyTableSection:转置表(标签列+LazyRow 时列)、行序持久化、`GlobalScope`→scope、now 列 secondaryContainer 高亮 | `WeekCharts.kt` |
| 可定制 meteogram(面板间拖变量) | ChartLayoutSheet:面板增删、变量在面板间移动/移除/追加,DataStore 持久化 JSON | `WeekCharts.kt` |
| Meteogram 图表引擎 | TimeChart:昼夜光带、日分隔+标签、温度色标线+渐变面积、极值标注、pictogram 行、风向箭头行、云量分层带(高/中/低堆叠)、降水柱、POP 右轴 0-100、now 虚线、捏合/拖拽缩放、双击重置、按住显示十字线+数值气泡 | `charts/TimeChart.kt`、`charts/WeatherGlyphs.kt` |
| 变量注册表(22 变量) | ChartVariables.ALL 1:1(键名、配色、线宽、虚线、色标、渐变、极值、marker、右轴、m→km 变换) | `domain/ChartVariables.kt` |
| `neededHourlyApiVars` 按需请求 | 同名移植(表行+图表布局并集 + weather_code 兜底) | `domain/ChartVariables.kt` |
| 模式选择器(15 组 38 模式) | ModelCatalog.forecastGroups + 分组对话框(分辨率/更新频率副标题)、无数据时"切回 best_match + 域内城市"逃生门 | `domain/ModelCatalog.kt` |
| 附近城市 | CitiesRepository:打包 cities500(pop≥2 万,2.47 万城市,1MB asset),距离阻尼评分/同城半径/最小间距算法完整移植;fetchNearbyDaily 多坐标批量;选中日显示最高/最低 | `data/CitiesRepository.kt` |
| 模式对比 | CompareScreen:变量 chips、模型多选 Apply/Discard 语义、色盲安全调色板(选择顺序优先)、均值开关(虚线参考线)、降水一致性(中位线+min/max 带)、天气码 pictogram 时间线、风向文本行 | `ui/compare/` |
| 14 天集合预报 | EnsembleScreen:7 组 15 集合模式、成员细线(开关)+均值粗线+min/max 带、成员数标注、裁剪说明 | `ui/ensemble/` |
| 季节展望 | SeasonalScreen:月度卡片(异常值色块、降水占正常值 %、湿日数、偏暖成员占比)+ p25-p75/min-max 带图 + 1991-2020 正常值参考线(±7 天环形平滑的 ClimateNormals) | `ui/seasonal/`、`data/WeatherRepository.kt` |
| 历史档案(1940+) | HistoricalScreen:起止日期选择器(1940→今天-5d 可选域)、快捷范围(30 天/去年同月)、ERA5/ERA5-Land/CERRA 模型下拉、逐日常量统计展开、温度色标线+正常值虚线 | `ui/historical/` |
| 地图页 | MapsScreen:WebView 加载 maps.open-meteo.com(地点 hash 种子),模型→domain 候选表,外链浏览器 | `ui/maps/` |
| 地点搜索(防抖、收藏 24、最近 8) | LocationSearchSheet:250ms 防抖、geocoding search、收藏星标/删除最近、国旗 emoji(替代国旗 SVG) | `ui/search/` |
| 设置(主题/单位/模式记忆) | SettingsSheet:主题三态、**动态色开关(Material You)**、温度/风速/降水单位 SegmentedButton,全部 DataStore 持久化 | `ui/settings/`、`data/settings/SettingsRepository.kt` |
| 友好错误(标题+建议+详情+重试) | FriendlyError + humanizeError(网络/无数据/参数/通用四桶)+ ErrorPanel | `data/WeatherRepository.kt`、`ui/components/Common.kt` |
| i18n(en/de/es/fr/it) | `scripts/port_strings.py` → values{,-de,-es,-fr,-it}/strings.xml(408 键,`{x}`→`%n$s`) | `app/src/main/res/values*` |
| PWA 更新提示/Service Worker | 不适用(原生分发) | — |
| About/Privacy/Imprint 长文 | 设置页 About 摘要 + 数据署名 + 外链 | `ui/settings/` |

## 3. Material 3 设计(skill 落地)

依据 `SKILL.md` 与 references:

- **Color**:动态色优先(Android 12+ `dynamicDark/LightColorScheme`),可关;静态 scheme 以 drizz.li 品牌橙 `#DB6C00` 为种子构建完整 light/dark tonal 方案(含 5 级 surfaceContainer、inverse 系);图表数据墨水沿用网站固定调色板(跨主题可读),轴/网格/文字用 `colorScheme` 角色。
- **Navigation**:6 个目的地 → 按决策树:compact(手机)= NavigationBar 3 项(Week/Compare/14-day)+ "More" ModalBottomSheet(Seasonal/Historical/Maps),与网站移动导航一致;medium/expanded = NavigationRail 全 6 项。`calculateWindowSizeClass` 自适应。
- **Components**:TopAppBar(pinnableScrollBehavior)、NavigationBar/Rail、Cards(surfaceContainerLow,medium 圆角)、SegmentedButton(单位/时间范围/1h-3h)、FilterChip(变量/模型)、ModalBottomSheet(搜索/设置/自定义器/模式选择)、Switch、RadioButton、ExposedDropdownMenu(档案模式)、DatePicker(Dialog)、CircularProgressIndicator、HorizontalDivider。
- **Typography**:M3 默认字阶(Roboto),hero 温度用 displayMedium。
- **Shape/Motion**:卡片 medium(12dp)、底部抽屉 extra-large;页面切换 emphasized 时间曲线的 fade+轻位移;图表手势为物理驱动实时重绘。
- **Adaptive**:8dp 间距体系;内容最大宽度由 Surface 容器控制;rail 布局 ≥600dp 生效。
- **Accessibility**:图标 contentDescription、触达目标 ≥48dp(IconButton/ListItem)、错误面板双语文案、对比度由 tonal role 配对保证(onX 永远配 X)。

## 4. 数据与工程细节

- `timeformat=unixtime` + `timezone=auto`:统一用 epoch 秒 + IANA 时区渲染(`util/Dates.kt`),彻底规避字符串时区歧义;季节/附近批量响应按"响应自带 offset 位移后读 UTC 日期"取本地日键(与网站一致)。
- 对比页并行逐模式请求(每个模型一个 forecast 请求),时间轴取首个响应网格。
- 集合/季节响应的成员键 `*_memberNN` 泛型解析(不硬编码成员数,兼容各模式)。
- 气候正常值:1991-2020 单次大请求 → 年积日聚合 → 环形 ±7d 平滑;失败不影响主视图(降级为无正常值)。
- OkHttp 20MB 缓存;仓库层"签名去重"避免重复请求;错误统一 humanize。

## 5. 构建与运行

```bash
cd android
./gradlew :app:assembleDebug     # 产物: app/build/outputs/apk/debug/app-debug.apk
# 或用 Android Studio (Ladybug+) 打开 android/ 目录直接 Run
```

- 沙箱环境限制(仅放行 GitHub,Maven Central / Google Maven / Gradle CDN 均不可达),APK 编译需在本地/CI 完成;工程自带 Gradle Wrapper 8.10.2,任何装了 JDK 17+ 的机器 `./gradlew` 一条命令即可出包。
- App 运行时只需访问 Open-Meteo API(无任何自家后端);附近城市数据已内嵌,离线可算。

## 6. 与网站的已知差异(有意取舍)

1. FlatBuffers → JSON(数据一致,端点/参数/变量完全相同)。
2. 地图页由 iframe+postMessage 握手 → WebView 直载(选层在地图 UI 内完成,不回传 hash)。
3. 对比页多模型请求由"单请求多响应"→"并行单响应"(JSON API 行为差异),结果结构等价。
4. 下载图表 PNG 未移植(原生分享截图可替代);PWA 相关(安装横幅/版本轮询)不适用。
5. about/imprint/privacy 长文页未逐字移植,以 About 摘要+署名+外链代替。
6. 国旗用 emoji 渲染(替代 R2 国旗 SVG),天气图标用几何字形绘制(替代 icon font,随主题着色)。
