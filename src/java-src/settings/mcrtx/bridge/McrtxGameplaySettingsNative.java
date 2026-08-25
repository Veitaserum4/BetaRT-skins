package mcrtx.bridge;

public final class McrtxGameplaySettingsNative {
    private McrtxGameplaySettingsNative() {
    }

    public static void setPlayerShadowsEnabled(boolean enabled) {
        if (ready()) {
            try { nSetPlayerShadowsEnabled(enabled); } catch (Throwable ignored) {}
        }
    }
    public static void setFirstPersonBodyEnabled(boolean enabled) {
        if (ready()) {
            try { nSetFirstPersonBodyEnabled(enabled); } catch (Throwable ignored) {}
        }
    }
    public static void setHeldTorchLightsEnabled(boolean enabled) {
        if (ready()) {
            try { nSetHeldTorchLightsEnabled(enabled); } catch (Throwable ignored) {}
        }
    }
    public static void setBlockOutlineEnabled(boolean enabled) {
        if (ready()) {
            try { nSetBlockOutlineEnabled(enabled); } catch (Throwable ignored) {}
        }
    }
    public static void setBlockOutlineStyle(int style) {
        if (ready()) {
            try { nSetBlockOutlineStyle(style); } catch (Throwable ignored) {}
        }
    }
    public static void setBlockOutlineEmissiveIntensity(float intensity) {
        if (ready()) {
            try { nSetBlockOutlineEmissiveIntensity(intensity); } catch (Throwable ignored) {}
        }
    }
    public static void setViewModelFovDegrees(int fovDegrees) {
        if (ready()) {
            try { nSetViewModelFovDegrees((float) fovDegrees); } catch (Throwable ignored) {}
        }
    }

    private static boolean ready() {
        return RemixBridgeNative.isAvailable() && RemixLifecycleBridge.isInitialized();
    }

    private static native void nSetPlayerShadowsEnabled(boolean enabled);
    private static native void nSetFirstPersonBodyEnabled(boolean enabled);
    private static native void nSetHeldTorchLightsEnabled(boolean enabled);
    private static native void nSetBlockOutlineEnabled(boolean enabled);
    private static native void nSetBlockOutlineStyle(int style);
    private static native void nSetBlockOutlineEmissiveIntensity(float intensity);
    private static native void nSetViewModelFovDegrees(float fovYDegrees);
}
