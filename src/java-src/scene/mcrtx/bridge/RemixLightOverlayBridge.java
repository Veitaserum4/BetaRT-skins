package mcrtx.bridge;

public final class RemixLightOverlayBridge {
    private RemixLightOverlayBridge() {
    }

    public static synchronized void setLightLevelOverlayEnabled(boolean enabled) {
        if (RemixLifecycleBridge.isInitialized()) {
            try {
                nSetLightLevelOverlayEnabled(enabled);
            } catch (Throwable ignored) {
            }
        }
    }

    public static synchronized void submitLightLevelMarkers(int[] markerData, int count) {
        if (RemixLifecycleBridge.isInitialized()) {
            try {
                nSubmitLightLevelMarkers(markerData, count);
            } catch (Throwable ignored) {
            }
        }
    }

    public static synchronized void clearLightLevelMarkers() {
        if (RemixLifecycleBridge.isInitialized()) {
            try {
                nClearLightLevelMarkers();
            } catch (Throwable ignored) {
            }
        }
    }

    private static native void nSetLightLevelOverlayEnabled(boolean enabled);
    private static native void nSubmitLightLevelMarkers(int[] markerData, int count);
    private static native void nClearLightLevelMarkers();
}
