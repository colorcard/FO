package com.fo.addon.gui;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
import meteordevelopment.meteorclient.utils.Utils;

/**
 * 模块设置页底部的只读「使用说明」区块。
 *
 * <p>Meteor 的 {@code ModuleScreen} 会在设置之后调用 {@code Module.getWidget(theme)}，
 * 所以这里返回一个可折叠的 {@link WSection} 即可，不需要改 Meteor 本身。
 * 以 {@code "# "} 开头的行按分组标题渲染，其余行按正文渲染（超宽自动换行）。
 */
public final class ModuleGuide {
    private ModuleGuide() {
    }

    public static WSection section(GuiTheme theme, String... lines) {
        WSection section = theme.section("使用说明", true);
        // 与 ModuleScreen 的模块简介同宽（窗口一半），再留出区块左右 padding。
        double width = Math.max(120.0, Utils.getWindowWidth() / 2.0 - 24.0);
        for (String line : lines) {
            if (line.startsWith("# ")) {
                section.add(theme.label(line.substring(2), true, width)).expandX().widget();
            } else {
                section.add(theme.label(line, width)).expandX().widget();
            }
        }
        return section;
    }
}
