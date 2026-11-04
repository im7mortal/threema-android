package ch.threema.app.utils;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.RemoteException;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.search.SearchBar;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

import org.koin.java.KoinJavaComponent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.MenuCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import ch.threema.app.BuildConfig;
import ch.threema.app.BuildFlavor;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.home.HomeActivity;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.preference.service.SynchronizedSettingsService;
import ch.threema.app.restrictions.AppRestrictionService;
import ch.threema.app.restrictions.AppRestrictions;
import ch.threema.app.services.LockAppService;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.services.license.LicenseService;
import ch.threema.app.threemasafe.ThreemaSafeConfigureActivity;

import static ch.threema.android.PermissionExtensionsKt.checkGrantedPermission;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import static android.content.res.Configuration.UI_MODE_NIGHT_YES;
import static androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO;
import static androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES;

public class ConfigUtils {
    private static final Logger logger = getThreemaLogger("ConfigUtils");

    private static final int CONTENT_PROVIDER_BATCH_SIZE = 50;

    private static Boolean isTablet = null;
    private static int preferredThumbnailWidth = -1, preferredAudioMessageWidth = -1, currentDayNightMode;

    public static boolean isTabletLayout(Context context) {
        if (isTablet != null) {
            return isTablet;
        }
        isTablet = false;

        if (context != null) {
            Resources res = context.getResources();

            if (res != null) {
                isTablet = res.getBoolean(R.bool.tablet_layout);
            }
        }
        return isTablet;
    }

    public static boolean isTabletLayout() {
        Context appContext = ThreemaApplication.getAppContext();
        return isTabletLayout(appContext);
    }

    public static boolean isLandscape(Context context) {
        return context.getResources().getBoolean(R.bool.is_landscape);
    }

    public static boolean isCallsEnabled() {
        ServiceManager serviceManager = ServiceManager.get();

        if (serviceManager != null) {
            return serviceManager.getSynchronizedSettingsService().isVoipEnabled()
                && !getAppRestrictions().isCallsDisabled();
        }
        return true;
    }

    public static boolean isVideoCallsEnabled() {
        ServiceManager serviceManager = ServiceManager.get();

        if (serviceManager != null) {
            return BuildConfig.VIDEO_CALLS_ENABLED
                && serviceManager.getSynchronizedSettingsService().areVideoCallsEnabled()
                && !getAppRestrictions().isVideoCallsDisabled();
        }
        return BuildConfig.VIDEO_CALLS_ENABLED;
    }

    public static boolean isGroupCallsEnabled() {
        final @Nullable ServiceManager serviceManager = ServiceManager.get();
        return serviceManager != null
            && serviceManager.getSynchronizedSettingsService().areGroupCallsEnabled()
            && !getAppRestrictions().isGroupCallsDisabled()
            && !getAppRestrictions().isCallsDisabled();
    }

    public static boolean isWorkDirectoryEnabled() {
        ServiceManager serviceManager = ServiceManager.get();

        if (serviceManager != null) {
            return serviceManager.getPreferenceService().getWorkDirectoryEnabled()
                && !getAppRestrictions().isWorkDirectoryDisabled();
        }
        return false;
    }

    /**
     * Check if multi-device is enabled. This checks the restriction th_disable_multidevice. If not
     * set, then check th_disable_web which is used as fallback when th_disable_multidevice is not
     * set.
     */
    public static boolean isMultiDeviceEnabled() {
        Boolean isMultiDeviceDisabled = getAppRestrictions().isMultiDeviceDisabledOrNull();
        if (isMultiDeviceDisabled != null) {
            return !isMultiDeviceDisabled;
        } else {
            return !getAppRestrictions().isWebDisabled();
        }
    }

    public static boolean isTheDarkSide(@NonNull Context context) {
        if (context != ThreemaApplication.getAppContext()) {
            currentDayNightMode = (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == UI_MODE_NIGHT_YES ? MODE_NIGHT_YES : MODE_NIGHT_NO;
        }
        return currentDayNightMode == MODE_NIGHT_YES;
    }

    /**
     * Store current day/night mode.
     *
     * @param dayNightMode must be either MODE_NIGHT_YES or MODE_NIGHT_NO
     */
    public static void setCurrentDayNightMode(@AppCompatDelegate.NightMode int dayNightMode) {
        currentDayNightMode = dayNightMode;
    }

    /**
     * Get user-facing application version string including alpha/beta version suffix
     */
    public static String getAppVersion() {
        return BuildConfig.VERSION_NAME;
    }

    /**
     * Get build number of this app build, including architecture-specific multipliers.
     * <p>
     * NOTE: This is almost the same as `BuildConfig.VERSION_CODE`. However, the value returned
     * from this function may contain a multiplier depending on the target architecture,
     * while `BuildConfig.VERSION_CODE` always returns just the plain version code. For example:
     * If this function returns `9000936`, then `BuildConfig.VERSION_CODE` would contain just `936`.
     */
    public static int getBuildNumber(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            if (packageInfo != null) {
                return packageInfo.versionCode;
            }
        } catch (PackageManager.NameNotFoundException x) {
            logger.error("Exception", x);
        }
        return 0;
    }

    /**
     * Return information about the device, including the manufacturer and the model.
     */
    public static @NonNull String getDeviceInfo(boolean includeAppVersion) {
        final StringBuilder info = new StringBuilder();
        if (includeAppVersion) {
            info.append(getAppVersion()).append("/");
        }
        info.append(Build.MANUFACTURER).append(";")
            .append(Build.MODEL).append("/")
            .append(Build.VERSION.RELEASE).append("/")
            .append(BuildFlavor.getFullDisplayName());
        return info.toString();
    }

    /**
     * Return information about the device including the manufacturer and the model.
     * The version is NOT included.
     * If mdm parameters are active on this device they are also appended according to ANDR-2213.
     *
     * @return The device info meant to be sent with support requests
     */
    public static @NonNull String getSupportDeviceInfo() {
        final StringBuilder info = new StringBuilder(getDeviceInfo(false));
        if (isWorkRestricted()) {
            AppRestrictionService appRestrictionService = KoinJavaComponent.get(AppRestrictionService.class);
            String mdmSource = appRestrictionService.getMdmSource().getStringRepresentation();
            if (mdmSource != null) {
                info.append("/").append(mdmSource);
            }
        }
        return info.toString();
    }

    public static void recreateActivity(Activity activity) {
        activity.finish();

        final Intent intent = HomeActivity.createIntent(activity);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
    }

    public static boolean isWhitelabelOnPremBuild(@NonNull Context context) {
        return BuildFlavor.IS_ON_PREM && !context.getPackageName().equals("ch.threema.app.onprem");
    }

    public static boolean isDemoOPServer(@NonNull PreferenceService preferenceService) {
        return preferenceService.getOppfUrl() != null && preferenceService.getOppfUrl().toLowerCase().contains(".3ma.ch/");
    }

    /**
     * Returns true if this is a work build and app is under control of a device policy controller (DPC) or Threema MDM.
     * <p>
     * Use {@link AppRestrictions#isRestricted()} instead.
     */
    @Deprecated
    public static boolean isWorkRestricted() {
        return getAppRestrictions().isRestricted();
    }

    /**
     * Warning: This will misleadingly return false if the service manager is not available!
     */
    public static boolean isSerialLicenseValid() {
        ServiceManager serviceManager = ServiceManager.get();
        if (serviceManager != null) {
            // OnPrem needs server info in addition to license
            if (BuildFlavor.IS_ON_PREM && serviceManager.getPreferenceService().getOppfUrl() == null) {
                return false;
            }

            LicenseService<?> licenseService = serviceManager.getLicenseService();
            return isSerialLicensed() && licenseService.hasCredentials() && licenseService.isLicensed();
        }
        return false;
    }

    public static boolean isSerialLicensed() {
        BuildFlavor.LicenseType currentLicenseType = BuildFlavor.getLicenseType();
        return currentLicenseType.equals(BuildFlavor.LicenseType.WORK)
            || currentLicenseType.equals(BuildFlavor.LicenseType.SERIAL);
    }

    public static boolean hasInvalidCredentials() {
        return BuildFlavor.IS_WORK && ConfigUtils.isSerialLicensed() && !ConfigUtils.isSerialLicenseValid();
    }

    // call this before setContentView
    public static void applyScreenshotPolicy(
        @NonNull Activity activity,
        @NonNull SynchronizedSettingsService synchronizedSettingsService,
        @NonNull LockAppService lockAppService
    ) {
        if (lockAppService.isLockingEnabled() || synchronizedSettingsService.areScreenshotsDisabled() ||
            activity instanceof ThreemaSafeConfigureActivity) {
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    /**
     * @return The height (in pixels) of the system status bar or 0 in case the activity is not attached yet
     */
    public static int getStatusBarHeight(final @NonNull Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            final @Nullable WindowInsetsCompat windowInsets = getWindowInsets(activity);
            if (windowInsets == null) {
                return 0;
            }
            final @NonNull Insets staturBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
            return staturBarInsets.top - staturBarInsets.bottom;
        }
        int result = 0;
        @SuppressLint({"InternalInsetResource", "DiscouragedApi"}) final int resourceId = activity.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = activity.getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    /**
     * @return the height (in pixels) of the navigation bar at the bottom of some devices
     */
    public static int getNavigationBarHeight(final @NonNull Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            final @Nullable WindowInsetsCompat windowInsets = getWindowInsets(activity);
            if (windowInsets == null) {
                return 0;
            }
            final @NonNull Insets navigationBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
            return navigationBarInsets.bottom - navigationBarInsets.top;
        }
        if (activity.isInMultiWindowMode()) {
            return 0;
        }
        final @NonNull NavigationBarDimensions dimensions = new NavigationBarDimensions();
        getNavigationBarDimensions(activity.getWindowManager(), dimensions);
        return dimensions.height;
    }

    @Nullable
    private static WindowInsetsCompat getWindowInsets(final @NonNull Activity activity) {
        final @Nullable Window window = activity.getWindow();
        if (window == null) {
            return null;
        }
        final @NonNull View decorView = window.getDecorView();
        return ViewCompat.getRootWindowInsets(decorView);
    }

    @Deprecated
    private static void getNavigationBarDimensions(final @NonNull WindowManager windowManager, @NonNull NavigationBarDimensions dimensions) {
        dimensions.width = dimensions.height = 0;

        DisplayMetrics metrics = new DisplayMetrics();
        // get dimensions of usable display space with decorations (status bar / navigation bar) subtracted
        windowManager.getDefaultDisplay().getMetrics(metrics);
        int usableHeight = metrics.heightPixels;
        int usableWidth = metrics.widthPixels;
        // get dimensions of display without subtracting any decorations
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        int realHeight = metrics.heightPixels;
        int realWidth = metrics.widthPixels;
        if (realHeight > usableHeight)
            dimensions.height = realHeight - usableHeight;

        if (realWidth > usableWidth)
            dimensions.width = realWidth - usableWidth;
    }

    public static class NavigationBarDimensions {
        public int width;
        public int height;
    }

    /**
     * @return The resolved height of attribute {@code R.attr.actionBarSize} in pixels.
     */
    @Px
    public static int getActionBarSize(@NonNull Context context) {
        TypedValue typedValue = new TypedValue();
        if (context.getTheme().resolveAttribute(R.attr.actionBarSize, typedValue, true)) {
            return TypedValue.complexToDimensionPixelSize(typedValue.data, context.getResources().getDisplayMetrics());
        }
        return 0;
    }

    public static void adjustToolbar(Context context, Toolbar toolbar) {
        // adjust toolbar height after rotate
        if (toolbar != null) {
            int size = getActionBarSize(context);
            toolbar.setMinimumHeight(size);
            ViewGroup.LayoutParams lp = toolbar.getLayoutParams();
            lp.height = size;
            toolbar.setLayoutParams(lp);
        }
    }

    /**
     * Determine whether you have been granted the permission for read access to videos and images.
     * Prior to Android 13 READ_EXTERNAL_STORAGE is checked.
     *
     * @param context the context to check the permissions
     * @return true if permissions are already granted, false otherwise
     */
    public static boolean isVideoImagePermissionGranted(@NonNull Context context) {
        return isPartialVideoImagePermissionGranted(context) || isFullVideoImagePermissionGranted(context);
    }

    /**
     * Determine whether you have been fully granted the permission for access to videos and images.
     * Prior to Android 13 READ_EXTERNAL_STORAGE is checked.
     *
     * @param context the context to check the permissions
     * @return true if permissions are already granted, false otherwise
     */
    public static boolean isFullVideoImagePermissionGranted(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return checkGrantedPermission(context, Manifest.permission.READ_MEDIA_VIDEO)
                && checkGrantedPermission(context, Manifest.permission.READ_MEDIA_IMAGES);
        } else {
            return checkGrantedPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE);
        }
    }

    /**
     * Determine whether you have been partially granted the permission for access to videos and images.
     * Partial access was introduced in Android 14 (SDK 34)
     *
     * @param context the context to check the permissions
     * @return true if permission is already granted, false otherwise
     */
    public static boolean isPartialVideoImagePermissionGranted(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return checkGrantedPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED);
        } else {
            return false;
        }
    }

    /**
     * Request all required permissions to read external storage. For Android 13 and higher this
     * requests READ_MEDIA_IMAGES and READ_MEDIA_VIDEO. READ_MEDIA_AUDIO is not requested.
     *
     * @param activity    Activity context for onRequestPermissionsResult callback
     * @param requestCode request code for onRequestPermissionsResult callback
     * @return true if permissions are already granted, false otherwise
     */
    @Deprecated
    public static boolean requestReadStoragePermission(@NonNull Activity activity, Fragment fragment, int requestCode) {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permissions = new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED};
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO};
        } else {
            permissions = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
        }

        if (checkIfNeedsPermissionRequest(activity, permissions)) {
            requestPermissions(activity, fragment, permissions, requestCode);
            return false;
        }
        return true;
    }

    /**
     * Request all possibly required permissions of Storage group. For Android 13 and higher this
     * requests READ_MEDIA_IMAGES and READ_MEDIA_VIDEO. READ_MEDIA_AUDIO is not requested.
     *
     * @param activity    Activity context for onRequestPermissionsResult callback
     * @param requestCode request code for onRequestPermissionsResult callback
     * @return true if permissions are already granted, false otherwise
     */
    @Deprecated
    public static boolean requestStoragePermissions(@NonNull Activity activity, Fragment fragment, int requestCode) {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permissions = new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED};
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO};
        } else {
            permissions = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};
        }

        if (checkIfNeedsPermissionRequest(activity, permissions)) {
            requestPermissions(activity, fragment, permissions, requestCode);
            return false;
        }
        return true;
    }

    /**
     * Request storage write permission
     *
     * @param activity    Activity context for onRequestPermissionsResult callback
     * @param requestCode request code for onRequestPermissionsResult callback
     * @return true if permissions are already granted, false otherwise
     */
    @Deprecated
    public static boolean requestWriteStoragePermissions(@NonNull Activity activity, Fragment fragment, int requestCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // scoped storage
            return true;
        }
        final @NonNull String[] permissions = new String[]{
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        };
        if (checkIfNeedsPermissionRequest(activity, permissions)) {
            requestPermissions(activity, fragment, permissions, requestCode);
            return false;
        }
        return true;
    }

    @Deprecated
    private static void requestPermissions(Activity activity, Fragment fragment, String[] permissions, int requestCode) {
        if (fragment != null) {
            fragment.requestPermissions(permissions, requestCode);
        } else {
            ActivityCompat.requestPermissions(activity, permissions, requestCode);
        }
    }

    private static boolean checkIfNeedsPermissionRequest(@NonNull Context context, @NonNull String[] permissions) {
        for (String permission : permissions) {
            if (!checkGrantedPermission(context, permission)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Show a snackbar explaining the reason why the user should enable a certain permission.
     *
     * @param context        the context; if null, nothing is shown
     * @param parentLayout   the parent layout where the snackbar is placed; if null, a toast is shown
     * @param stringResource the string resource of the message that will be shown
     */
    public static void showPermissionRationale(@Nullable Context context, @Nullable View parentLayout, @StringRes int stringResource) {
        if (context != null) {
            showPermissionRationale(context, parentLayout, context.getString(stringResource), null);
        }
    }

    /**
     * Show a snackbar explaining the reason why the user should enable a certain permission.
     *
     * @param context        the context; if null, nothing is shown
     * @param parentLayout   the parent layout where the snackbar is placed; if null, a toast is shown
     * @param stringResource the string resource of the message that will be shown
     * @param callback       the callback for the snackbar
     */
    public static void showPermissionRationale(
        @Nullable Context context,
        @Nullable View parentLayout,
        @StringRes int stringResource,
        @Nullable BaseTransientBottomBar.BaseCallback<Snackbar> callback
    ) {
        if (context != null) {
            showPermissionRationale(context, parentLayout, context.getString(stringResource), callback);
        }
    }

    /**
     * Show a snackbar explaining the reason why the user should enable a certain permission.
     *
     * @param context      the context; if null, nothing is shown
     * @param parentLayout the parent layout where the snackbar is placed; if null, a toast is shown
     * @param message      the message that is shown
     * @param callback     the callback for the snackbar
     */
    private static void showPermissionRationale(
        @NonNull Context context,
        @Nullable View parentLayout,
        @NonNull String message,
        @Nullable BaseTransientBottomBar.BaseCallback<Snackbar> callback
    ) {
        if (parentLayout == null) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
        } else {
            Snackbar snackbar = SnackbarUtil.make(parentLayout, message, BaseTransientBottomBar.LENGTH_LONG, 5);
            snackbar.setAction(R.string.menu_settings, v -> {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + BuildConfig.APPLICATION_ID));
                context.startActivity(intent);
            });
            if (callback != null) {
                snackbar.addCallback(callback);
            }
            snackbar.show();
        }
    }

    public static int getPreferredThumbnailWidth(Context context, boolean reset) {
        if (preferredThumbnailWidth == -1 || reset) {
            if (context != null) {
                int width = context.getResources().getDisplayMetrics().widthPixels;
                int height = context.getResources().getDisplayMetrics().heightPixels;

                if (ConfigUtils.isTabletLayout()) {
                    width -= context.getResources().getDimensionPixelSize(R.dimen.message_fragment_width);
                }

                // width of thumbnail should be 60% of smallest display width
                preferredThumbnailWidth = (int) ((float) width < height ? width * 0.6f : height * 0.6f);
            }
        }
        return preferredThumbnailWidth;
    }

    public static int getPreferredAudioMessageWidth(Context context, boolean reset) {
        if (preferredAudioMessageWidth == -1 || reset) {
            if (context != null) {
                int width = context.getResources().getDisplayMetrics().widthPixels;
                int height = context.getResources().getDisplayMetrics().heightPixels;

                if (ConfigUtils.isTabletLayout()) {
                    width -= context.getResources().getDimensionPixelSize(R.dimen.message_fragment_width);
                }

                // width of audio message should be 80% of smallest display width
                preferredAudioMessageWidth = (int) ((float) width < height ? width * 0.75f : height * 0.75f);
            }
        }
        return preferredAudioMessageWidth;
    }

    public static int getPreferredImageDimensions(@PreferenceService.ImageScale int imageScale) {
        int maxSize = 0;
        switch (imageScale) {
            case PreferenceService.IMAGE_SCALE_SMALL:
                maxSize = 640;
                break;
            case PreferenceService.IMAGE_SCALE_MEDIUM:
                maxSize = 1024;
                break;
            case PreferenceService.IMAGE_SCALE_LARGE:
                maxSize = 1600;
                break;
            case PreferenceService.IMAGE_SCALE_XLARGE:
                maxSize = 2592;
                break;
            case PreferenceService.IMAGE_SCALE_ORIGINAL:
                maxSize = 65535;
                break;
        }
        return maxSize;
    }

    /**
     * Configure menu to display dividers. Call this in onCreateOptionsMenu()
     */
    @SuppressLint("RestrictedApi")
    public static void addIconsToOverflowMenu(@NonNull Menu menu) {
        MenuCompat.setGroupDividerEnabled(menu, true);

        try {
            // restricted API
            if (menu instanceof MenuBuilder) {
                MenuBuilder menuBuilder = (MenuBuilder) menu;
                menuBuilder.setOptionalIconsVisible(true);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Apply operations to content provider in small batches preventing TransactionTooLargeException
     * TODO(ANDR-4326): Move this to ThreemaRawContactWriter and fix wrong back references in case several batches exist.
     *
     * @param authority                 Authority
     * @param contentProviderOperations Operations to apply in smaller batches
     */
    public static void applyToContentResolverInBatches(@NonNull String authority, ArrayList<ContentProviderOperation> contentProviderOperations) throws OperationApplicationException, RemoteException {
        ContentResolver contentResolver = ThreemaApplication.getAppContext().getContentResolver();

        for (int i = 0; i < contentProviderOperations.size(); i += CONTENT_PROVIDER_BATCH_SIZE) {
            List<ContentProviderOperation> contentProviderOperationsBatch = contentProviderOperations.subList(i, Math.min(contentProviderOperations.size(), i + CONTENT_PROVIDER_BATCH_SIZE));
            contentResolver.applyBatch(authority, new ArrayList<>(contentProviderOperationsBatch));
        }
    }

    /**
     * Adjust padding of SearchView so that the search icon is no longer cut off
     *
     * @param searchView The instance of a appcompat SearchView
     */
    public static void adjustSearchViewPadding(@NonNull SearchView searchView) {
        LinearLayout searchFrame = searchView.findViewById(R.id.search_edit_frame);
        if (searchFrame != null) {
            LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) searchFrame.getLayoutParams();
            if (layoutParams != null) {
                layoutParams.setMargins(0, layoutParams.topMargin, layoutParams.rightMargin, layoutParams.bottomMargin);
                searchFrame.setLayoutParams(layoutParams);
            }
            TextView searchSrcTextView = searchView.findViewById(R.id.search_src_text);
            if (searchSrcTextView != null) {
                searchSrcTextView.setPadding(0, 0, 0, 0);
            }
            searchView.setPadding(0, 0, 0, 0);
        }
    }

    /**
     * Adjust the left margin of a search bar text view so that it matches the search action view's text view
     *
     * @param context   A context
     * @param searchBar The search bar
     */
    public static void adjustSearchBarTextViewMargin(@NonNull Context context, @NonNull SearchBar searchBar) {
        TextView searchBarTextView = searchBar.findViewById(R.id.open_search_bar_text_view);
        if (searchBarTextView != null) {
            try {
                SearchBar.LayoutParams layoutParams = (SearchBar.LayoutParams) searchBarTextView.getLayoutParams();
                layoutParams.setMargins(context.getResources().getDimensionPixelSize(R.dimen.search_bar_text_view_margin), layoutParams.topMargin, layoutParams.rightMargin, layoutParams.bottomMargin);
                searchBarTextView.setLayoutParams(layoutParams);
            } catch (Exception e) {
                logger.debug("Unable to get layout params for search bar");
            }
        }
    }

    /**
     * Query whether the user has enabled background restrictions for this app.
     * The user may chose to do this, if they see that an app is consuming an unreasonable amount of battery while in the background.
     * (Android directs the user to do this - many users are not aware of the consequences)
     * If true, any work that the app tries to do will be aggressively restricted while it is in the background. At a minimum, jobs and alarms will not execute and foreground services cannot be started unless an app activity is in the foreground.
     * Note that these restrictions stay in effect even when the device is charging.
     *
     * @param context A context
     * @return true if background restrictions are enabled
     */
    public static boolean isBackgroundRestricted(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            return activityManager.isBackgroundRestricted();
        } else {
            return false;
        }
    }

    /**
     * Check whether the user has restricted the app from network use when in the background
     *
     * @param context A context
     * @return true if the app cannot access the network when in background
     */
    public static boolean isBackgroundDataRestricted(@NonNull Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        return connectivityManager.getRestrictBackgroundStatus() == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED;
    }

    /**
     * Check whether the app can post full screen notifications (e.g. used for calls)
     *
     * @param context A context
     * @return true if the app is forbidden to use fullscreen notifications
     */
    public static boolean isFullScreenNotificationsDisabled(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(context);
            return !notificationManagerCompat.canUseFullScreenIntent();
        }
        return false;
    }

    /**
     * Check whether the provided mime type hints at an image format that can be displayed with Glide on this Android version
     *
     * @param mimeType Mime type to check
     * @return true if conditions are met
     */
    public static boolean isDisplayableAnimatedImageFormat(@Nullable String mimeType) {
        return (MimeUtil.isWebPFile(mimeType) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) || MimeUtil.isGifFile(mimeType);
    }

    private static AppRestrictions getAppRestrictions() {
        return KoinJavaComponent.get(AppRestrictions.class);
    }
}
