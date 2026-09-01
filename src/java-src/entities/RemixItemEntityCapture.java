final class RemixItemEntityCapture {
    private static volatile boolean enabled = true;
    private static boolean pickupRenderActive;

    private RemixItemEntityCapture() {
    }

    static void onPickupRenderStart(sn entity) {
        pickupRenderActive = false;
    }

    static void onPickupRenderEnd() {
        pickupRenderActive = false;
    }

    private static iz getItemStackFromEntity(sn entity) {
        if (entity instanceof hl) {
            return ((hl) entity).a;
        }
        return null;
    }

    static void onRenderStart(sn entity) {
        if (!canCapture() || entity == null) {
            return;
        }
        RemixDynamicEntitySession.ensureFrame();
        pickupRenderActive = true;
        iz itemStack = getItemStackFromEntity(entity);
        int itemId = itemStack != null ? itemStack.c : -1;
        String texture = (itemId > -1 && itemId < 256) ? "/terrain.png" : "/gui/items.png";
        if (RemixHeldItemCapture.isLapisItem(itemStack)) {
            texture = RemixHeldItemCapture.lapisTextureAlias(texture);
        }
        RemixDynamicEntitySession.beginEntity(entity.aD, 0, 0, 0.0f, texture);
        
        double worldX = entity.aM;
        double worldY = entity.aN;
        double worldZ = entity.aO;
        
        mcrtx.bridge.RemixDynamicEntityBridge.setEntityLight(entity.aD, worldX, worldY, worldZ, itemId);
    }

    static void onRenderEnd() {
        if (!pickupRenderActive) {
            return;
        }
        pickupRenderActive = false;
        RemixLivingEntityCapture.onRenderEnd();
    }

    static void setEnabled(boolean value) {
        enabled = value;
        if (value || !pickupRenderActive) {
            return;
        }
        pickupRenderActive = false;
        RemixDynamicEntitySession.clearEntityState();
    }

    private static boolean canCapture() {
        return enabled && RemixDynamicEntitySession.canCapture();
    }

    static void resetActiveCapture() {
        pickupRenderActive = false;
    }
}
