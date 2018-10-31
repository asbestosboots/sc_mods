package snap.ssbypass;

import android.graphics.Bitmap;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookConstructor;
import static de.robv.android.xposed.XposedHelpers.getAdditionalInstanceField;
import static de.robv.android.xposed.XposedHelpers.setAdditionalInstanceField;
import static de.robv.android.xposed.XposedHelpers.setObjectField;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import de.robv.android.xposed.XC_MethodReplacement;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;

import com.google.common.io.ByteStreams;
import com.google.common.io.Closer;
import com.google.common.io.Flushables;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


public class SSBypass implements IXposedHookLoadPackage {
    AtomicBoolean hasHooked = new AtomicBoolean();
    String replaceImageLocation = "/storage/emulated/0/Snapchat/";
    String SaveLocation = "/storage/emulated/0/Saved/";
    BitmapFactory.Options bitmapOptions = new android.graphics.BitmapFactory.Options();

    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.snapchat.android") || hasHooked.getAndSet(true))
            return;

        bitmapOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;

        findAndHookMethod("android.app.Application", lpparam.classLoader, "attach", android.content.Context.class, new XC_MethodHook() { // snapchat is a multidex application, wait for it to be attached
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                findAndHookMethod("arkl", lpparam.classLoader, "a", "gq", Object.class, new XC_MethodReplacement() { // kill screenshot detector
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                        XposedBridge.log("Screenshot detector hooked.");
                        return null;
                    }
                });

                // todo: video sharing
                findAndHookMethod("acti", lpparam.classLoader, "a", Bitmap.class, Integer.class, String.class, long.class, boolean.class, int.class, "fqn$b", new XC_MethodHook() { // image sharing
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        XposedBridge.log("Image taken. Proceeding with hook.");

                        File jpeg = new File(replaceImageLocation + "replace.jpeg");
                        if (jpeg.exists()) {
                            Bitmap replace = BitmapFactory.decodeFile(jpeg.getPath(), bitmapOptions);
                            param.args[0] = rotateBitmap(replace, -90);

                            File findAvailable = new File(replaceImageLocation + "replaced.jpeg");
                            int index = 0;

                            while(findAvailable.exists()) {
                                findAvailable = new File(replaceImageLocation + "replaced" + index++ + ".jpeg");
                            }
                            jpeg.renameTo(findAvailable);
                            XposedBridge.log("Replaced image.");
                        } else XposedBridge.log("Nothing to replace");
                    }
                });

                findAndHookMethod("akuo", lpparam.classLoader, "a", "aprs", String.class, new XC_MethodHook() { // store metadata for direct snap saving
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable { // direct snap sharing
                        Object metadata = param.args[0];

                        String key = getOrCreateKey(metadata);
                        Boolean isVideo = (boolean) XposedHelpers.callMethod(metadata, "bb_");
                        Boolean isZipped = (boolean) XposedHelpers.callMethod(metadata, "bj");

                        String username = (String) XposedHelpers.getObjectField(metadata, "aH");
                        Long timestamp = (long) XposedHelpers.callMethod(metadata, "aF_");

                        Object cryptoHolder = param.getResult();
                        Object encryptor = XposedHelpers.getObjectField(cryptoHolder, "c");

                        setAdditionalInstanceField(encryptor, "SNAP_KEY", key);
                        setAdditionalInstanceField(encryptor, "SNAP_ISVIDEO", isVideo);
                        setAdditionalInstanceField(encryptor, "SNAP_ISZIPPED", isZipped);
                        setAdditionalInstanceField(encryptor, "SNAP_AUTHOR", username);
                        setAdditionalInstanceField(encryptor, "SNAP_TIMESTAMP", timestamp);

                        XposedBridge.log("akuo: " + key);
                        XposedBridge.log("isVideo: " + (isVideo ? "true" : "false"));
                        XposedBridge.log("isZipped: " + (isZipped ? "true" : "false"));
                        XposedBridge.log("Username: " + username);
                        XposedBridge.log("Timestamp: " + timestamp.toString());
                    }
                });

                findAndHookMethod("com.snapchat.android.framework.crypto.CbcEncryptionAlgorithm", lpparam.classLoader, "b", InputStream.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable { // direct snap saving
                        String key = (String) getAdditionalInstanceField(param.thisObject, "SNAP_KEY");
                        if (key == null) return;

                        Boolean isVideo = (boolean) getAdditionalInstanceField(param.thisObject, "SNAP_ISVIDEO");
                        Boolean isZipped = (boolean) getAdditionalInstanceField(param.thisObject, "SNAP_ISZIPPED");
                        String username = (String) getAdditionalInstanceField(param.thisObject, "SNAP_AUTHOR");
                        Long timestamp = (long) getAdditionalInstanceField(param.thisObject, "SNAP_TIMESTAMP");

                        InputStream stream = (InputStream) param.getResult();
                        ByteArrayOutputStream output = new ByteArrayOutputStream();
                        try {
                            ByteStreams.copy(stream, output);
                        } catch (IOException e) {
                            XposedBridge.log(e);
                        }
                        ByteArrayInputStream copiedInputStream = new ByteArrayInputStream(output.toByteArray());
                        param.setResult(copiedInputStream);

                        XposedBridge.log("Got this far:");
                        XposedBridge.log("isVideo: " + (isVideo ? "true" : "false"));
                        XposedBridge.log("isZipped: " + (isZipped ? "true" : "false"));
                        XposedBridge.log("Username: " + username);
                        XposedBridge.log("Timestamp: " + timestamp.toString());
                        XposedBridge.log("End of this far");

                        String readableTimestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS", java.util.Locale.getDefault()).format(timestamp);
                        String savePath = SaveLocation + username + "/" + username + "." + readableTimestamp + key.hashCode() + (isZipped ? ".zip" : (isVideo ? ".mp4" : ".jpg"));

                        File saveFolder = new File(SaveLocation + username);
                        saveFolder.mkdirs();

                        File saveFile = new File(savePath);
                        if (saveFile.exists()) return;

                        streamCopy(output, new FileOutputStream(saveFile));

                        // todo: automatically unzip zipped snaps
                        // Closer closer = Closer.create();
                        //ZipInputStream zip = closer.register(new FileInputStream(saveFile));

                    }
                });

                findAndHookMethod("aqwh", lpparam.classLoader, "a", "ww", int.class, int.class, new XC_MethodHook() { // direct snap saving: prevent type checking from happening when we return a duplicate inputstream
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        setObjectField(param.thisObject, "d", false);
                    }
                });

                XposedBridge.log("Hooked (1.6)");
                XposedBridge.log("Hooked (1.6)");
                XposedBridge.log("Hooked (1.6)");
            }
        });

    }

    private String getOrCreateKey(Object obj) {
        if (obj == null)
            return null;

        String key = (String) XposedHelpers.getAdditionalInstanceField(obj, "SNAP_KEY");

        if (key == null) {
            key = java.util.UUID.randomUUID().toString();
            XposedHelpers.setAdditionalInstanceField(obj, "SNAP_KEY", key);
        }

        return key;
    }


    private Bitmap rotateBitmap(android.graphics.Bitmap src, int ang) { // rotate bitmap to fix image rotation bug when sharing
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        matrix.postRotate(ang);
        return android.graphics.Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), matrix, true);
    }

    public static boolean streamCopy(ByteArrayOutputStream byteOutput, OutputStream targetStream) { // direct snap saving: copy stream
        Closer closer = Closer.create();

        try {
            closer.register(byteOutput);
            closer.register(targetStream);
            byteOutput.writeTo(targetStream);

            Flushables.flushQuietly(byteOutput);
            Flushables.flushQuietly(targetStream);

            return true;
        } catch (IOException e) {
            XposedBridge.log(e);
        } finally {
            try {
                closer.close();
            } catch (IOException e) {
                XposedBridge.log(e);
            }
        }

        return false;
    }

}