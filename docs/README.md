<div align="center">
  <img src="./hina.jpg" width="250" alt="Hina" />

  # FuckCustomTab
  **去他妈的应用内浏览器**
  <br>
[![LSPosed](https://img.shields.io/badge/LSPosed-Supported-brightgreen)](https://github.com/Xposed-Modules-Repo/via.fuckcustomtab.frisk)
[![下载次数](https://img.shields.io/badge/Downloads-1000%2B-blue)](https://github.com/frisk1127/FuckCustomTab/releases)
</div>

---

## 作用
阻止浏览器跳转链接时使用应用内浏览器（CustomTab）

理论上支持所有使用 CustomTab 的浏览器

### 已测试
| 浏览器 | 版本 |
| --- | --- |
| Via | 6.9.0 |
| Edge | 143.0.3650.139 |
| Chrome | 143.0.7499.192 |

## 如果不适配你的浏览器

如果本模块没有适配你使用的浏览器，欢迎 [提交 Issue](https://github.com/frisk1127/FuckCustomTab/issues)，我会尽快处理。

<details>
<summary><strong>制作原因</strong></summary>

<p></p>

<p>
Via 在版本 6.9.0 适配了链接应用内打开 导致想要使用浏览器内置的下载管理器等页面需要再点击一个按钮跳转到 Via 非常繁琐
</p>

<p>
本模块用于移除此特性。
</p>

</details>

## 效果

| 使用前 | 使用后 |
| --- | --- |
| <img src="./before.jpg" width="380" alt="Before" /> | <img src="./after.jpg" width="380" alt="After" /> |
