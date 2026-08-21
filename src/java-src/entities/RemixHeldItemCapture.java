import mcrtx.bridge.RemixDynamicEntityBridge;
import mcrtx.bridge.RemixLifecycleBridge;

final class RemixHeldItemCapture {
    static final int NO_HELD_ITEM = -1;
    static final String TERRAIN_TEXTURE_PATH = "/terrain.png";
    static final String GUI_ITEMS_TEXTURE_PATH = "/gui/items.png";

    private static final int TORCH_BLOCK_ID = 50;
    private static final int REDSTONE_TORCH_OFF_BLOCK_ID = 75;
    private static final int REDSTONE_TORCH_ON_BLOCK_ID = 76;
    private static final int LAVA_BUCKET_ITEM_ID = 327;
    private static final int CLOCK_ITEM_ID = 347;
    private static final int COMPASS_ITEM_ID = 345;
    private static final float ENTITY_HELD_TORCH_RIGHT_NUDGE = 0.18f;

    private static volatile boolean heldTorchLightsEnabled = true;

    private RemixHeldItemCapture() {
    }

    private static int getClockFrame() {
        net.minecraft.client.Minecraft mc = RemixLifecycleBridge.getRememberedMinecraft();
        if (mc != null && mc.f != null && mc.f.t != null) {
            boolean isNether = mc.f.t instanceof wd;
            double time = !isNether ? mc.f.b(1.0f) : Math.random();
            return (int)(time * 64.0) % 64;
        }
        return 0;
    }

    private static int getCompassFrame() {
        net.minecraft.client.Minecraft mc = RemixLifecycleBridge.getRememberedMinecraft();
        if (mc != null && mc.f != null && mc.h != null) {
            gs player = (gs) mc.h;
            br spawn = mc.f.u();
            int spawnX = spawn.a;
            int spawnZ = spawn.c;
            double dx = (double)spawnX - player.aM;
            double dz = (double)spawnZ - player.aO;
            double targetAngle = (Math.atan2(dz, dx) * 180.0 / Math.PI) - (player.aS + 90.0);
            while (targetAngle < 0) targetAngle += 360.0;
            while (targetAngle >= 360.0) targetAngle -= 360.0;
            return (int)(targetAngle / 360.0 * 32.0) % 32;
        }
        return 0;
    }

    static void setHeldTorchLightsEnabled(boolean enabled) {
        heldTorchLightsEnabled = enabled;
    }

    static void onFirstPersonItemRender(iz itemStack) {
        if (itemStack == null) {
            return;
        }

        if (!RemixFirstPersonCapture.isActive()) {
            RemixDynamicEntityBridge.setFirstPersonHeldItem(
                    heldTorchLightsEnabled && isTorchLikeHeldItem(itemStack.c)
                            ? itemStack.c
                            : NO_HELD_ITEM);
            return;
        }

        RemixFirstPersonCapture.setActiveTexture(texturePathForItem(itemStack));
        RemixDynamicEntityBridge.setFirstPersonHeldItem(
                heldTorchLightsEnabled && isTorchLikeHeldItem(itemStack.c)
                        ? itemStack.c
                        : NO_HELD_ITEM);
    }

    static void onPlayerEquippedItemRenderStart(gs player, iz itemStack, float partialTicks) {
        if (!RemixLifecycleBridge.isInitialized() || player == null) {
            return;
        }

        syncEntityHeldTorch(player, itemStack, partialTicks);
        if (!RemixDynamicEntitySession.isEntityActive() || itemStack == null) {
            return;
        }
        RemixDynamicEntitySession.bindEntityTexture(texturePathForItem(itemStack), null);
    }

    static void onLivingEquippedItemRenderStart(ls entity, iz itemStack) {
        if (!RemixLifecycleBridge.isInitialized() || entity == null || itemStack == null) {
            return;
        }
        if (!RemixDynamicEntitySession.isEntityActive()) {
            return;
        }
        RemixDynamicEntitySession.bindEntityTexture(texturePathForItem(itemStack), null);
    }

    private static boolean isTorchLikeHeldItem(int itemId) {
        return itemId == TORCH_BLOCK_ID
                || itemId == REDSTONE_TORCH_ON_BLOCK_ID
                || itemId == REDSTONE_TORCH_OFF_BLOCK_ID
                || itemId == LAVA_BUCKET_ITEM_ID;
    }

    private static String texturePathForItem(iz itemStack) {
        if (itemStack.c == CLOCK_ITEM_ID) {
            return "/gui/clock.png?frame=" + getClockFrame();
        }
        if (itemStack.c == COMPASS_ITEM_ID) {
            return "/gui/compass.png?frame=" + getCompassFrame();
        }
        return itemStack.c < 256 ? TERRAIN_TEXTURE_PATH : GUI_ITEMS_TEXTURE_PATH;
    }

    private static void syncEntityHeldTorch(gs player, iz heldItem, float partialTicks) {
        if (RemixFirstPersonCapture.isShadowCaptureActive()) {
            return;
        }

        if (!heldTorchLightsEnabled) {
            RemixDynamicEntityBridge.setEntityLight(
                    player.aD, 0.0f, 0.0f, 0.0f, NO_HELD_ITEM);
            return;
        }

        int itemId = heldItem != null && isTorchLikeHeldItem(heldItem.c)
                ? heldItem.c
                : NO_HELD_ITEM;
        if (itemId == NO_HELD_ITEM) {
            RemixDynamicEntityBridge.setEntityLight(
                    player.aD, 0.0f, 0.0f, 0.0f, NO_HELD_ITEM);
            return;
        }

        float[] modelView = RemixDynamicModelCapture.captureModelViewMatrix();
        if (modelView == null) {
            return;
        }
        RemixCameraState.PreciseTransform modelToWorld =
                RemixCameraState.buildModelToWorldTransform(modelView);
        double handX = modelToWorld.x;
        double handY = modelToWorld.y;
        double handZ = modelToWorld.z;
        float interpolatedYaw = player.aU + (player.aS - player.aU) * partialTicks;
        double yawRadians = Math.toRadians(interpolatedYaw);
        handX += (-Math.cos(yawRadians)) * (double) ENTITY_HELD_TORCH_RIGHT_NUDGE;
        handZ += (-Math.sin(yawRadians)) * (double) ENTITY_HELD_TORCH_RIGHT_NUDGE;
        RemixDynamicEntityBridge.setEntityLight(player.aD, handX, handY, handZ, itemId);
    }
}
