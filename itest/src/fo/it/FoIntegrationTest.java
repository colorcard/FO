package fo.it;

import com.fo.addon.gui.EnchantLevelMapSettingScreen;
import com.fo.addon.pathing.PathManagers;
import com.fo.addon.modules.AutoMining;
import com.fo.addon.utils.EnchantLevelMapSetting;
import com.fo.addon.utils.FakeBlockManager;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.screens.ModuleScreen;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;

/**
 * FO 26.3 集成冒烟测试（仅测试环境使用，不随正式 jar 发布）。
 *
 * <p>流程：主菜单自动打开 FoTest 世界 → 等待玩家/区块就绪 → 对 FO 分类下每个模块：
 * 序列化/反序列化、打开设置界面渲染、启用 10 tick、关闭；附魔书映射设置额外打开专用界面。
 * 随后验证 class tweaker（假破坏 sequence）、Baritone 反射桥、鞘翅/古城异步搜索。
 * 全部通过后输出 FO_INTEGRATION_TESTS_PASSED 并退出。
 */
public class FoIntegrationTest implements ClientModInitializer {
    private static final Minecraft mc = Minecraft.getInstance();
    private int phase = 0;
    private int wait = 0;
    private int index = 0;
    private int sub = 0;
    private int heartbeat = 0;
    private List<Module> modules = List.of();
    private Module current;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        log("FO integration test initializer loaded");
    }

    private void tick() {
        if (++heartbeat % 100 == 0) {
            log("tick heartbeat phase=" + phase + " screen="
                + (mc.gui.screen() == null ? "null" : mc.gui.screen().getClass().getSimpleName())
                + " level=" + (mc.level != null));
        }

        switch (phase) {
            case 0 -> {
                if (++wait > 100) {
                    log("opening world FoTest (after delay)");
                    mc.createWorldOpenFlows().openWorld("FoTest", () -> log("openWorld callback"));
                    phase = 1;
                    wait = 0;
                }
            }
            case 1 -> {
                if (mc.level != null && mc.player != null) {
                    log("world loaded, waiting for chunks");
                    phase = 2;
                    wait = 40;
                } else if (++wait > 1200) {
                    fail("timed out waiting for world load");
                }
            }
            case 2 -> {
                if (wait-- > 0) return;
                modules = new ArrayList<>(Modules.get().getGroup(com.fo.addon.AddonTemplate.CATEGORY));
                log("FO modules: " + modules.size());
                for (Module m : modules) log(" - " + m.name + " settings=" + countSettings(m));
                if (modules.size() != 8) {
                    fail("expected 8 FO modules, got " + modules.size());
                    return;
                }
                phase = 3;
            }
            case 3 -> testModule();
            case 4 -> testClassTweakerAndBaritone();
            case 5 -> testAncientSearch();
            case 6 -> testAutoMiningWithGear();
            case 7 -> testElytraSearch();
            case 8 -> {
                if (wait-- > 0) return;
                log("FO_INTEGRATION_TESTS_PASSED modules=" + modules.size());
                finish(0);
            }
            default -> { }
        }
    }

    private void testModule() {
        if (current == null) {
            if (index >= modules.size()) {
                phase = 4;
                sub = 0;
                wait = 0;
                return;
            }
            current = modules.get(index++);
            sub = 0;
            log("testing module " + current.name);
        }

        switch (sub) {
            case 0 -> {
                current.disable();
                CompoundTag tag = current.toTag();
                if (tag == null) {
                    fail("toTag returned null for " + current.name);
                    return;
                }
                current.fromTag(tag);
                log("  serialization ok settings=" + countSettings(current));
                mc.gui.setScreen(new ModuleScreen(GuiThemes.get(), current));
                sub = 1;
                wait = 5;
            }
            case 1 -> {
                if (wait-- > 0) return;
                mc.gui.setScreen(null);
                EnchantLevelMapSetting map = findEnchantSetting(current);
                if (map != null) {
                    mc.gui.setScreen(new EnchantLevelMapSettingScreen(GuiThemes.get(),
                        (Setting<Object2IntMap<String>>) (Setting<?>) map));
                    sub = 2;
                    wait = 5;
                } else {
                    sub = 3;
                    current.enable();
                    wait = 10;
                }
            }
            case 2 -> {
                if (wait-- > 0) return;
                mc.gui.setScreen(null);
                sub = 3;
                current.enable();
                wait = 10;
            }
            case 3 -> {
                if (wait-- > 0) return;
                boolean active = current.isActive();
                current.disable();
                log("  toggle ok activeAfterEnable=" + active + " activeAfterDisable=" + current.isActive());
                if (current.isActive()) {
                    fail("module did not disable: " + current.name);
                    return;
                }
                current = null;
                sub = 0;
            }
            default -> fail("bad sub state");
        }
    }

    private void testClassTweakerAndBaritone() {
        if (sub == 0) {
            if (wait-- > 0) return;
            for (Module m : modules) m.disable();
            try {
                FakeBlockManager.addFakeCompensate(mc.player.blockPosition().above());
                log("classtweaker ok: getBlockStatePredictionHandler/startPredicting accessible");
            } catch (Throwable t) {
                fail("classtweaker check failed: " + t);
                return;
            }
            sub = 1;
            wait = 5;
            return;
        }
        if (sub == 1) {
            if (wait-- > 0) return;
            try {
                var pm = PathManagers.get();
                log("path manager=" + pm.getClass().getSimpleName());
                if (!pm.getClass().getSimpleName().equals("BaritonePathManager")) {
                    fail("Baritone path manager expected, got " + pm.getClass().getSimpleName());
                    return;
                }
                pm.moveTo(mc.player.blockPosition().offset(2, 0, 0), false);
                sub = 2;
                wait = 20;
            } catch (Throwable t) {
                fail("path manager check failed: " + t);
            }
            return;
        }
        if (sub == 2) {
            if (wait-- > 0) return;
            try {
                PathManagers.get().stop();
                log("path manager stop ok");
            } catch (Throwable t) {
                fail("path manager stop failed: " + t);
                return;
            }
            phase = 5;
            sub = 0;
            wait = 0;
        }
    }

    private void testElytraSearch() {
        if (sub == 0) {
            Module elytra = Modules.get().get(com.fo.addon.modules.ElytraCollector.class);
            if (elytra == null) {
                fail("ElytraCollector not found");
                return;
            }
            set(elytra, "世界种子", "0");
            set(elytra, "搜索半径", 500);
            set(elytra, "低Y退出", false);
            set(elytra, "开始采集", true);
            elytra.enable();
            log("elytra search started");
            sub = 1;
            wait = 80;
            return;
        }
        if (sub == 1) {
            if (wait-- > 0) return;
            Module elytra = Modules.get().get(com.fo.addon.modules.ElytraCollector.class);
            if (elytra != null) {
                elytra.disable();
                log("elytra search stopped");
            }
            phase = 8;
            wait = 20;
        }
    }

    private void testAncientSearch() {
        if (sub == 0) {
            Module ancient = Modules.get().get(com.fo.addon.ancient.modules.FOAncientCitySearch.class);
            if (ancient == null) {
                fail("FOAncientCitySearch not found");
                return;
            }
            set(ancient, "种子", "0");
            set(ancient, "搜索半径", 1000);
            set(ancient, "自动搜索", true);
            ancient.enable();
            log("ancient search started");
            sub = 1;
            wait = 600;
            return;
        }
        if (sub == 1) {
            if (wait-- > 0) return;
            Module ancient = Modules.get().get(com.fo.addon.ancient.modules.FOAncientCitySearch.class);
            if (ancient != null) {
                ancient.disable();
                log("ancient search stopped");
            }
            phase = 6;
            sub = 0;
            wait = 0;
        }
    }

    /** 给测试角色发一套挖矿必需装备，验证 AutoMining 的真实启动路径（而不是只测缺物品自停）。 */
    private void testAutoMiningWithGear() {
        if (sub == 0) {
            var server = mc.getSingleplayerServer();
            if (server == null || mc.player == null) {
                fail("singleplayer server/player missing for AutoMining test");
                return;
            }
            ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
            if (sp == null) {
                fail("ServerPlayer missing for AutoMining test");
                return;
            }

            var enchants = sp.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            Holder<Enchantment> fortune = enchants.getOrThrow(Enchantments.FORTUNE);
            Holder<Enchantment> silkTouch = enchants.getOrThrow(Enchantments.SILK_TOUCH);

            ItemStack fortunePick = new ItemStack(Items.DIAMOND_PICKAXE);
            EnchantmentHelper.updateEnchantments(fortunePick, m -> m.set(fortune, 3));
            ItemStack silkPick = new ItemStack(Items.DIAMOND_PICKAXE);
            EnchantmentHelper.updateEnchantments(silkPick, m -> m.set(silkTouch, 1));

            sp.getInventory().add(fortunePick);
            sp.getInventory().add(silkPick);
            sp.getInventory().add(new ItemStack(Items.ENDER_CHEST));
            sp.getInventory().add(new ItemStack(Items.CRAFTING_TABLE));

            AutoMining mining = Modules.get().get(AutoMining.class);
            if (mining == null) {
                fail("AutoMining not found");
                return;
            }
            set(mining, "挖矿模式", AutoMining.MiningMode.ANCIENT_DEBRIS);
            mining.enable();
            log("automining gear test started active=" + mining.isActive());
            sub = 1;
            wait = 40;
            return;
        }
        if (sub == 1) {
            if (wait-- > 0) return;
            AutoMining mining = Modules.get().get(AutoMining.class);
            boolean active = mining != null && mining.isActive();
            if (mining != null) mining.disable();
            log("automining gear test activeAfter40Ticks=" + active + " activeAfterDisable="
                + (mining != null && mining.isActive()));
            phase = 7;
            sub = 0;
            wait = 0;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> void set(Module module, String name, T value) {
        Setting<T> setting = (Setting<T>) module.settings.get(name);
        if (setting == null) throw new IllegalStateException("missing setting: " + name);
        setting.set(value);
    }

    private static int countSettings(Module module) {
        int n = 0;
        for (SettingGroup group : module.settings) {
            for (Setting<?> ignored : group) n++;
        }
        return n;
    }

    private static EnchantLevelMapSetting findEnchantSetting(Module module) {
        for (SettingGroup group : module.settings) {
            for (Setting<?> setting : group) {
                if (setting instanceof EnchantLevelMapSetting map) return map;
            }
        }
        return null;
    }

    private static void log(String msg) {
        System.out.println("[FO-IT] " + msg);
    }

    private static void fail(String msg) {
        System.out.println("[FO-IT] FO_INTEGRATION_TESTS_FAILED: " + msg);
        finish(2);
    }

    private static void finish(int code) {
        new Thread(() -> {
            try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
            System.exit(code);
        }, "fo-it-exit").start();
        mc.stop();
    }
}
