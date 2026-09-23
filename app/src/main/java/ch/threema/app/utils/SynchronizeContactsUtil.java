package ch.threema.app.utils;

import android.content.Context;
import android.os.Bundle;
import android.os.UserManager;

public class SynchronizeContactsUtil {
    public static boolean isRestrictedProfile(Context context) {
        UserManager um = (UserManager) context.getSystemService(Context.USER_SERVICE);
        Bundle restrictions = um.getUserRestrictions();
        // cannot add accounts or modify sync profiles
        return restrictions.getBoolean(UserManager.DISALLOW_MODIFY_ACCOUNTS, false);
    }
}
