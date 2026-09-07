import mcrtx.bridge.RemixParticleOverlayBridge;
import mcrtx.bridge.RemixLifecycleBridge;
import mcrtx.bridge.McrtxGameplaySettings;

public final class RemixBlockOutlineCapture {
    private RemixBlockOutlineCapture() {
    }

    public static void onBlockOutlineRender(gs player, vf movingobjectposition, int renderMode, float partialTicks) {
        if (!RemixLifecycleBridge.isInitialized() || !McrtxGameplaySettings.isBlockOutlineEnabled() || player == null || movingobjectposition == null || renderMode != 0) {
            return;
        }

        if (movingobjectposition.a != jg.a) {
            return;
        }

        fd attachedWorld = RemixChunkCapture.attachedWorld();
        if (attachedWorld == null && player != null) {
            attachedWorld = player.aI;
        }
        if (attachedWorld == null) {
            return;
        }

        int blockX = movingobjectposition.b;
        int blockY = movingobjectposition.c;
        int blockZ = movingobjectposition.d;
        int blockId = attachedWorld.a(blockX, blockY, blockZ);
        if (blockId <= 0 || blockId >= uu.m.length || uu.m[blockId] == null) {
            return;
        }

        uu block = uu.m[blockId];
        block.a(attachedWorld, blockX, blockY, blockZ);
        if (block instanceof vm) {
            int meta = attachedWorld.e(blockX, blockY, blockZ) & 7;
            float f = 0.15f;
            if (meta == 1) {
                block.a(0.0f, 0.2f, 0.5f - f, f * 2.0f, 0.8f, 0.5f + f);
            } else if (meta == 2) {
                block.a(1.0f - f * 2.0f, 0.2f, 0.5f - f, 1.0f, 0.8f, 0.5f + f);
            } else if (meta == 3) {
                block.a(0.5f - f, 0.2f, 0.0f, 0.5f + f, 0.8f, f * 2.0f);
            } else if (meta == 4) {
                block.a(0.5f - f, 0.2f, 1.0f - f * 2.0f, 0.5f + f, 0.8f, 1.0f);
            } else {
                f = 0.1f;
                block.a(0.5f - f, 0.0f, 0.5f - f, 0.5f + f, 0.6f, 0.5f + f);
            }
        }

        eq boundingBox = block.f(attachedWorld, blockX, blockY, blockZ);
        float minX;
        float minY;
        float minZ;
        float maxX;
        float maxY;
        float maxZ;
        if (boundingBox != null) {
            minX = (float) boundingBox.a;
            minY = (float) boundingBox.b;
            minZ = (float) boundingBox.c;
            maxX = (float) boundingBox.d;
            maxY = (float) boundingBox.e;
            maxZ = (float) boundingBox.f;
        } else {
            minX = (float) (blockX + block.bs);
            minY = (float) (blockY + block.bt);
            minZ = (float) (blockZ + block.bu);
            maxX = (float) (blockX + block.bv);
            maxY = (float) (blockY + block.bw);
            maxZ = (float) (blockZ + block.bx);
        }

        RemixParticleOverlayBridge.captureBlockOutline(
                blockX,
                blockY,
                blockZ,
                minX,
                minY,
                minZ,
                maxX,
                maxY,
                maxZ);
    }
}
