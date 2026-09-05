# Drizz for Android — drizz.li 的原生 Android 移植

[![Build Android APK](https://github.com/SkyForest233/weather-web-1/actions/workflows/android-build.yml/badge.svg)](https://github.com/SkyForest233/weather-web-1/actions/workflows/android-build.yml)

[drizz.li](https://drizz.li/)([open-meteo/drizz.li](https://github.com/open-meteo/drizz.li),AGPL-3.0)是
Open-Meteo 官方的"快、免费、不废话"天气网站。本项目把它**完整移植为原生 Android 应用**:

- **Kotlin + Jetpack Compose**,设计系统 **Material 3 / Material You**
  (依据 [hamen/material-3-skill](https://github.com/hamen/material-3-skill),已安装于 `.claude/skills/material-3/`);
- 五个 Open-Meteo 数据端点全接入:预报 / 集合 / 季节 / 档案 / 地理编码;
- 六大页面全移植:周预报 · 模式对比 · 14 天集合 · 季节展望 · 历史档案(1940+)· 交互地图;
- 自研 Canvas meteogram 引擎:昼夜光带、温度色标线、极值标注、天气 pictogram、风向箭头、
  云量分层带、捏合缩放、十字线读数;
- "in words" 逐日整句叙述(4 语言模板 + 逐日稳定随机)、meteoalarm 式日预警、
  可定制小时表与 meteogram 面板、附近城市(内置 2.4 万城市数据,离线可用)、
  38 个预报模型 + 15 个集合模型目录、5 语言(en/de/es/fr/it,由网站消息文件自动移植)。

## 文档

| 文档 | 内容 |
|---|---|
| [`docs/01-drizzli-拆解分析.md`](docs/01-drizzli-拆解分析.md) | drizz.li 源码级完整拆解:架构、6 页面、5 端点、领域算法、图表引擎、状态管理、品牌视觉 |
| [`docs/02-Android-移植方案.md`](docs/02-Android-移植方案.md) | 功能逐一映射表、Material 3 设计落地、数据工程细节、构建指南、有意取舍 |

## 构建

**不需要电脑**：用 GitHub Actions 云端构建 APK。首次只需用手机浏览器添加一个工作流文件(仓库已备好内容,复制粘贴即可):

1. 打开 [这个链接](https://github.com/SkyForest233/weather-web-1/new/arena%2F01a06fa8-weather-web-1?filename=.github/workflows/android-build.yml)(新建文件页,文件名已预填 `.github/workflows/android-build.yml`);
2. 再打开 [工作流内容](https://raw.githubusercontent.com/SkyForest233/weather-web-1/arena%2F01a06fa8-weather-web-1/ci/android-build.yml),全选复制,粘贴到编辑区;
3. 点 **Commit changes** 提交。GitHub Actions 会立刻开始自动构建;
4. 构建完成后(约 5–10 分钟),进入 Actions 该次运行 → **Artifacts** 下载 `Drizz-debug-APK`(zip 内含 apk),手机上解压后点开安装(允许"未知来源");
5. 以后每次推送代码都会自动构建;在 Actions 页面手动 **Run workflow** 并勾选 **Create release**,APK 还会发布到 **Releases** 页,直接下载 `.apk` 即装。

本地/电脑构建(可选)：

```bash
cd android
./gradlew :app:assembleDebug
# APK → android/app/build/outputs/apk/debug/app-debug.apk
```

运行时只需访问 Open-Meteo 公共 API,无自建后端、无账号、无追踪。

## 工程结构

```
android/                     Android Studio 工程(Gradle 8.10.2 · AGP 8.7.3 · Kotlin 2.0.21)
└── app/src/main/
    ├── assets/data/cities.json     附近城市数据(GeoNames CC-BY-4.0,pop≥2万)
    ├── res/values*/strings.xml     408 键 × 5 语言(scripts/port_strings.py 生成)
    └── java/li/drizz/app/
        ├── charts/                 TimeChart 引擎 + 天气字形
        ├── data/                   Open-Meteo 客户端、仓库、DataStore 设置、城市数据
        ├── domain/                 天气码/单位/预警/叙述/模式目录/变量注册表/色标
        ├── ui/                     AppShell + 六大页面 + 搜索/设置
        └── ui/theme/               M3 主题(动态色 + 品牌橙种子方案)
scripts/                     资源移植脚本(字符串、城市数据)
.claude/skills/material-3/   已安装的 Material 3 设计 skill
```

## 许可与署名

- 代码:沿用上游 **AGPL-3.0**;
- 数据:Open-Meteo(CC BY 4.0,数据源含 DWD ICON、NOAA GFS/HRRR、ECMWF、Météo-France、UKMO、KNMI、DMI、MET Norway、MeteoSwiss、JMA、CMA、CHMI、GEM、ItaliaMeteo)与 GeoNames(CC BY 4.0);
- 应用图标沿用 drizz.li favicon 雨伞图形。
