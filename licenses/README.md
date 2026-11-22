# 词库来源

四级、六级和高考词库直接由 [ECDICT 固定版本](https://github.com/skywind3000/ECDICT/tree/ab22ad36302cafc6349a050e2b60894855208055) 的 `ecdict.csv` 派生。

从 `tag` 字段筛选 `cet4`、`cet6` 和 `gk`，保留单词与中文释义，并转换为制表符分隔文本。三套词库分别包含 3,849、5,407 和 3,677 条记录。分类沿用上游标签，仅作词汇练习参考。

上游提供的 [MIT 许可全文](ECDICT-MIT.txt) 随仓库发布，并一并打入 APK。
