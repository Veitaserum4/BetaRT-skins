import mcrtx.bridge.HookProfiler;
import mcrtx.bridge.RemixDynamicEntityBridge;
import mcrtx.bridge.RemixLifecycleBridge;
import mcrtx.bridge.RemixParticleOverlayBridge;

final class RemixDynamicEntitySession {
    private static final int MAX_DYNAMIC_BONES = 256;
    private static final int TILE_ENTITY_ID_NAMESPACE = 0x40000000;

    private static boolean frameActive;
    private static boolean entityActive;
    private static int activeEntityId = -1;
    private static int activeHurtStage;
    private static int activeCreeperFuseStage;
    private static float activeCreeperFuseProgress;
    private static String activeEntityTexture = "";
    private static int nextBoneIndex;
    private static volatile boolean renderingEnabled = true;
    private static boolean loggedHookFailure;
    private static boolean loggedBoneOverflow;

    private RemixDynamicEntitySession() {
    }

    static void ensureFrame() {
        if (frameActive || !RemixLifecycleBridge.isInitialized()) {
            return;
        }

        long beginFrameStartNanos = System.nanoTime();
        RemixDynamicEntityBridge.beginDynamicEntityFrame();
        RemixParticleOverlayBridge.beginDestroyOverlayFrame();
        RemixParticleOverlayBridge.beginBlockOutlineFrame();
        frameActive = true;
        HookProfiler.record(HookProfiler.SIDE_HOOK, "hook.dynamicEntity.ensureFrame.beginFrame",
                System.nanoTime() - beginFrameStartNanos);
    }

    static void onFramePresented() {
        frameActive = false;
        RemixSignCapture.onFramePresented();
    }

    static boolean canCapture() {
        return renderingEnabled && RemixLifecycleBridge.isInitialized();
    }

    static boolean isRenderingEnabled() {
        return renderingEnabled;
    }

    static void setRenderingEnabled(boolean enabled) {
        renderingEnabled = enabled;
        if (enabled) {
            return;
        }

        clearEntityState();
        RemixItemEntityCapture.resetActiveCapture();
        RemixEntityFireCapture.resetActiveCapture();
        RemixSignCapture.resetActiveCapture();
        RemixFirstPersonCapture.resetActiveCapture();
    }

    static void beginEntity(int entityId, int hurtStage, int creeperFuseStage,
            float creeperFuseProgress, String texture) {
        entityActive = true;
        activeEntityId = entityId;
        preparePresentation(hurtStage, creeperFuseStage, creeperFuseProgress);
        activeEntityTexture = texture == null ? "" : texture;
        RemixDynamicEntityBridge.beginDynamicEntity(entityId, hurtStage, creeperFuseStage);
        if (!activeEntityTexture.isEmpty()) {
            RemixDynamicEntityBridge.setDynamicEntityTexture(activeEntityTexture);
        }
    }

    static void prepareAuxiliaryEntity(int hurtStage, int creeperFuseStage, float creeperFuseProgress) {
        preparePresentation(hurtStage, creeperFuseStage, creeperFuseProgress);
    }

    private static void preparePresentation(int hurtStage, int creeperFuseStage, float creeperFuseProgress) {
        activeHurtStage = hurtStage;
        activeCreeperFuseStage = creeperFuseStage;
        activeCreeperFuseProgress = creeperFuseProgress;
        nextBoneIndex = 0;
    }

    static void endEntity() {
        if (!entityActive) {
            return;
        }
        RemixDynamicEntityBridge.endDynamicEntity();
        clearEntityState();
    }

    static void endAuxiliaryEntity() {
        RemixDynamicEntityBridge.endDynamicEntity();
        clearPresentation();
    }

    static void clearEntityState() {
        entityActive = false;
        activeEntityId = -1;
        activeEntityTexture = "";
        clearPresentation();
    }

    private static void clearPresentation() {
        activeHurtStage = 0;
        activeCreeperFuseStage = 0;
        activeCreeperFuseProgress = 0.0f;
        nextBoneIndex = 0;
    }

    static boolean isEntityActive() {
        return entityActive;
    }

    static int activeEntityId() {
        return activeEntityId;
    }

    static int activeHurtStage() {
        return activeHurtStage;
    }

    static int activeCreeperFuseStage() {
        return activeCreeperFuseStage;
    }

    static float activeCreeperFuseProgress() {
        return activeCreeperFuseProgress;
    }

    static String activeEntityTexture() {
        return activeEntityTexture;
    }

    static String activeCaptureTexture() {
        if (entityActive && !activeEntityTexture.isEmpty()) {
            return activeEntityTexture;
        }
        return RemixFirstPersonCapture.activeTexture();
    }

    static void setEntityTexture(String texture) {
        String normalized = texture == null ? "" : texture;
        if (normalized.isEmpty() || normalized.equals(activeEntityTexture)) {
            return;
        }
        activeEntityTexture = normalized;
        RemixDynamicEntityBridge.setDynamicEntityTexture(normalized);
    }

    static void bindEntityTexture(String primaryTexture, String fallbackTexture) {
        if (!entityActive) {
            return;
        }
        String resolvedTexture = normalizeTexturePath(primaryTexture, fallbackTexture);
        if (RemixEntityFireCapture.isActive()) {
            resolvedTexture = RemixEntityFireCapture.textureAlias(
                    resolvedTexture.isEmpty() ? RemixHeldItemCapture.TERRAIN_TEXTURE_PATH : resolvedTexture);
        } else {
            if (activeEntityTexture.startsWith(RemixHeldItemCapture.LAPIS_TEXTURE_ALIAS_PREFIX)
                    || resolvedTexture.startsWith(RemixHeldItemCapture.LAPIS_TEXTURE_ALIAS_PREFIX)) {
                resolvedTexture = RemixHeldItemCapture.lapisTextureAlias(resolvedTexture);
            }
            if (RemixFirstPersonCapture.isShadowCaptureActive()) {
                resolvedTexture = RemixFirstPersonCapture.shadowTextureAlias(resolvedTexture);
            }
        }
        setEntityTexture(resolvedTexture);
    }

    private static final java.util.Set<String> downloadedThisSession = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<String, Boolean>());
    private static final java.util.Set<String> downloadingSkins = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<String, Boolean>());
    private static final java.util.Set<String> existingSkinsCache = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<String, Boolean>());
    private static final java.util.Map<String, String> activeSkinPaths = new java.util.concurrent.ConcurrentHashMap<String, String>();

    static void clearSkinCache() {
        downloadedThisSession.clear();
    }

    private static Object lastWorld = null;

    static String normalizeTexturePath(String primaryTexture, String fallbackTexture) {
        net.minecraft.client.Minecraft mc = mcrtx.bridge.RemixLifecycleBridge.getRememberedMinecraft();
        if (mc != null && mc.f != lastWorld) {
            lastWorld = mc.f;
            clearSkinCache();
        }

        String normalizedPrimary = stripTexturePrefix(primaryTexture);
        if (!normalizedPrimary.isEmpty() && normalizedPrimary.charAt(0) == '/') {
            return normalizedPrimary;
        }
        if (!normalizedPrimary.isEmpty() && normalizedPrimary.startsWith("mcrtx_alias/")) {
            return normalizedPrimary;
        }

        if (!normalizedPrimary.isEmpty() && (normalizedPrimary.startsWith("http://") || normalizedPrimary.startsWith("https://"))) {
            try {
                java.net.URL url = new java.net.URL(normalizedPrimary);
                String path = url.getPath();
                String fileName = path.substring(path.lastIndexOf('/') + 1);
                if (fileName.endsWith(".png")) {
                    String prefix = path.toLowerCase().contains("cloak") ? "cloak_" : "skin_";
                    String ddsFileName = prefix + fileName.substring(0, fileName.length() - 4) + ".dds";
                    
                    final String baseName = prefix + fileName.substring(0, fileName.length() - 4);
                    
                    String activePath = activeSkinPaths.get(normalizedPrimary);
                    boolean fileExists = activePath != null;
                    
                    if (!fileExists) {
                        java.io.File skinsDir = new java.io.File("../libraries/mcrtx_assets/skins");
                        if (!skinsDir.exists()) {
                            skinsDir.mkdirs();
                        }
                        
                        // Look for newest base skin (excluding PBR siblings like _emissive)
                        java.io.File[] existingFiles = skinsDir.listFiles(new java.io.FilenameFilter() {
                            public boolean accept(java.io.File dir, String name) {
                                return name.startsWith(baseName) && name.endsWith(".dds") && !isPbrSibling(name);
                            }
                        });
                        if (existingFiles != null && existingFiles.length > 0) {
                            java.io.File newest = existingFiles[0];
                            for (java.io.File f : existingFiles) {
                                if (f.lastModified() > newest.lastModified()) newest = f;
                            }
                            if (newest.length() > 0) {
                                activePath = "/skins/" + newest.getName();
                                activeSkinPaths.put(normalizedPrimary, activePath);
                                fileExists = true;
                            }
                        }
                    }

                    boolean shouldDownload = !fileExists && !downloadedThisSession.contains(normalizedPrimary);


                    if (shouldDownload && downloadingSkins.add(normalizedPrimary)) {
                        downloadedThisSession.add(normalizedPrimary);
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                System.out.println("[BetaRT] Attempting to download skin from: " + url.toString());
                                try {
                                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                                    conn.setUseCaches(false);
                                    conn.setConnectTimeout(5000);
                                    conn.setReadTimeout(5000);
                                    conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                                    conn.setRequestProperty("Connection", "close");
                                    
                                    if (conn.getResponseCode() == 200) {
                                        java.io.InputStream in = conn.getInputStream();
                                        java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(in);
                                        in.close();
                                        if (image != null) {
                                            java.io.File skinsDir = new java.io.File("../libraries/mcrtx_assets/skins");
                                            if (!skinsDir.exists()) skinsDir.mkdirs();
                                            
                                            // Find previous newest file to migrate any PBR siblings
                                            String previousOldStem = null;
                                            java.io.File[] oldFiles = skinsDir.listFiles(new java.io.FilenameFilter() {
                                                public boolean accept(java.io.File dir, String name) {
                                                    return name.startsWith(baseName) && name.endsWith(".dds") && !isPbrSibling(name);
                                                }
                                            });
                                            if (oldFiles != null && oldFiles.length > 0) {
                                                java.io.File oldest = oldFiles[0];
                                                for (java.io.File f : oldFiles) {
                                                    if (f.lastModified() > oldest.lastModified()) oldest = f;
                                                }
                                                String oldName = oldest.getName();
                                                if (oldName.endsWith(".dds")) {
                                                    previousOldStem = oldName.substring(0, oldName.length() - 4);
                                                }
                                            }
                                            
                                            String newStem = baseName + "_" + System.currentTimeMillis();
                                            String newFileName = newStem + ".dds";
                                            java.io.File ddsFile = new java.io.File(skinsDir, newFileName);
                                            java.io.File tempFile = new java.io.File(ddsFile.getAbsolutePath() + ".tmp");
                                            
                                            saveAsDDS(image, tempFile);
                                            
                                            // Using nio Files.move for reliable overwrite and exception logging
                                            try {
                                                java.nio.file.Files.move(tempFile.toPath(), ddsFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                            } catch (Exception e) {
                                                System.out.println("[BetaRT] Failed to move temp skin file: " + e.getMessage());
                                                tempFile.renameTo(ddsFile); // fallback
                                            }

                                            // Migrate any existing PBR maps from previous stem to new stem
                                            if (previousOldStem != null && !previousOldStem.equals(newStem)) {
                                                final String matchOldStem = previousOldStem;
                                                java.io.File[] pbrFiles = skinsDir.listFiles(new java.io.FilenameFilter() {
                                                    public boolean accept(java.io.File dir, String name) {
                                                        return name.startsWith(matchOldStem) && isPbrSibling(name);
                                                    }
                                                });
                                                if (pbrFiles != null) {
                                                    for (java.io.File pbr : pbrFiles) {
                                                        String suffix = pbr.getName().substring(matchOldStem.length());
                                                        java.io.File newPbr = new java.io.File(skinsDir, newStem + suffix);
                                                        try {
                                                            java.nio.file.Files.copy(pbr.toPath(), newPbr.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                                        } catch (Exception ignored) {}
                                                    }
                                                }
                                            }

                                            // Delete old base skin files (never delete any PBR files)
                                            if (oldFiles != null) {
                                                for (java.io.File f : oldFiles) {
                                                    if (!f.getName().equals(newFileName)) {
                                                        try { f.delete(); } catch(Exception ignored) {}
                                                    }
                                                }
                                            }
                                            
                                            activeSkinPaths.put(normalizedPrimary, "/skins/" + newFileName);
                                            System.out.println("[BetaRT] Successfully downloaded and converted skin: " + newFileName);
                                        } else {
                                            System.out.println("[BetaRT] Failed to parse skin image data from: " + url.toString());
                                        }
                                    } else {
                                        System.out.println("[BetaRT] Failed to download skin from: " + url.toString() + " (HTTP " + conn.getResponseCode() + ")");
                                    }
                                } catch (Exception e) {
                                    System.out.println("[BetaRT] Exception downloading skin from: " + url.toString());
                                    e.printStackTrace();
                                } finally {
                                    downloadingSkins.remove(normalizedPrimary);
                                }
                            }
                        }, "BetaRT-Skin-Downloader").start();
                    }

                    if (fileExists || activeSkinPaths.containsKey(normalizedPrimary)) {
                        return activeSkinPaths.get(normalizedPrimary);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        String normalizedFallback = stripTexturePrefix(fallbackTexture);
        return normalizedFallback.isEmpty() ? "" : normalizedFallback;
    }

    static String stripTexturePrefix(String texturePath) {
        if (texturePath == null || texturePath.isEmpty()) {
            return "";
        }
        String normalized = texturePath;
        while (normalized.startsWith("%clamp%") || normalized.startsWith("%blur%")) {
            if (normalized.startsWith("%clamp%")) {
                normalized = normalized.substring(7);
            } else {
                normalized = normalized.substring(6);
            }
        }
        return normalized;
    }

    private static boolean isPbrSibling(String filename) {
        if (filename == null) {
            return false;
        }
        String lower = filename.toLowerCase();
        return lower.contains("_emissive.")
                || lower.contains("_normal.")
                || lower.contains("_rough.")
                || lower.contains("_roughness.")
                || lower.contains("_metallic.")
                || lower.contains("_metalness.")
                || lower.contains("_height.")
                || lower.contains("_displacement.")
                || lower.contains("_depth.")
                || lower.contains("_ao.")
                || lower.contains("_subsurface.")
                || lower.contains("_transmittance.")
                || lower.contains("_thickness.")
                || lower.contains("_radius.");
    }

    static int stableTileEntityId(int x, int y, int z, int salt) {
        int hash = salt;
        hash = 31 * hash + x;
        hash = 31 * hash + y;
        hash = 31 * hash + z;
        return TILE_ENTITY_ID_NAMESPACE | (hash & 0x3FFFFFFF);
    }

    static int allocateBoneIndex() {
        if (nextBoneIndex >= MAX_DYNAMIC_BONES) {
            if (!loggedBoneOverflow) {
                loggedBoneOverflow = true;
                System.err.println("[mcrtx] dynamic capture exceeded Remix bone limit; skipping excess dynamic geometry");
            }
            return -1;
        }

        int boneIndex = nextBoneIndex;
        nextBoneIndex += 1;
        return boneIndex;
    }

    static void submitBoneTransform(int boneIndex, RemixCameraState.PreciseTransform transform) {
        float[] matrix = transform.matrix;
        RemixDynamicEntityBridge.setDynamicEntityBoneTransform(
                boneIndex,
                matrix[0], matrix[4], matrix[8], transform.x,
                matrix[1], matrix[5], matrix[9], transform.y,
                matrix[2], matrix[6], matrix[10], transform.z);
    }

    static void handleFailure(RuntimeException exception) {
        RemixDynamicEntityBridge.endDynamicEntity();
        if (!loggedHookFailure) {
            loggedHookFailure = true;
            System.err.println("[mcrtx] dynamic entity capture disabled after hook failure");
            exception.printStackTrace();
        }
        clearEntityState();
        RemixItemEntityCapture.resetActiveCapture();
        RemixEntityFireCapture.resetActiveCapture();
        RemixSignCapture.resetActiveCapture();
        RemixFirstPersonCapture.resetActiveCapture();
    }

    private static boolean isOverlayPixel(int x, int y) {
        if (y < 16) {
            return x >= 32 && x < 64; // Hat overlay
        } else if (y >= 32 && y < 48) {
            return true; // Modern Body/Right Arm/Right Leg overlays
        } else if (y >= 48 && y < 64) {
            return (x >= 16 && x < 32) || (x >= 48 && x < 64); // Modern Left Leg/Left Arm overlays
        }
        return false;
    }

    private static void saveAsDDS(java.awt.image.BufferedImage image, java.io.File file) throws Exception {
        int width = image.getWidth();
        int height = image.getHeight();
        byte[] header = new byte[128];
        header[0] = 'D'; header[1] = 'D'; header[2] = 'S'; header[3] = ' ';
        header[4] = 124;
        header[8] = 0x0F; header[9] = 0x10; header[10] = 0x08; header[11] = 0x00;
        header[12] = (byte) (height & 0xFF); header[13] = (byte) ((height >> 8) & 0xFF);
        header[16] = (byte) (width & 0xFF); header[17] = (byte) ((width >> 8) & 0xFF);
        int pitch = width * 4;
        header[20] = (byte) (pitch & 0xFF); header[21] = (byte) ((pitch >> 8) & 0xFF);
        header[76] = 32;
        header[80] = 0x41;
        header[88] = 32;
        header[94] = (byte) 0xFF; // R
        header[97] = (byte) 0xFF; // G
        header[100] = (byte) 0xFF; // B
        header[107] = (byte) 0xFF; // A
        header[109] = 0x10;
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
        try {
            fos.write(header);
            
            byte[] pixelData = new byte[width * height * 4];
            int offset = 0;
            for (int i = 0; i < pixels.length; i++) {
                int argb = pixels[i];
                int x = i % width;
                int y = i / width;
                
                if (!isOverlayPixel(x, y)) {
                    argb |= 0xFF000000;
                }
                
                pixelData[offset++] = (byte) (argb & 0xFF);         // B
                pixelData[offset++] = (byte) ((argb >> 8) & 0xFF);  // G
                pixelData[offset++] = (byte) ((argb >> 16) & 0xFF); // R
                pixelData[offset++] = (byte) ((argb >> 24) & 0xFF); // A
            }
            fos.write(pixelData);
        } finally {
            fos.close();
        }
    }
}
