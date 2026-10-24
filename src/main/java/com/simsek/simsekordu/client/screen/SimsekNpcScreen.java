package com.simsek.simsekordu.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.simsek.simsekordu.data.HuntMode;
import com.simsek.simsekordu.data.Job;
import com.simsek.simsekordu.data.MiningRegion;
import com.simsek.simsekordu.data.Rank;
import com.simsek.simsekordu.entity.SimsekNpc;
import com.simsek.simsekordu.menu.SimsekNpcMenu;
import com.simsek.simsekordu.network.SimsekNpcPayloads;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * NPC screen with an info tab and an inventory tab. No texture is used: the panel, slots and
 * bars are drawn with {@link GuiGraphics#fill} in the vanilla style.
 *
 * <p>The info tab ends in a task section for the job: the mining region of a miner (and of any soldier that is not
 * a hunter) or the hunting centre and mode of a hunter.
 */
public class SimsekNpcScreen extends AbstractContainerScreen<SimsekNpcMenu> {
    private enum Tab {
        INFO,
        INVENTORY
    }

    private static final int TEXT_COLOR = 0x404040;
    private static final int PAD = 6;
    private static final int CONTENT_WIDTH = SimsekNpcMenu.PANEL_WIDTH - 2 * PAD;
    /** Second text column of the info tab (job, task status). */
    private static final int RIGHT_COLUMN_X = 96;

    // Vertical layout of the info tab (relative to the panel's top edge).
    private static final int TITLE_Y = 6;
    private static final int HEALTH_Y = 17;
    private static final int XP_BAR_Y = 28;
    private static final int XP_BAR_HEIGHT = 11;
    private static final int BONUS_TITLE_Y = 42;
    private static final int BONUS_LINE_1_Y = 52;
    private static final int BONUS_LINE_2_Y = 62;
    private static final int JOB_BUTTONS_Y = 74;
    private static final int COMMAND_BUTTONS_Y = 95;
    private static final int RENAME_Y = 116;
    private static final int SEPARATOR_Y = 138;
    private static final int TASK_LINE_1_Y = 142;
    private static final int TASK_LINE_2_Y = 152;
    private static final int TASK_LINE_3_Y = 162;
    private static final int MODE_BUTTONS_Y = 173;
    private static final int CANCEL_Y_HUNTER = 191;
    private static final int CANCEL_Y_OTHER = 174;
    private static final int HEIGHT_HUNTER = 215;
    private static final int HEIGHT_OTHER = 198;
    private static final int INVENTORY_HEIGHT = 166;
    /** Space kept above the panel for the tab buttons, also on small windows. */
    private static final int TAB_SPACE = 24;

    private Tab tab = Tab.INFO;
    private boolean hunterLayout;
    private Button infoTabButton;
    private Button inventoryTabButton;
    private final Map<Job, Button> jobButtons = new EnumMap<>(Job.class);
    private final Map<HuntMode, Button> modeButtons = new EnumMap<>(HuntMode.class);
    private Button followButton;
    private Button waitButton;
    private EditBox nameBox;
    private Button renameButton;
    private Button cancelTaskButton;

    public SimsekNpcScreen(SimsekNpcMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = SimsekNpcMenu.PANEL_WIDTH;
        this.imageHeight = HEIGHT_HUNTER;
    }

    @Override
    protected void init() {
        // Centre using the tallest layout so that switching tabs or jobs does not move the top edge.
        this.imageHeight = HEIGHT_HUNTER;
        super.init();
        this.topPos = Math.max(this.topPos, TAB_SPACE);
        SimsekNpc npc = this.menu.getNpc();
        if (npc == null) {
            onClose();
            return;
        }

        this.infoTabButton = addRenderableWidget(Button.builder(Component.translatable("gui.simsekordu.tab.info"),
                button -> setTab(Tab.INFO)).bounds(this.leftPos, this.topPos - 22, 70, 20).build());
        this.inventoryTabButton = addRenderableWidget(Button.builder(Component.translatable("gui.simsekordu.tab.inventory"),
                button -> setTab(Tab.INVENTORY)).bounds(this.leftPos + 72, this.topPos - 22, 70, 20).build());

        this.jobButtons.clear();
        int x = this.leftPos + PAD;
        for (Job job : Job.values()) {
            Button button = Button.builder(Component.translatable(job.getTranslationKey()),
                    b -> PacketDistributor.sendToServer(new SimsekNpcPayloads.SetJobPayload(npc.getId(), job.ordinal())))
                    .bounds(x, this.topPos + JOB_BUTTONS_Y, 46, 18).build();
            this.jobButtons.put(job, addRenderableWidget(button));
            x += 47;
        }

        this.followButton = addRenderableWidget(Button.builder(Component.translatable("gui.simsekordu.command.follow"),
                b -> PacketDistributor.sendToServer(new SimsekNpcPayloads.SetWaitingPayload(npc.getId(), false)))
                .bounds(this.leftPos + PAD, this.topPos + COMMAND_BUTTONS_Y, 92, 18).build());
        this.waitButton = addRenderableWidget(Button.builder(Component.translatable("gui.simsekordu.command.wait"),
                b -> PacketDistributor.sendToServer(new SimsekNpcPayloads.SetWaitingPayload(npc.getId(), true)))
                .bounds(this.leftPos + PAD + 96, this.topPos + COMMAND_BUTTONS_Y, 92, 18).build());

        this.nameBox = new EditBox(this.font, this.leftPos + PAD, this.topPos + RENAME_Y + 1, 110, 16,
                Component.translatable("gui.simsekordu.rename"));
        this.nameBox.setMaxLength(SimsekNpcPayloads.MAX_NAME_LENGTH);
        this.nameBox.setValue(npc.getNpcName());
        addRenderableWidget(this.nameBox);
        this.renameButton = addRenderableWidget(Button.builder(Component.translatable("gui.simsekordu.rename"),
                b -> submitRename()).bounds(this.leftPos + PAD + 112, this.topPos + RENAME_Y, 76, 18).build());

        this.modeButtons.clear();
        int modeX = this.leftPos + PAD;
        for (HuntMode mode : HuntMode.values()) {
            Button button = Button.builder(Component.translatable(mode.getTranslationKey()),
                    b -> PacketDistributor.sendToServer(new SimsekNpcPayloads.SetHuntModePayload(npc.getId(), mode.ordinal())))
                    .bounds(modeX, this.topPos + MODE_BUTTONS_Y, 62, 16).build();
            this.modeButtons.put(mode, addRenderableWidget(button));
            modeX += 63;
        }

        this.cancelTaskButton = addRenderableWidget(Button.builder(Component.translatable("gui.simsekordu.task.cancel"),
                b -> PacketDistributor.sendToServer(new SimsekNpcPayloads.CancelTaskPayload(npc.getId())))
                .bounds(this.leftPos + PAD, this.topPos + CANCEL_Y_OTHER, CONTENT_WIDTH, 18).build());

        setTab(this.tab);
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        String typedName = this.nameBox != null ? this.nameBox.getValue() : null;
        super.resize(minecraft, width, height);
        if (typedName != null && this.nameBox != null) {
            this.nameBox.setValue(typedName);
        }
    }

    private void setTab(Tab newTab) {
        this.tab = newTab;
        this.menu.setInventoryTabActive(newTab == Tab.INVENTORY);
        setFocused(null);
        this.nameBox.setFocused(false);
        applyLayout();
    }

    /** Sizes the panel and shows the widgets that belong to the current tab and job. */
    private void applyLayout() {
        SimsekNpc npc = this.menu.getNpc();
        boolean info = this.tab == Tab.INFO;
        this.hunterLayout = npc != null && npc.getJob() == Job.HUNTER;
        this.imageHeight = !info ? INVENTORY_HEIGHT : this.hunterLayout ? HEIGHT_HUNTER : HEIGHT_OTHER;
        this.jobButtons.values().forEach(button -> button.visible = info);
        this.modeButtons.values().forEach(button -> button.visible = info && this.hunterLayout);
        this.followButton.visible = info;
        this.waitButton.visible = info;
        this.renameButton.visible = info;
        this.cancelTaskButton.visible = info;
        this.cancelTaskButton.setY(this.topPos + (this.hunterLayout ? CANCEL_Y_HUNTER : CANCEL_Y_OTHER));
        this.nameBox.visible = info;
        updateButtonStates();
    }

    private void updateButtonStates() {
        SimsekNpc npc = this.menu.getNpc();
        if (npc == null) {
            return;
        }
        this.infoTabButton.active = this.tab != Tab.INFO;
        this.inventoryTabButton.active = this.tab != Tab.INVENTORY;
        this.jobButtons.forEach((job, button) -> button.active = npc.getJob() != job);
        this.modeButtons.forEach((mode, button) -> button.active = npc.getHuntMode() != mode);
        this.followButton.active = npc.isWaiting();
        this.waitButton.active = !npc.isWaiting();
        this.cancelTaskButton.active = npc.getJob() == Job.HUNTER ? npc.getHuntCenter() != null : npc.getMiningRegion() != null;
    }

    private void submitRename() {
        SimsekNpc npc = this.menu.getNpc();
        String name = SimsekNpcPayloads.sanitizeName(this.nameBox.getValue());
        if (npc != null && !name.isEmpty()) {
            PacketDistributor.sendToServer(new SimsekNpcPayloads.RenamePayload(npc.getId(), name));
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        SimsekNpc npc = this.menu.getNpc();
        if (npc == null || !npc.isAlive()) {
            onClose();
            return;
        }
        if ((npc.getJob() == Job.HUNTER) != this.hunterLayout) {
            applyLayout();
        }
        updateButtonStates();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (this.tab == Tab.INFO && this.nameBox.isFocused()) {
            if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
                submitRename();
                return true;
            }
            // Keep typing in the box from triggering screen shortcuts (e.g. the inventory key closing the screen).
            return this.nameBox.keyPressed(keyCode, scanCode, modifiers) || this.nameBox.canConsumeInput()
                    || super.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        drawPanel(graphics, this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
        if (this.tab == Tab.INVENTORY) {
            for (Slot slot : this.menu.slots) {
                drawSlotBackground(graphics, this.leftPos + slot.x - 1, this.topPos + slot.y - 1);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        SimsekNpc npc = this.menu.getNpc();
        if (npc == null) {
            return;
        }
        int labelX = this.tab == Tab.INVENTORY ? SimsekNpcMenu.SLOT_X - 1 : PAD;
        drawFitted(graphics, npc.getName(), labelX, TITLE_Y, CONTENT_WIDTH);
        if (this.tab == Tab.INVENTORY) {
            graphics.drawString(this.font, Component.translatable("gui.simsekordu.inventory"), labelX, 18, TEXT_COLOR, false);
            graphics.drawString(this.font, this.playerInventoryTitle, labelX, SimsekNpcMenu.PLAYER_INV_Y - 12, TEXT_COLOR, false);
            return;
        }

        Rank rank = npc.getRank();
        int health = Mth.ceil(npc.getHealth());
        int maxHealth = Mth.ceil(npc.getMaxHealth());
        int xp = npc.getXp();
        int xpNeeded = SimsekNpc.xpForNextLevel(npc.getNpcLevel());

        drawFitted(graphics, Component.translatable("gui.simsekordu.health", health, maxHealth), PAD, HEALTH_Y, RIGHT_COLUMN_X - PAD - 4);
        drawFitted(graphics, Component.translatable("gui.simsekordu.job", Component.translatable(npc.getJob().getTranslationKey())),
                RIGHT_COLUMN_X, HEALTH_Y, rightColumnWidth());
        drawXpBar(graphics, PAD, XP_BAR_Y, CONTENT_WIDTH, XP_BAR_HEIGHT, xpNeeded > 0 ? (float) xp / xpNeeded : 0.0F);
        graphics.drawCenteredString(this.font, Component.translatable("gui.simsekordu.xp", xp, xpNeeded),
                PAD + CONTENT_WIDTH / 2, XP_BAR_Y + 2, 0xFFFFFF);

        drawFitted(graphics, Component.translatable("gui.simsekordu.bonuses.title",
                Component.translatable(rank.getTranslationKey())), PAD, BONUS_TITLE_Y, CONTENT_WIDTH);
        drawFitted(graphics, Component.translatable("gui.simsekordu.bonuses.line1",
                percentBonus(rank.getMaxHealthMultiplier()), percentBonus(rank.getAttackDamageMultiplier()),
                percentBonus(rank.getMovementSpeedMultiplier())), PAD, BONUS_LINE_1_Y, CONTENT_WIDTH);
        drawFitted(graphics, Component.translatable("gui.simsekordu.bonuses.line2",
                percentBonus(rank.getMiningSpeedMultiplier()), rank.getAimBonusPercent()), PAD, BONUS_LINE_2_Y, CONTENT_WIDTH);

        graphics.fill(PAD, SEPARATOR_Y, PAD + CONTENT_WIDTH, SEPARATOR_Y + 1, 0xFF8B8B8B);
        graphics.fill(PAD, SEPARATOR_Y + 1, PAD + CONTENT_WIDTH, SEPARATOR_Y + 2, 0xFFFFFFFF);
        if (this.hunterLayout) {
            drawHuntSection(graphics, npc);
        } else {
            drawMiningSection(graphics, npc);
        }
    }

    private void drawMiningSection(GuiGraphics graphics, SimsekNpc npc) {
        MiningRegion region = npc.getMiningRegion();
        if (region == null) {
            drawFitted(graphics, Component.translatable("gui.simsekordu.task.region.none"), PAD, TASK_LINE_1_Y, CONTENT_WIDTH);
            drawFitted(graphics, Component.translatable("gui.simsekordu.task.remaining", "-"), PAD, TASK_LINE_2_Y, RIGHT_COLUMN_X - PAD - 4);
        } else {
            drawFitted(graphics, Component.translatable("gui.simsekordu.task.region", coords(region.min()), coords(region.max())),
                    PAD, TASK_LINE_1_Y, CONTENT_WIDTH);
            drawFitted(graphics, Component.translatable("gui.simsekordu.task.remaining", npc.getMiningRemaining()),
                    PAD, TASK_LINE_2_Y, RIGHT_COLUMN_X - PAD - 4);
        }
        drawStatusAndStorage(graphics, npc, region != null);
    }

    private void drawHuntSection(GuiGraphics graphics, SimsekNpc npc) {
        BlockPos center = npc.getHuntCenter();
        drawFitted(graphics, center == null
                ? Component.translatable("gui.simsekordu.hunt.center.none")
                : Component.translatable("gui.simsekordu.hunt.center", coords(center).replace(",", ", ")),
                PAD, TASK_LINE_1_Y, CONTENT_WIDTH);
        drawFitted(graphics, Component.translatable("gui.simsekordu.hunt.kills", npc.getHuntKills()),
                PAD, TASK_LINE_2_Y, RIGHT_COLUMN_X - PAD - 4);
        drawStatusAndStorage(graphics, npc, center != null);
    }

    /** The status (right of the second task line) and the storage coordinates (third line), shared by both sections. */
    private void drawStatusAndStorage(GuiGraphics graphics, SimsekNpc npc, boolean hasTask) {
        BlockPos storage = npc.getStoragePos();
        Component status = hasTask ? Component.translatable(npc.getTaskStatus().getTranslationKey()) : Component.literal("-");
        drawFitted(graphics, Component.translatable("gui.simsekordu.task.status", status), RIGHT_COLUMN_X, TASK_LINE_2_Y, rightColumnWidth());
        drawFitted(graphics, storage == null
                ? Component.translatable("gui.simsekordu.task.storage.none")
                : Component.translatable("gui.simsekordu.task.storage", coords(storage).replace(",", ", ")),
                PAD, TASK_LINE_3_Y, CONTENT_WIDTH);
    }

    private static String coords(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static int rightColumnWidth() {
        return SimsekNpcMenu.PANEL_WIDTH - PAD - RIGHT_COLUMN_X;
    }

    private static int percentBonus(double multiplier) {
        return (int) Math.round((multiplier - 1.0D) * 100.0D);
    }

    private void drawFitted(GuiGraphics graphics, Component text, int x, int y, int maxWidth) {
        graphics.drawString(this.font,
                Language.getInstance().getVisualOrder(this.font.substrByWidth(text, maxWidth)),
                x, y, TEXT_COLOR, false);
    }

    private static void drawXpBar(GuiGraphics graphics, int x, int y, int width, int height, float fraction) {
        graphics.fill(x, y, x + width, y + height, 0xFF000000);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF373737);
        int filled = Mth.floor((width - 2) * Mth.clamp(fraction, 0.0F, 1.0F));
        if (filled > 0) {
            graphics.fill(x + 1, y + 1, x + 1 + filled, y + height - 1, 0xFF3F9F3F);
            graphics.fill(x + 1, y + 1, x + 1 + filled, y + 2, 0xFF6FCF6F);
        }
    }

    private static void drawPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xFF000000);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFFC6C6C6);
        graphics.fill(x + 1, y + 1, x + width - 2, y + 3, 0xFFFFFFFF);
        graphics.fill(x + 1, y + 1, x + 3, y + height - 2, 0xFFFFFFFF);
        graphics.fill(x + 2, y + height - 3, x + width - 1, y + height - 1, 0xFF555555);
        graphics.fill(x + width - 3, y + 2, x + width - 1, y + height - 1, 0xFF555555);
    }

    private static void drawSlotBackground(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, 0xFF8B8B8B);
        graphics.fill(x, y, x + 17, y + 1, 0xFF373737);
        graphics.fill(x, y, x + 1, y + 17, 0xFF373737);
        graphics.fill(x + 1, y + 17, x + 18, y + 18, 0xFFFFFFFF);
        graphics.fill(x + 17, y + 1, x + 18, y + 18, 0xFFFFFFFF);
    }
}
