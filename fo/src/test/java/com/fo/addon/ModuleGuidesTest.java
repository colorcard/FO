package com.fo.addon;

import com.fo.addon.guide.ModuleGuides;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 「使用说明」文本回归：9 个模块都必须有说明，且关键前置条件不能被误删。
 *
 * <p>这些文本会直接显示在模块设置页底部，属于用户可见内容；这里锁住结构（标题/正文）
 * 和每个模块最关键的几条事实，防止后续重构时把说明清空或改错。
 */
class ModuleGuidesTest {

    private static Map<String, String[]> all() {
        Map<String, String[]> guides = new LinkedHashMap<>();
        guides.put("鞘翅采集", ModuleGuides.elytraCollector());
        guides.put("自动种树", ModuleGuides.autoTree());
        guides.put("自动扔垃圾", ModuleGuides.autoTrash());
        guides.put("自动LogPlus", ModuleGuides.autoLog());
        guides.put("自动挖沙", ModuleGuides.autoMineSand());
        guides.put("自动挖矿", ModuleGuides.autoMining());
        guides.put("杀戮光环", ModuleGuides.killAura());
        guides.put("古城战利品搜索", ModuleGuides.ancientCitySearch());
        guides.put("自动搬运", ModuleGuides.stashMover());
        return guides;
    }

    @Test
    void everyModuleHasStructuredGuide() {
        for (Map.Entry<String, String[]> entry : all().entrySet()) {
            String module = entry.getKey();
            String[] guide = entry.getValue();

            assertTrue(guide.length >= 4, module + " 的说明太短");
            assertTrue(guide[0].startsWith("# "), module + " 的第一行必须是分组标题");
            assertTrue(guide.length >= 3, module + " 至少要有 3 段");

            int headers = 0;
            for (String line : guide) {
                assertNotNull(line, module + " 说明包含 null");
                assertFalse(line.isBlank(), module + " 说明包含空行");
                assertFalse(line.contains("TODO"), module + " 说明残留 TODO");
                if (line.startsWith("# ")) {
                    headers++;
                    assertTrue(line.length() > 2, module + " 有空标题");
                }
            }
            assertTrue(headers >= 3, module + " 至少要有 3 个分组标题");
        }
    }

    @Test
    void elytraGuideKeepsCriticalWarnings() {
        String text = joined(ModuleGuides.elytraCollector());
        assertContains(text, "末地", "鞘翅采集必须说明只能在末地使用");
        assertContains(text, "末影箱", "鞘翅采集必须说明末影箱用途");
        assertContains(text, "潜影盒", "鞘翅采集必须说明潜影盒用途");
        assertContains(text, "联机必须手动填服务器种子", "鞘翅采集必须说明联机种子");
        assertContains(text, "低Y退出", "鞘翅采集必须说明低Y退出会断线");
        assertContains(text, "自动扔垃圾", "鞘翅采集必须说明联动");
    }

    @Test
    void miningAndSandGuidesKeepStartupRequirements() {
        String mining = joined(ModuleGuides.autoMining());
        assertContains(mining, "时运镐", "自动挖矿缺少时运镐要求");
        assertContains(mining, "精准采集镐", "自动挖矿缺少精准采集镐要求");
        assertContains(mining, "末影箱", "自动挖矿缺少末影箱要求");
        assertContains(mining, "工作台", "自动挖矿缺少工作台要求");
        assertContains(mining, "主世界", "自动挖矿缺少维度说明");
        assertContains(mining, "下界", "自动挖矿缺少维度说明");

        String sand = joined(ModuleGuides.autoMineSand());
        assertContains(sand, "Baritone", "自动挖沙缺少 Baritone 要求");
        assertContains(sand, "32 格", "自动挖沙缺少潜影盒搜索范围");
        assertContains(sand, "FO补给", "自动挖沙缺少补给盒关键词");
        assertContains(sand, "存沙", "自动挖沙缺少存沙说明");
    }

    @Test
    void otherGuidesKeepKeyFacts() {
        assertContains(joined(ModuleGuides.autoTree()), "快捷栏", "自动种树缺少快捷栏要求");
        assertContains(joined(ModuleGuides.autoTree()), "骨粉", "自动种树缺少骨粉说明");

        assertContains(joined(ModuleGuides.autoTrash()), "白名单", "自动扔垃圾缺少名单模式说明");
        assertContains(joined(ModuleGuides.autoTrash()), "排除快捷栏", "自动扔垃圾缺少快捷栏保护说明");

        assertContains(joined(ModuleGuides.autoLog()), "断开服务器", "自动LogPlus 必须说明是断线而不是停模块");
        assertContains(joined(ModuleGuides.autoLog()), "默认全部关闭", "自动LogPlus 必须说明默认关闭");

        assertContains(joined(ModuleGuides.killAura()), "忽略被动生物", "杀戮光环缺少安全过滤说明");
        assertContains(joined(ModuleGuides.killAura()), "服务器规则", "杀戮光环缺少服务器规则提醒");

        assertContains(joined(ModuleGuides.ancientCitySearch()), "应用种子", "古城搜索缺少应用种子说明");
        assertContains(joined(ModuleGuides.ancientCitySearch()), "游戏版本", "古城搜索缺少游戏版本说明");
        assertContains(joined(ModuleGuides.ancientCitySearch()), "区块", "古城搜索缺少半径单位说明");

        assertContains(joined(ModuleGuides.stashMover()), "箱子搜索距离", "自动搬运缺少出生点距离要求");
        assertContains(joined(ModuleGuides.stashMover()), "docs/STASHMOVER.md", "自动搬运缺少详细文档入口");
    }

    private static String joined(String[] lines) {
        return String.join("\n", lines);
    }

    private static void assertContains(String text, String needle, String message) {
        assertTrue(text.contains(needle), message + "（缺少「" + needle + "」）");
    }
}
