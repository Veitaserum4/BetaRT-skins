import mcrtx.bridge.McrtxGameplaySettings;
import mcrtx.bridge.RemixLightOverlayBridge;
import net.minecraft.client.Minecraft;

public final class RemixLightLevelOverlay {
    private static final int RADIUS_HORIZONTAL = 24;
    private static final int RADIUS_VERTICAL = 9;
    private static final int MAX_MARKERS = 8192;
    private static final int[] MARKER_BUFFER = new int[MAX_MARKERS * 4];
    private static int lastMarkerCount = 0;

    private RemixLightLevelOverlay() {
    }

    public static void update(Minecraft minecraft) {
        if (minecraft == null || minecraft.f == null || minecraft.h == null) {
            if (lastMarkerCount > 0) {
                lastMarkerCount = 0;
                RemixLightOverlayBridge.clearLightLevelMarkers();
            }
            return;
        }

        boolean enabled = McrtxGameplaySettings.isLightLevelOverlayEnabled();
        RemixLightOverlayBridge.setLightLevelOverlayEnabled(enabled);

        if (!enabled) {
            if (lastMarkerCount > 0) {
                lastMarkerCount = 0;
                RemixLightOverlayBridge.clearLightLevelMarkers();
            }
            return;
        }

        try {
            fd world = minecraft.f;
            sn player = (sn) minecraft.h;
            int playerX = (int) Math.floor(player.aM);
            int playerY = (int) Math.floor(player.aN);
            int playerZ = (int) Math.floor(player.aO);

            int markerCount = 0;
            int maxRadiusSq = RADIUS_HORIZONTAL * RADIUS_HORIZONTAL;

            for (int dx = -RADIUS_HORIZONTAL; dx <= RADIUS_HORIZONTAL; dx++) {
                for (int dz = -RADIUS_HORIZONTAL; dz <= RADIUS_HORIZONTAL; dz++) {
                    if (dx * dx + dz * dz > maxRadiusSq) {
                        continue;
                    }
                    int x = playerX + dx;
                    int z = playerZ + dz;

                    for (int dy = -RADIUS_VERTICAL; dy <= RADIUS_VERTICAL; dy++) {
                        int y = playerY + dy;
                        if (y <= 1 || y >= 126) {
                            continue;
                        }

                        // 1. Block below (x, y - 1, z) must be a solid opaque cube (not air, glass, ice, bedrock)
                        int belowId = world.a(x, y - 1, z);
                        if (belowId <= 0 || belowId >= uu.m.length || belowId == 20 || belowId == 79 || belowId == 7) {
                            continue;
                        }
                        uu belowBlock = uu.m[belowId];
                        if (belowBlock == null || !belowBlock.d()) {
                            continue;
                        }

                        // 2. Block at (x, y, z) must not be a solid opaque cube and not liquid
                        int hereId = world.a(x, y, z);
                        if (hereId != 0) {
                            if (hereId >= uu.m.length) {
                                continue;
                            }
                            uu hereBlock = uu.m[hereId];
                            if (hereBlock != null && hereBlock.d()) {
                                continue;
                            }
                            ln hereMat = world.f(x, y, z);
                            if (hereMat != null && hereMat.d()) {
                                continue;
                            }
                        }

                        // 3. Block above at (x, y + 1, z) must not be a solid opaque cube
                        int aboveId = world.a(x, y + 1, z);
                        if (aboveId > 0 && aboveId < uu.m.length) {
                            uu aboveBlock = uu.m[aboveId];
                            if (aboveBlock != null && aboveBlock.d()) {
                                continue;
                            }
                        }

                        // 4. Calculate light levels at (x, y, z)
                        int blockLight = world.a(eb.b, x, y, z);
                        if (blockLight > 7) {
                            continue;
                        }

                        int skyLight = world.a(eb.a, x, y, z);
                        int type = (skyLight <= 7) ? 0 : 1;

                        if (markerCount < MAX_MARKERS) {
                            int index = markerCount * 4;
                            MARKER_BUFFER[index + 0] = x;
                            MARKER_BUFFER[index + 1] = y;
                            MARKER_BUFFER[index + 2] = z;
                            MARKER_BUFFER[index + 3] = type;
                            markerCount++;
                        }
                    }
                }
            }

            lastMarkerCount = markerCount;
            RemixLightOverlayBridge.submitLightLevelMarkers(MARKER_BUFFER, markerCount);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
