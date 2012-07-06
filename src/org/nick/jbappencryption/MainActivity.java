package org.nick.jbappencryption;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import android.app.Activity;
import android.content.pm.ContainerEncryptionParams;
import android.content.pm.IPackageInstallObserver;
import android.content.pm.ManifestDigest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.Toast;

public class MainActivity extends Activity implements OnClickListener,
        OnCheckedChangeListener {

    private static final String HMAC_ALGORITHM = "HmacSHA1";

    private static final String ENCRYPTION_ALGORITHM = "AES/CBC/PKCS5Padding";

    private static final String TAG = MainActivity.class.getSimpleName();

    // flag for installPackage()
    public static final int INSTALL_REPLACE_EXISTING = 0x00000002;

    // installPackage() return code
    public static final int INSTALL_SUCCEEDED = 1;

    private Handler handler = new Handler();

    private EditText encryptionKeyText;
    private EditText ivText;
    private EditText apkFileText;
    private CheckBox checkHmacCb;
    private EditText hmacKeyText;
    private EditText hmacText;
    private Button installButton;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        findViews();
    }

    private void findViews() {
        encryptionKeyText = (EditText) findViewById(R.id.encryption_key_text);
        ivText = (EditText) findViewById(R.id.iv_text);
        apkFileText = (EditText) findViewById(R.id.apk_filename_text);
        checkHmacCb = (CheckBox) findViewById(R.id.check_hmac_cb);
        checkHmacCb.setOnCheckedChangeListener(this);
        hmacKeyText = (EditText) findViewById(R.id.hmac_key_text);
        hmacText = (EditText) findViewById(R.id.hmac_tag_text);
        toggleHmacFields(checkHmacCb.isChecked());
        installButton = (Button) findViewById(R.id.install_button);
        installButton.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        try {
            String apkFile = apkFileText.getText().toString();
            if (checkEmpty(apkFile, "APK file name")) {
                return;
            }

            if (checkHmacCb.isChecked()) {
                installEncryptedApkCheckMac(apkFile);
            } else {
                installEncryptedApk(apkFile);
            }
        } catch (InvocationTargetException e) {
            setProgressBarIndeterminateVisibility(false);
            Log.e(TAG, "Error installing APK: "
                    + e.getTargetException().getMessage(), e);
            Toast.makeText(
                    this,
                    "Error installing APK: "
                            + e.getTargetException().getMessage(),
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            setProgressBarIndeterminateVisibility(false);
            Log.e(TAG, "Error installing APK: " + e.getMessage(), e);
            Toast.makeText(this, "Error installing APK: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private boolean checkEmpty(String str, String fieldName) {
        if (str == null || str.length() == 0) {
            Toast.makeText(this, fieldName + " is required.",
                    Toast.LENGTH_SHORT).show();

            return true;
        }

        return false;
    }

    class InstallObserver extends IPackageInstallObserver.Stub {

        @Override
        public void packageInstalled(String packageName, int returnCode)
                throws RemoteException {
            if (returnCode == INSTALL_SUCCEEDED) {
                final String message = "Successfully installed encrypted APK: "
                        + packageName;
                Log.d(TAG, "*********** " + message);
                handler.post(new Runnable() {
                    public void run() {
                        setProgressBarIndeterminateVisibility(false);

                        Toast.makeText(MainActivity.this, message,
                                Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                final String message = "Failed to install " + packageName
                        + " returnCode = " + returnCode;
                Log.e(TAG, "************* " + message);
                handler.post(new Runnable() {
                    public void run() {
                        setProgressBarIndeterminateVisibility(false);

                        Toast.makeText(MainActivity.this, message,
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        }
    }

    private InstallObserver installObserver = new InstallObserver();

    private void installEncryptedApk(String apkpath) throws Exception {
        File apkFile = new File(Environment.getExternalStorageDirectory(),
                apkpath);
        Uri apkUri = Uri.fromFile(apkFile);
        String encryptionKeyHex = encryptionKeyText.getText().toString();
        if (checkEmpty(encryptionKeyHex, "encryption key")) {
            return;
        }
        byte[] key = fromHex(encryptionKeyHex);
        SecretKey secretKey = new SecretKeySpec(key, "AES");
        String ivHex = ivText.getText().toString();
        if (checkEmpty(ivHex, "IV")) {
            return;
        }
        byte[] iv = fromHex(ivHex);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        ContainerEncryptionParams encryptionParams = new ContainerEncryptionParams(
                ENCRYPTION_ALGORITHM, ivSpec, secretKey);

        setProgressBarIndeterminateVisibility(true);
        installPackageWithVerification(apkUri, encryptionParams);
    }

    private void installEncryptedApkCheckMac(String apkpath) throws Exception {
        File apkFile = new File(Environment.getExternalStorageDirectory(),
                apkpath);
        Uri apkUri = Uri.fromFile(apkFile);
        String encryptionKeyHex = encryptionKeyText.getText().toString();
        if (checkEmpty(encryptionKeyHex, "encryption key")) {
            return;
        }
        byte[] key = fromHex(encryptionKeyHex);
        SecretKey secretKey = new SecretKeySpec(key, "AES");

        String ivHex = ivText.getText().toString();
        if (checkEmpty(ivHex, "IV")) {
            return;
        }
        byte[] iv = fromHex(ivHex);
        String encAlgorithm = ENCRYPTION_ALGORITHM;
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        String hmacKeyStr = hmacKeyText.getText().toString();
        if (checkEmpty(hmacKeyStr, "HMAC key")) {
            return;
        }
        SecretKey hmacKey = new SecretKeySpec(hmacKeyStr.getBytes("ASCII"),
                "HMAC");
        String hmacTagHex = hmacText.getText().toString();
        if (checkEmpty(hmacTagHex, "HMAC tag")) {
            return;
        }
        byte[] macTag = fromHex(hmacTagHex);
        int encryptedDataEnd = (int) new File(apkpath).length();
        ContainerEncryptionParams encryptionParams = new ContainerEncryptionParams(
                encAlgorithm, ivSpec, secretKey, HMAC_ALGORITHM, null, hmacKey,
                macTag, 0, 0, encryptedDataEnd);

        setProgressBarIndeterminateVisibility(true);
        installPackageWithVerification(apkUri, encryptionParams);
    }

    // public abstract void installPackageWithVerification(Uri packageURI,
    // IPackageInstallObserver observer, int flags, String installerPackageName,
    // Uri verificationURI, ManifestDigest manifestDigest,
    // ContainerEncryptionParams encryptionParams);
    private void installPackageWithVerification(Uri packageURI,
            ContainerEncryptionParams encryptionParams) throws Exception {
        PackageManager pm = getPackageManager();

        Method installWithVerification = PackageManager.class.getMethod(
                "installPackageWithVerification", new Class<?>[] { Uri.class,
                        IPackageInstallObserver.class, int.class, String.class,
                        Uri.class, ManifestDigest.class,
                        ContainerEncryptionParams.class });

        Uri verificationURI = null;
        ManifestDigest md = null;
        int flags = INSTALL_REPLACE_EXISTING;
        installWithVerification.invoke(pm, new Object[] { packageURI,
                installObserver, flags, "my.market", verificationURI, md,
                encryptionParams });
    }

    private static byte[] fromHex(String digits) {
        digits = digits.trim();
        final int bytes = digits.length() / 2;
        if (2 * bytes != digits.length()) {
            throw new IllegalArgumentException(
                    "Hex string must have an even number of digits");
        }

        byte[] result = new byte[bytes];
        for (int i = 0; i < digits.length(); i += 2) {
            result[i / 2] = (byte) Integer.parseInt(digits.substring(i, i + 2),
                    16);
        }

        return result;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        toggleHmacFields(isChecked);
    }

    private void toggleHmacFields(boolean isChecked) {
        hmacKeyText.setEnabled(isChecked);
        hmacText.setEnabled(isChecked);
    }
}
