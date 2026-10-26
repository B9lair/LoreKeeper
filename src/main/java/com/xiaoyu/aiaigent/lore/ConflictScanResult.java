package com.xiaoyu.aiaigent.lore;

import java.util.List;

/**
 * ConflictScanService LLM 结构化输出的承载类型：{"items":[...]}。
 */
public record ConflictScanResult(List<ConflictScanItem> items) {
}
