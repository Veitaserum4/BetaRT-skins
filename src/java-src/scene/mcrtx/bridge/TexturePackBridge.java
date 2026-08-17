package mcrtx.bridge;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class TexturePackBridge {
    private TexturePackBridge() {}

    private static File getTexturePackFile(Object texturePackBase) {
        if (texturePackBase == null) return null;
        try {
            for (Field field : texturePackBase.getClass().getDeclaredFields()) {
                if (field.getType() == File.class) {
                    field.setAccessible(true);
                    return (File) field.get(texturePackBase);
                }
            }
            if (texturePackBase.getClass().getSuperclass() != null) {
                for (Field field : texturePackBase.getClass().getSuperclass().getDeclaredFields()) {
                    if (field.getType() == File.class) {
                        field.setAccessible(true);
                        return (File) field.get(texturePackBase);
                    }
                }
            }
        } catch (Exception e) {}
        return null;
    }

    public static void onGameBooted() {
        try {
            net.minecraft.client.Minecraft mc = RemixLifecycleBridge.getRememberedMinecraft();
            if (mc == null) return;
            
            Object texturePackList = null;
            for (Field field : mc.getClass().getDeclaredFields()) {
                if (field.getType().getSimpleName().equals("ik")) {
                    field.setAccessible(true);
                    texturePackList = field.get(mc);
                    break;
                }
            }
            
            if (texturePackList != null) {
                for (Field field : texturePackList.getClass().getDeclaredFields()) {
                    field.setAccessible(true);
                    Object pack = field.get(texturePackList);
                    if (pack != null && getTexturePackFile(pack) != null) {
                        // Check if this is the currently selected pack by verifying it's not the default pack class
                        if (!pack.getClass().getSimpleName().equals("i")) {
                            onTexturePackChanged(pack);
                            return;
                        }
                    }
                }
                onTexturePackChanged(null);
            }
        } catch (Exception e) {
            System.err.println("[mcrtx] Failed to check texture pack on boot:");
            e.printStackTrace();
        }
    }

    public static void onTexturePackChanged(Object texturePackBase) {
        System.out.println("[mcrtx] Texture pack changed: " + texturePackBase);
        
        
        if (texturePackBase == null) {
            System.out.println("[mcrtx] Default texture pack selected, cache cleared.");
            new File(new File("mcrtx_texturepack_cache"), "current.txt").delete();
            RemixSceneBridge.reloadMaterials();
            return;
        }
        
        try {
            File tpFile = getTexturePackFile(texturePackBase);
            
            if (tpFile == null || !tpFile.getName().endsWith(".zip")) {
                System.out.println("[mcrtx] Default texture pack selected (no zip), cache cleared.");
                new File(new File("mcrtx_texturepack_cache"), "current.txt").delete();
                RemixSceneBridge.reloadMaterials();
                return;
            }
            
            String zipName = tpFile.getName();
            if (zipName.endsWith(".zip")) {
                zipName = zipName.substring(0, zipName.length() - 4);
            }
            String currentId = zipName + "_" + System.currentTimeMillis();
            
            File cacheBase = new File("mcrtx_texturepack_cache");
            cacheBase.mkdirs();
            
            File[] oldFiles = cacheBase.listFiles();
            if (oldFiles != null) {
                for (File f : oldFiles) {
                    if (f.isDirectory()) deleteDirectory(f);
                }
            }
            
            File cacheDir = new File(cacheBase, currentId);
            cacheDir.mkdirs();
            
            int extractedCount = 0;
            try (ZipFile zipFile = new ZipFile(tpFile)) {
                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (!entry.isDirectory() && isPbrAsset(name)) {
                        File outFile = new File(cacheDir, name);
                        outFile.getParentFile().mkdirs();
                        try (InputStream is = zipFile.getInputStream(entry);
                             FileOutputStream fos = new FileOutputStream(outFile)) {
                            byte[] buffer = new byte[8192];
                            int read;
                            while ((read = is.read(buffer)) != -1) {
                                fos.write(buffer, 0, read);
                            }
                        }
                        extractedCount++;
                    }
                }
            }
            
            try (FileOutputStream fos = new FileOutputStream(new File(cacheBase, "current.txt"))) {
                fos.write(currentId.getBytes());
            }
            
            System.out.println("[mcrtx] Extracted " + extractedCount + " PBR maps to cache: " + currentId);
        } catch (Exception e) {
            System.err.println("[mcrtx] Failed to extract texture pack PBR maps:");
            e.printStackTrace();
        }
        
        RemixSceneBridge.reloadMaterials();
    }
    
    private static boolean isPbrAsset(String name) {
        if (name.startsWith("mcrtx_assets")) return true;
        String lower = name.toLowerCase();
        return lower.endsWith(".dds") ||
               lower.endsWith("_normal.png") ||
               lower.endsWith("_roughness.png") ||
               lower.endsWith("_emissive.png") ||
               lower.endsWith("_metallic.png") ||
               lower.endsWith("_height.png") ||
               lower.endsWith("_transmittance.png") ||
               lower.endsWith("_thickness.png") ||
               lower.endsWith("_radius.png") ||
               lower.endsWith("_singlescatteringalbedo.png") ||
               lower.endsWith("water.png") ||
               lower.endsWith("fire.png") ||
               lower.endsWith("lava.png") ||
               lower.endsWith("portal.png") ||
               lower.endsWith("clouds.png") ||
               lower.endsWith("particles.png") ||
               lower.endsWith("rain.png");
    }
    
    private static void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        dir.delete();
    }
}
