package com.xiaoyu.aiaigent.lore;

import java.util.List;

/**
 * 抽取出的角色条目：标准名 + 别名。
 * 供章节入库流水线维护 character 表，驱动「别名归一」「角色词表」「角色精确查询」。
 */
public record CharacterRef(
        String name,          // 角色标准名（首次出现用本名，后续用标准名）
        List<String> aliases  // 该角色的其他称呼（如「主角」「林玄」），可空
) {
    public CharacterRef {
        aliases = aliases == null ? List.of() : aliases;
    }
}
