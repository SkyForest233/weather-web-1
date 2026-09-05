# drizz.li 完整拆解分析

> 分析对象:<https://drizz.li/> · 源码:<https://github.com/open-meteo/drizz.li>
> (commit `467665c`,2026-08-31 · AGPL-3.0 License)

---

## 1. 项目定位

drizz.li 是 Open-Meteo 官方出品的**纯静态、无账号、无广告、无追踪**的天气网站:

- 前端完全静态打包(SvelteKit 5 + TypeScript + Tailwind 4 + shadcn-svelte),浏览器直接从 Open-Meteo API 拉取数据渲染;
- 唯一的服务端代码是一个 Cloudflare Worker(`worker/`),只提供 `/api/geo`:返回 Cloudflare 从请求 IP 推断的近似位置,让首次访问直接打开用户所在城市;
- 数据传输用 **FlatBuffers(protobuf)** 而非 JSON(Open-Meteo SDK `openmeteo`);
- 可视化是**自研 Canvas 图表引擎**(`src/lib/charts`);
- i18n 用 Paraglide JS,语言在 URL 路径中(en/de/es/fr/it 共 **408 条消息键**);
- 测试用 Vitest + Playwright browser project。

## 2. 信息架构(6 个页面)

| 路由 | 功能 | 关键组件 |
|---|---|---|
| `/weather/week/[location]` | 7 天(可扩展 15 天、回看过去 3 天)预报 | DailyCards/DailyStripSticky、DaySummary、HourlyTable、MeteogramCharts、ModelSelector、NearbyCities、VariableSidebar |
| `/weather/compare/[location]` | 同一地点多模式对比 | ComparisonSelectionPanel、ModelPictogramTimeline |
| `/weather/14-day/[location]` | 14 天集合预报(成员离散度) | ensemble API 折线族 |
| `/weather/seasonal/[location]` | 未来数月月度展望 vs 气候正常值 | SeasonalMonths、SeasonalCharts、outlook.ts |
| `/weather/historical/[location]` | 1940 年至今再分析档案 + 气候正常值对比 | DateRangeControls、HistoricalDaily、HistoricalMeteograms |
| `/weather/maps/` | Open-Meteo 交互地图(iframe 嵌入 maps.open-meteo.com) | hash 双向管道 + 模型→domain 映射 |

## 3. 数据层(`src/lib/services/weather.ts`,1350 行)

所有请求走 5 个 Open-Meteo 端点:

| 端点 | 用途 | 关键参数 |
|---|---|---|
| `api.open-meteo.com/v1/forecast` | 周预报 / 多模式对比 / 附近城市批量 | `hourly`,`daily`(16 项),`models`,`past_days`,`forecast_days`,`timezone=auto` |
| `ensemble-api.open-meteo.com/v1/ensemble` | 14 天集合 | 每变量返回 N 个成员 |
| `archive-api.open-meteo.com/v1/archive` | ERA5/CERRA 历史档案(1940+) | `start_date`,`end_date`,`models` |
| `seasonal-api.open-meteo.com/v1/seasonal` | SEAS5 季节集合(≤216 天) | `daily` 6 变量 × 成员 |
| `geocoding-api.open-meteo.com/v1/search|get` | 地点搜索 / 按 GeoNames id 反查 | `language` 跟随站点语言 |

核心结构:

- **WeekForecast**:hourly 22 变量池 + daily 16 变量(含 `sunshine_duration`、`daylight_duration`、`uv_index_max`、`moonrise/moonset/moon_phase`);`buildDaylightBands()` 生成昼夜光带。
- **ModelCompare**:一次请求带 `models=a,b,c`,FlatBuffers 返回逐模式响应;JSON 路径下 Android 端等价实现为并行逐模式请求。输出 `ModelSeriesData[]` + `hourlyFlat`(`temperature_2m_icon_seamless` 扁平键)。
- **Ensemble**:成员按 `var0_member0…var0_memberM-1` 排布,客户端折叠出 average/min/max。
- **ClimateNormals**:一次性请求 1991–2020 全档案,按"年积日 1..366"聚合再做 **±7 天环形平滑**,得到逐日气候正常值。
- **Seasonal**:成员折叠出 mean/min/max/p25/p75,并按模型自身地平线裁掉尾部空白。
- **fetchNearbyDaily**:一次请求带多个逗号分隔坐标,按序返回多响应(刻意用 best_match,保证全球覆盖)。
- **humanizeWeatherError()**:把网络/无数据/参数类错误映射为"标题+行动建议+可折叠技术详情"。

## 4. 领域算法(移植的核心资产)

| 文件 | 算法 |
|---|---|
| `utils/weather-codes.ts` | WMO 码 → 图标族映射(仅 Open-Meteo 实际会发的 30 个码);`computeDayNightWeatherCodes()` 从逐小时码按日出日落切分出白天/夜间主导天况,修复 daily `weather_code` 数值 max 的已知缺陷(Open-Meteo issue #228) |
| `significance.ts` | 日卡片"灰显阈值"(日照占白昼 ≥10% 才显示、降水痕量线)与 **meteoalarm 式预警**:阵风 80/110 km/h、降水 30/60 mm、高温 40/45 °C、低温 -20/-30 °C → 橙色/红色三角 |
| `forecast-text.ts` | **"in words" 逐日叙述**:把逐小时数据折算成 overnight/morning/afternoon/evening 四段天况 → 合并相邻同况段 → 整句模板(SKY_ALL/TWO/THREE、TEMP、PRECIP_WINDOW/SPREAD/CHANCE/DRY、WIND_DIR/GUSTS、CALM、UV 全部本地化整句);FNV-1a 哈希做**逐日稳定伪随机**,同一天措辞稳定、不同天措辞轮换(含语序轮换);月相名/照亮比/UV 等级(WHO 分档) |
| `variables.ts` | **可定制 meteogram 变量注册表**:22 个变量(line/bar/cloudband/pictogram/windArrows、色温线、渐变填充、极值标注、右轴预设 0-100 等),`neededHourlyApiVars()` 只请求实际展示的变量 |
| `utils/color-scales.ts` | 温度色标:weather-map-layer 的 42 断点色带(°C),线性插值 |
| `comparison.ts` | 对比页:色盲安全 16 色模式调色板(选择顺序优先)、降水一致性(中位数/min/max + wetCount)、模式均值、风向有效性过滤、8 方位方位角 |
| `outlook.ts` | 季节月度展望:月度均值/异常值(仅用预报实际覆盖的天数对比正常值)、降水占正常值比例、湿日数、"多少比例成员认为偏暖" |
| `build-cities.mjs` | 附近城市:GeoNames cities500(pop≥2 万)切 10°×10° 瓦片,近圈 400 km + 远圈 1500 km(>30 万人口);选取算法 = 人口/(1+(d/halfWeight)²) 距离阻尼评分 + "同一城市"半径(人口越大半径越大)+ 最小间距防都市圈填榜 |

## 5. 图表引擎(`src/lib/charts`)

- `CanvasChart.svelte`:canvas 2D,特性包括——昼夜光带背景、本地午夜日分隔线+星期标签、温度色标描边(前景线)+色温渐变面积、极小值/极大值标注、天气图标 pictogram 行、风向箭头行、云量分层带(高/中/低按真实垂直顺序堆叠、越近地面越深)、降水柱、右轴预设(降水概率 0-100)、"drag 或 Ctrl+滚轮缩放"、双击重置、默认时间范围(auto=手机 3 天/宽屏全部)。
- 布局由 `storedChartLayout`(`{id, variables[]}[]` 面板数组)持久化,用户可在面板间拖拽变量、增删面板。

## 6. 状态与设置(`src/lib/stores/settings.ts`,svelte-persisted-store)

| 键 | 内容 |
|---|---|
| `stored_location` | 当前地点(GeoNames 完整记录);recents 8 条、favorites 24 条 |
| `theme` | system/light/dark |
| `selected_model` | 预报模式(全站共享);`ensemble_model`、`archive_model`、`seasonal_model` 分页面独立 |
| `variable_prefs` | 小时表行开关 + 行顺序;`chart_layout_v1` meteogram 面板;`chart_range_v1` 默认范围;`hourly_interval` 1h/3h |
| `units_v1` | 温度 °C/°F、风速 km/h·m/s·mph·kn、降水 mm/in |
| `nearby_open_v1` | 附近城市面板开合(决定是否付费拉数据) |

## 7. 模式目录(`options.ts`)

- 预报模式 **15 组 38 个**:Automatic&seamless(best_match、ICON/GFS/MF/UKMO/KNMI/DMI/METno/MeteoSwiss/CHMI/JMA/GEM seamless)、ECMWF(IFS/AIFS)、DWD、NOAA(GFS/HRRR/GraphCast/AIGFS/HGEFS/NBM/NAM)、Météo-France、UKMO、KNMI、DMI、MET Norway、MeteoSwiss、JMA、CMA、CHMI、GEM、ItaliaMeteo;每个模式带网格分辨率与更新频率,选中区域模式而当地无数据时给"域内城市"逃生门。
- 集合模式 **7 组 15 个**(ICON EPS、GEFS、ECMWF EPS、GEM、UKMO、MeteoSwiss、Google WeatherNext 2)。
- 档案模式 ERA5/ERA5-Land/CERRA/ECMWF IFS;季节模式 SEAS5。
- 地图页 `maps-domain.ts` 把 drizz.li 模式 id 翻译成 maps.open-meteo.com 的 domain 候选列表(与地图握手时按宣告域尝试)。

## 8. UI/UX 结构

- **桌面**:顶栏(logo、地点 pill、搜索、设置菜单[主题/语言/单位]、导航) + 左侧 VariableSidebar;**移动**:`weather-nav` 底部导航(Week/Compare/14-day + More 溢出菜单)。
- **周页**:日卡片条(选中态、预警三角、今日/明日标签)→ DaySummary(整句叙述 + 日出日落/白昼/日照进度条/UV/月相)→ 小时表(行可定制可重排、1h/3h、now 列高亮)→ meteogram 面板组 → 附近城市。
- **对比页**:模型多选面板(**Apply/Discard 待应用语义**、家族分组、>N 警告)、逐变量图表、模式均值开关、天气码 pictogram 时间线、降水一致性图。
- 搜索框:防抖 300ms、收藏(星标)与最近分组、按人口排序。
- 无障碍:aria 标签全覆盖;错误面板:标题+建议+可折叠原始报错+重试。

## 9. 品牌视觉

- 主色 `oklch(0.65 0.17 55)` ≈ **#DB6C00**(暖橙),浅背景 `#FAF9F7`、深背景 `oklch(0.17 0.015 60)` ≈ #1A120C(暖棕黑);
- favicon:浅蓝天空渐变底 + 橙色雨伞 + 蓝色雨滴;
- 温度色标沿用 weather-map-layer(靛→蓝→绿→黄→红)。

## 10. 许可

AGPL-3.0。数据来源 Open-Meteo(含各气象机构模式)与 GeoNames(CC BY 4.0)。
