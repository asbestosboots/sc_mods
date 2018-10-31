package snap.ssbypass;

import android.graphics.Bitmap;
import java.io.File;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookConstructor;
import static de.robv.android.xposed.XposedHelpers.getAdditionalInstanceField;
import static de.robv.android.xposed.XposedHelpers.setAdditionalInstanceField;
import static de.robv.android.xposed.XposedHelpers.setObjectField;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import de.robv.android.xposed.XC_MethodReplacement;
import com.google.common.io.Files;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import android.content.Intent;
import java.io.InputStream;
import android.content.Context;
import java.io.OutputStream;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayList;
import java.util.List;
import java.io.IOException;
import java.text.SimpleDateFormat;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import java.util.LinkedHashMap;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closer;
import com.google.common.io.Flushables;
import android.app.Activity;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import android.content.SharedPreferences.Editor;
/*
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;
import com.googlecode.mp4parser.util.Matrix;
*/

public class SSBypass implements IXposedHookLoadPackage {
    AtomicBoolean hasHooked = new AtomicBoolean();
    String replaceLocation = "/storage/emulated/0/Snapchat/";
    String SaveLocation = "/storage/emulated/0/Saved/";
    BitmapFactory.Options bitmapOptions = new android.graphics.BitmapFactory.Options();

    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.snapchat.android") || hasHooked.getAndSet(true))
            return;

        bitmapOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;



        //get username hack (No addy needed ;))

        findAndHookMethod("android.app.Application",lpparam.classLoader, "activity",android.content.Context.class, new XC_MethodHook() { // snapchat is a multidex application, wait for it to be attached
                public String GetUsername(Activity activity){
                    Context mContext;
                    activity.getPreferences(Context.MODE_PRIVATE);
                    //TODO implement xml reader from ST
                    return "test";
                   // return activity(Memes.USERNAME, null);
                }
        });



        findAndHookMethod("android.app.Application", lpparam.classLoader, "attach", android.content.Context.class, new XC_MethodHook() { // snapchat is a multidex application, wait for it to be attached
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {



               // more efficent bypasses justin was here also it hooks both checks
                findAndHookMethod("arkl", lpparam.classLoader, "a", LinkedHashMap.class, XC_MethodReplacement.DO_NOTHING);
                findAndHookMethod("iok", lpparam.classLoader, "a", LinkedHashMap.class, XC_MethodReplacement.DO_NOTHING);

                findAndHookMethod("acti", lpparam.classLoader, "a", Bitmap.class, Integer.class, String.class, long.class, boolean.class, int.class, "fqn$b", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        XposedBridge.log("Image taken. Proceeding with hook.");

                        File jpeg = new File(replaceLocation + "replace.jpeg");
                        if (jpeg.exists()) {
                            Bitmap replace = BitmapFactory.decodeFile(jpeg.getPath(), bitmapOptions);
                            param.args[0] = rotateBitmap(replace, -90);

                            File findAvailable = new File(replaceLocation + "replaced.jpeg");
                            int index = 0;

                            while(findAvailable.exists()) {
                                findAvailable = new File(replaceLocation + "replaced" + index++ + ".jpeg");
                            }
                            jpeg.renameTo(findAvailable);
                            XposedBridge.log("Replaced image.");
                        } else XposedBridge.log("Nothing to replace");
                    }
                });

                class RootDetectorOverrides extends XC_MethodReplacement {

                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                        return false;
                    }

                }
                class RootDetectorStringOverrides extends XC_MethodReplacement {

                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                        return "false";
                    }
                }
                    // Snapchat
                    findAndHookMethod( "arkj", lpparam.classLoader, "b", new RootDetectorOverrides());
                    findAndHookMethod( "arkj", lpparam.classLoader, "c", new RootDetectorOverrides());
                    findAndHookMethod( "arkj", lpparam.classLoader, "d", new RootDetectorOverrides());
                    findAndHookMethod( "arkj", lpparam.classLoader, "e", new RootDetectorOverrides());

                    // Crashlytics
                    findAndHookMethod("bbfl", lpparam.classLoader, "f", Context.class, new RootDetectorOverrides());

                    // Braintree
                    findAndHookMethod("sm", lpparam.classLoader, "a", new RootDetectorStringOverrides());

                    findAndHookMethod("actn", lpparam.classLoader, "a" , Uri.class, int.class, boolean.class, "asmh", long.class, long.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        XposedBridge.log("Video taken. Proceeding with hook.");
                        Uri uri = (Uri) param.args[0];
                        File recordedVideo = new File(uri.getPath());
                        File videoToShare =  new File(replaceLocation + "replace.mp4");

                        // Sometimes, the video rotation is wrong. I wrote some code to rotate it to the correct angle,
                        // however the angle is correct more often than not so I won't use this until I figure out a way to pick whether to rotate our not
                        //
                        // File rotatedShareVideo = rotateMp4File(videoToShare);

                        if (videoToShare.exists()) {
                            Files.copy(videoToShare, recordedVideo);
                            File findAvailable = new File(replaceLocation + "replaced.mp4");
                            int index = 0;

                            while(findAvailable.exists()) {
                                findAvailable = new File(replaceLocation + "replaced" + index++ + ".mp4");
                            }
                            videoToShare.renameTo(findAvailable);
                            XposedBridge.log("Replaced Video.");
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

                        String username = (String) XposedHelpers.callStaticMethod(XposedHelpers.findClass("com.snapchat.android.core.user.UserPrefsImpl", lpparam.classLoader), "N");
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

                // File exportTemp = new File(replaceLocation + "/export_temp.mp4");
                // if (exportTemp.exists()) exportTemp.delete();

                XposedBridge.log("Hooked (1.8)");
                XposedBridge.log("Hooked (1.8)");
                XposedBridge.log("Hooked (1.8)");
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

/*
    private File rotateMp4(File mp4) {
        try {
            Movie movie = MovieCreator.build(mp4.getAbsolutePath());
            movie.setMatrix(Matrix.ROTATE_270);
            File export = new File(replaceLocation + "/export_temp.mp4");

            WritableByteChannel export = new FileOutputStream(export).getChannel();
            new DefaultMp4Builder().build(movie).writeContainer(export);
            return export;
        } catch (IOException e) {
            XposedBridge.log("Failed to rotate shared video");
            XposedBridge.log(e);
        }
    }
*/

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