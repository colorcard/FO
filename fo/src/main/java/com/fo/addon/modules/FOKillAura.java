package com.fo.addon.modules;

import com.fo.addon.AddonTemplate;
import com.fo.addon.gui.ModuleGuide;
import com.fo.addon.guide.ModuleGuides;
import com.fo.addon.utils.Rotation;
import com.fo.addon.utils.RotationManager;
import com.fo.addon.utils.SwapBackTracker;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.combat.CrystalAura;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.Target;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.function.Predicate;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * FO 杀戮光环（移植自 meteor-miku KillAuraMiku；显示名统一为「FO 杀戮光环」）。
 *
 * <p>V6.24 两项改动（用户确认后实施）：
 * <ol>
 *   <li><b>显示名补空格</b>：由「FO杀戮光环」改为「FO 杀戮光环」——其余 7 个模块统一是
 *       {@code FO } 前缀 + 空格，只有它漏了，UI 上看着不齐。</li>
 *   <li><b>新增「切换回来」</b>：对齐彗星端（Meteor）官方 KillAura 的 {@code swap-back}
 *       ——自动切武器前记住原来占的快捷栏槽位，攻击会话结束时切回；会话期间反复切武器不覆盖记录。
 *       默认<b>关闭</b>（与官方默认一致）。状态机抽到 {@link SwapBackTracker}（纯逻辑，可单测）。</li>
 * </ol>
 *
 * <p>「切换回来」的触发点严格对齐官方 {@code stopAttacking()} 的调用位置：仅在
 * <b>目标列表为空 / 手上不是可接受武器 / 关闭模块</b>时结束会话并切回；暂停类守卫
 * （使用物品、仅点击、卡顿、水晶光环）直接 return，<b>不</b>结束会话（与官方一致）。
 *
 * <p>V6.7p32 两项修复（用户确认后实施）：
 * <ol>
 *   <li><b>删除「停止格挡」死设置</b>：该设置声明后从未被读取（{@code shouldShieldBreak()}
 *       恒返回 false），删设置 + 删方法 + 删 {@code itemInHand()} 里的死分支。</li>
 *   <li><b>修复「每次攻击执行两次」</b>：旧 {@code attack()} 先打一次
 *       {@code attackEntity}，分支里又打一次（默认=双 {@code attackEntity}；
 *       发包模式=多一个攻击包，服务端同样收到两次攻击）。现在两种模式都只执行
 *       <b>一次</b>攻击：默认走正规 {@code attackEntity}，发包模式只发攻击包。</li>
 * </ol>
 */
public class FOKillAura extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTargeting = settings.createGroup("目标选择");
    private final SettingGroup sgTiming = settings.createGroup("时机控制");

    // 通用设置

    private final Setting<Weapon> weapon = sgGeneral.add(new EnumSetting.Builder<Weapon>()
        .name("武器类型")
        .description("仅在手持指定武器时攻击实体")
        .defaultValue(Weapon.Sword)
        .build()
    );

    private final Setting<RotationMode> rotation = sgGeneral.add(new EnumSetting.Builder<RotationMode>()
        .name("视角旋转")
        .description("决定何时将视角转向目标")
        .defaultValue(RotationMode.OnHit)
        .build()
    );

    private final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("自动切换")
        .description("攻击目标时自动切换到选定的武器")
        .defaultValue(true)
        .build()
    );

    /**
     * 切换回来（对应彗星端官方 KillAura 的 swap-back，V6.24 新增）。
     * 自动切武器前记住原来占的快捷栏槽位，攻击会话结束时切回；会话期间反复切武器不覆盖记录。
     */
    private final Setting<Boolean> swapBack = sgGeneral.add(new BoolSetting.Builder()
        .name("切换回来")
        .description("攻击结束后切回原来占用的快捷栏槽位（仅在开启「自动切换」时有效）.")
        .defaultValue(false)
        .visible(autoSwitch::get)
        .build()
    );

    private final Setting<Boolean> packetAttack = sgGeneral.add(new BoolSetting.Builder()
        .name("发包攻击")
        .description("更好的模式")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> stopSprint = sgGeneral.add(new BoolSetting.Builder()
        .name("停止疾跑")
        .description("攻击前停止疾跑以保持原版行为")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> onlyOnClick = sgGeneral.add(new BoolSetting.Builder()
        .name("仅在点击时")
        .description("仅在按住鼠标左键时攻击")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> onlyOnLook = sgGeneral.add(new BoolSetting.Builder()
        .name("仅在注视时")
        .description("仅在注视实体时攻击")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> pauseOnCombat = sgGeneral.add(new BoolSetting.Builder()
        .name("暂停Baritone")
        .description("在攻击实体时暂时冻结 Baritone 自动寻路")
        .defaultValue(false)
        .build()
    );

    // 目标选择
    public final Setting<Set<EntityType<?>>> entities = sgTargeting.add(new EntityTypeListSetting.Builder()
        .name("实体类型")
        .description("要攻击的实体类型")
        .onlyAttackable()
        .defaultValue(EntityTypes.PLAYER,
            EntityTypes.BLAZE,              // 烈焰人
            EntityTypes.HUSK,               // 尸壳
            EntityTypes.CAVE_SPIDER,        // 洞穴蜘蛛
            EntityTypes.CREEPER,            // 苦力怕
            EntityTypes.DROWNED,            // 溺尸
            EntityTypes.ELDER_GUARDIAN,     // 远古守卫者
            EntityTypes.ENDER_DRAGON,       // 末影龙
            EntityTypes.ENDERMAN,           // 末影人
            EntityTypes.ENDERMITE,          // 末影螨
            EntityTypes.EVOKER,             // 唤魔者
            EntityTypes.GHAST,              // 恶魂
            EntityTypes.GIANT,              // 巨人
            EntityTypes.GUARDIAN,           // 守卫者
            EntityTypes.HOGLIN,             // 疣猪兽
            EntityTypes.ILLUSIONER,         // 幻术师
            EntityTypes.MAGMA_CUBE,         // 岩浆怪
            EntityTypes.PHANTOM,            // 幻翼
            EntityTypes.PIGLIN,             // 猪灵
            EntityTypes.PIGLIN_BRUTE,       // 猪灵蛮兵
            EntityTypes.PILLAGER,           // 掠夺者
            EntityTypes.RAVAGER,            // 劫掠兽
            EntityTypes.SHULKER,            // 潜影贝
            EntityTypes.SILVERFISH,         // 蠹虫
            EntityTypes.SKELETON,           // 骷髅
            EntityTypes.SLIME,              // 史莱姆
            EntityTypes.SPIDER,             // 蜘蛛
            EntityTypes.STRAY,              // 流浪者
            EntityTypes.VEX,                // 恼鬼
            EntityTypes.VINDICATOR,         // 卫道士
            EntityTypes.WARDEN,             // 监察者
            EntityTypes.WITCH,              // 女巫
            EntityTypes.WITHER,             // 凋灵
            EntityTypes.WITHER_SKELETON,    // 凋灵骷髅
            EntityTypes.ZOMBIE,             // 僵尸
            EntityTypes.ZOMBIFIED_PIGLIN,   // 僵尸猪灵
            EntityTypes.ZOGLIN,             // 僵尸疣猪兽
            EntityTypes.FIREBALL,           // 火球
            EntityTypes.SHULKER_BULLET)
        .build()
    );

    private final Setting<SortPriority> priority = sgTargeting.add(new EnumSetting.Builder<SortPriority>()
        .name("优先级")
        .description("范围内目标的筛选方式")
        .defaultValue(SortPriority.ClosestAngle)
        .build()
    );

    private final Setting<Integer> maxTargets = sgTargeting.add(new IntSetting.Builder()
        .name("最大目标数")
        .description("同时锁定的实体数量")
        .defaultValue(1)
        .min(1)
        .sliderRange(1, 5)
        .visible(() -> !onlyOnLook.get())
        .build()
    );

    private final Setting<Double> range = sgTargeting.add(new DoubleSetting.Builder()
        .name("攻击范围")
        .description("可攻击实体的最大距离")
        .defaultValue(3.1)
        .min(3)
        .sliderMax(7)
        .build()
    );

    private final Setting<Double> wallsRange = sgTargeting.add(new DoubleSetting.Builder()
        .name("穿墙范围")
        .description("可穿墙攻击实体的最大距离")
        .defaultValue(4.5)
        .min(2)
        .sliderMax(7)
        .build()
    );

    private final Setting<EntityAge> mobAgeFilter = sgTargeting.add(new EnumSetting.Builder<EntityAge>()
        .name("生物年龄过滤")
        .description("决定要攻击的生物年龄（幼年、成年或全部）")
        .defaultValue(EntityAge.Both)
        .build()
    );

    private final Setting<Boolean> ignoreNamed = sgTargeting.add(new BoolSetting.Builder()
        .name("忽略命名生物")
        .description("是否攻击拥有自定义名称的生物")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignorePassive = sgTargeting.add(new BoolSetting.Builder()
        .name("忽略被动生物")
        .description("仅在被动型生物主动攻击你时才进行反击.如猪人、小黑、狼")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreTamed = sgTargeting.add(new BoolSetting.Builder()
        .name("忽略驯服生物")
        .description("避免攻击你驯服的生物")
        .defaultValue(true)
        .build()
    );

    // 时机控制
    private final Setting<Boolean> pauseOnLag = sgTiming.add(new BoolSetting.Builder()
        .name("卡顿时暂停")
        .description("服务器卡顿时暂停攻击")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseOnUse = sgTiming.add(new BoolSetting.Builder()
        .name("使用物品时暂停")
        .description("使用物品时不进行攻击")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseOnCA = sgTiming.add(new BoolSetting.Builder()
        .name("水晶光环时暂停")
        .description("水晶光环放置时不进行攻击")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> tpsSync = sgTiming.add(new BoolSetting.Builder()
        .name("TPS同步")
        .description("尝试将攻击延迟与服务器 TPS 同步")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> customDelay = sgTiming.add(new BoolSetting.Builder()
        .name("自定义延迟")
        .description("使用自定义延迟而非原版冷却时间")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> hitDelay = sgTiming.add(new IntSetting.Builder()
        .name("攻击延迟")
        .description("攻击实体的速度（以刻为单位）")
        .defaultValue(13)
        .min(0)
        .sliderMax(60)
        .visible(customDelay::get)
        .build()
    );

    private final Setting<Integer> switchDelay = sgTiming.add(new IntSetting.Builder()
        .name("切换延迟")
        .description("切换快捷栏后等待多少刻才能攻击实体")
        .defaultValue(0)
        .min(0)
        .sliderMax(10)
        .build()
    );

    private final List<Entity> targets = new ArrayList<>();
    private int switchTimer, hitTimer;
    private boolean wasPathing = false;
    /** 「切换回来」状态机：记录本次攻击会话切换武器前占用的快捷栏槽位（V6.24） */
    private final SwapBackTracker swapBackTracker = new SwapBackTracker();
    /** 正在攻击实体（有目标且已切换/手持武器）——自动挖矿锁时运镐会据此跳过避免抢槽 */
    public boolean attacking;

    public FOKillAura() {
        super(AddonTemplate.CATEGORY, "FO 杀戮光环", "超级强力的杀敌光环，自动攻击你周围指定的实体，不卡脚. 使用前请看设置页底部「使用说明」.");
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        return ModuleGuide.section(theme, ModuleGuides.killAura());
    }

    @Override
    public void onActivate() {
        // 对齐官方 onActivate：复位「切换回来」记录（previousSlot = -1, swapped = false）
        swapBackTracker.reset();
    }

    @Override
    public void onDeactivate() {
        targets.clear();
        // 对齐官方 onDeactivate（targets.clear() → stopAttacking()）：关闭模块时若还欠着切回就补上，
        // 否则玩家会被留在武器槽上
        restoreSwapBack();
        attacking = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!mc.player.isAlive() || PlayerUtils.getGameMode() == GameType.SPECTATOR) return;
        if (pauseOnUse.get() && (mc.gameMode.isDestroying() || mc.player.isUsingItem())) return;
        if (onlyOnClick.get() && !mc.options.keyAttack.isDown()) return;
        if (TickRate.INSTANCE.getTimeSinceLastTick() >= 1f && pauseOnLag.get()) return;
        if (pauseOnCA.get() && Modules.get().get(CrystalAura.class).isActive() && Modules.get().get(CrystalAura.class).kaTimer > 0)
            return;

        if (onlyOnLook.get()) {
            Entity targeted = mc.crosshairPickEntity;

            if (targeted == null) return;
            if (!entityCheck(targeted)) return;

            targets.clear();
            targets.add(mc.crosshairPickEntity);
        } else {
            targets.clear();
            TargetUtils.getList(targets, this::entityCheck, priority.get(), maxTargets.get());
        }

        if (targets.isEmpty()) {
            attacking = false;
            if (wasPathing) {
                PathManagers.get().resume();
                wasPathing = false;
            }
            // 无目标 = 本次攻击会话结束（对齐官方 stopAttacking 的调用点）
            restoreSwapBack();
            return;
        }

        Entity primary = targets.getFirst();

        if (autoSwitch.get()) {
            Predicate<ItemStack> predicate = switch (weapon.get()) {
                case Axe -> stack -> stack.is(ItemTags.AXES);
                case Sword -> stack -> isSwordItem(stack.getItem());
                case Mace -> stack -> stack.getItem() instanceof MaceItem;
                case Trident -> stack -> stack.getItem() instanceof TridentItem;
                case All ->
                    stack -> stack.is(ItemTags.AXES) || isSwordItem(stack.getItem()) || stack.getItem() instanceof MaceItem || stack.getItem() instanceof TridentItem;
                default -> o -> true;
            };
            FindItemResult weaponResult = InvUtils.findInHotbar(predicate);

            // 「切换回来」：本会话首次切换时记住切换前占用的槽位（对齐官方 if (!swapped) 语义，
            // 与「切换回来」开关无关——开关只决定会话结束时是否真的切回）
            swapBackTracker.recordBeforeSwap(mc.player.getInventory().getSelectedSlot());

            InvUtils.swap(weaponResult.slot(), false);
        }

        if (!itemInHand()) {
            // 手上不是可接受武器 = 本次攻击会话结束（对齐官方 acceptableWeapon 判定失败时调用 stopAttacking）
            restoreSwapBack();
            return;
        }

        attacking = true;
        if (rotation.get() == RotationMode.Always)
            Rotations.rotate(Rotations.getYaw(primary), Rotations.getPitch(primary, Target.Body));
        if (pauseOnCombat.get() && PathManagers.get().isPathing() && !wasPathing) {
            PathManagers.get().pause();
            wasPathing = true;
        }

        if (delayCheck()) targets.forEach(this::attack);
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (event.packet instanceof ServerboundSetCarriedItemPacket) {
            switchTimer = switchDelay.get();
        }
    }

    /**
     * 攻击会话结束：按「切换回来」设置决定是否切回原快捷栏槽位
     * （对齐官方 {@code KillAura.stopAttacking()} 末尾的切回段）。
     *
     * <p>无论是否真的切回，状态机都会复位，下次会话重新记录原槽位。
     */
    private void restoreSwapBack() {
        int slot = swapBackTracker.completeSession(swapBack.get());
        if (slot != SwapBackTracker.NO_SLOT) {
            InvUtils.swap(slot, false);
        }
    }

    private boolean entityCheck(Entity entity) {
        if (entity.equals(mc.player)) return false;
        if ((entity instanceof LivingEntity livingEntity && livingEntity.isDeadOrDying()) || !entity.isAlive()) return false;

        AABB hitbox = entity.getBoundingBox();
        if (!PlayerUtils.isWithin(
            Mth.clamp(mc.player.getX(), hitbox.minX, hitbox.maxX),
            Mth.clamp(mc.player.getY(), hitbox.minY, hitbox.maxY),
            Mth.clamp(mc.player.getZ(), hitbox.minZ, hitbox.maxZ),
            range.get()
        )) return false;

        if (!entities.get().contains(entity.getType())) return false;
        if (ignoreNamed.get() && entity.hasCustomName()) return false;
        if (!PlayerUtils.canSeeEntity(entity) && !PlayerUtils.isWithin(entity, wallsRange.get())) return false;
        if (ignorePassive.get()) {
            if (entity instanceof Enderman enderman && !enderman.isCreepy()) return false;
            if (entity instanceof ZombifiedPiglin piglin && !piglin.isAggressive()) return false;
            if (entity instanceof Wolf wolf && !wolf.isAggressive()) return false;
        }
        if (entity instanceof Player player) {
            if (player.isCreative()) return false;
            if (!Friends.get().shouldAttack(player)) return false;
        }
        if (entity instanceof Animal animal) {
            return switch (mobAgeFilter.get()) {
                case Baby -> animal.isBaby();
                case Adult -> !animal.isBaby();
                case Both -> true;
            };
        }
        return true;
    }

    private boolean delayCheck() {
        if (switchTimer > 0) {
            switchTimer--;
            return false;
        }

        float delay = (customDelay.get()) ? hitDelay.get() : 0.5f;
        if (tpsSync.get()) delay /= (TickRate.INSTANCE.getTickRate() / 20);

        if (customDelay.get()) {
            if (hitTimer < delay) {
                hitTimer++;
                return false;
            } else {
                return true;
            }
        } else {
            return mc.player.getAttackStrengthScale(delay) >= 1;
        }
    }

    private void attack(Entity target) {
        if (stopSprint.get()) {
            if (mc.player.isShiftKeyDown()) {
                sendReleaseShift();
            }
            if (mc.player.isSprinting()) {
                mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
            }
        }

        Vec3 feetPos = target.position();

        Vec3 torsoPos = feetPos.add(0.0, target.getBbHeight() / 2.0f, 0.0);
        Vec3 eyesPos = target.getEyePosition();
        Vec3 hitVec = Stream.of(feetPos, torsoPos, eyesPos)
            .min(Comparator.comparing(pos -> mc.player.getEyePosition().distanceToSqr(pos)))
            .orElse(eyesPos);

        Rotation rotation = new Rotation(hitVec).setPriority(10);
        RotationManager.getInstance().register(rotation);

        // V6.7p32：修复「每次攻击执行两次」。旧实现上面先打一次 attackEntity，
        // 这里分支里又打一次 —— 默认 = 双 attackEntity（服务端收到 2 个攻击包），
        // 发包模式 = 正规攻击 + 额外攻击包（同样 2 个）。现在两种模式都只执行一次攻击：
        // 默认走正规 attackEntity（带选中槽位同步 + 客户端攻击逻辑），
        // 发包模式只发攻击包（不再夹带一次正规攻击）。
        if (packetAttack.get()) {
            mc.getConnection().send(new ServerboundInteractPacket(target.getId(), InteractionHand.MAIN_HAND, Vec3.ZERO, mc.player.isShiftKeyDown()));
        } else {
            mc.gameMode.attack(mc.player, target);
        }
        mc.player.swing(InteractionHand.MAIN_HAND, mc.player.getMainHandItem().getAttackAnimation(), false);

        hitTimer = 0;
        if (this.rotation.get() == RotationMode.OnHit) {
            RotationManager.getInstance().sync();
        }
    }

    private boolean itemInHand() {
        Item item = mc.player.getMainHandItem().getItem();

        return switch (weapon.get()) {
            case Axe -> item.getDefaultInstance().is(ItemTags.AXES);
            case Sword -> isSwordItem(item);
            case Mace -> item instanceof MaceItem;
            case Trident -> item instanceof TridentItem;
            case All ->
                item.getDefaultInstance().is(ItemTags.AXES) || isSwordItem(item) || item instanceof MaceItem || item instanceof TridentItem;
            default -> true;
        };
    }

    /** 任意品质的剑判定（移植自 meteor-miku MikuUtil.isSwordItem） */
    private static boolean isSwordItem(Item item) {
        return Set.of(
            Items.WOODEN_SWORD,
            Items.STONE_SWORD,
            Items.IRON_SWORD,
            Items.GOLDEN_SWORD,
            Items.DIAMOND_SWORD,
            Items.NETHERITE_SWORD
        ).contains(item);
    }

    /** 停止潜行发包（移植自 meteor-miku Via.sendReleaseShift） */
    private void sendReleaseShift() {
        Input playerInput = new Input(false, false, false, false, false, false, false);
        mc.player.connection.send(new ServerboundPlayerInputPacket(playerInput));
    }

    public Entity getTarget() {
        if (!targets.isEmpty()) return targets.getFirst();
        return null;
    }

    @Override
    public String getInfoString() {
        if (!targets.isEmpty()) return EntityUtils.getName(getTarget());
        return null;
    }

    public enum Weapon {
        Sword("剑"),
        Axe("斧"),
        Mace("锤"),
        Trident("三叉戟"),
        All("全部武器"),
        Any("任意物品");

        private final String displayName;

        Weapon(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public enum RotationMode {
        Always("始终旋转"),
        OnHit("攻击时旋转"),
        None("不旋转");

        private final String displayName;

        RotationMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public enum EntityAge {
        Baby("幼年"),
        Adult("成年"),
        Both("全部");

        private final String displayName;

        EntityAge(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
}
