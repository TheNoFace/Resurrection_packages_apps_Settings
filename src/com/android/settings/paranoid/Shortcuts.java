/*
 * Copyright (C) 2012 ParanoidAndroid Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.paranoid;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.settings.R;
import com.android.settings.ApplicationsDialogPreference;
import com.android.settings.SettingsPreferenceFragment;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

public class Shortcuts extends ApplicationsDialogPreference {

    private static final int TARGET_LIMIT = 6;

    private static final int MENU_ADD = Menu.FIRST;
    private static final int MENU_RESET = MENU_ADD + 1;

    private PreferenceScreen mPreferenceScreen;
    private Context mContext;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mContext = getActivity();

        mPreferenceScreen = getPreferenceManager().createPreferenceScreen(mContext);
        setPreferenceScreen(mPreferenceScreen);

        // Get launch-able applications
        mPackageManager = getPackageManager();
        final Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        mInstalledApps = mPackageManager.queryIntentActivities(mainIntent, 0);
        mAppAdapter = new AppAdapter(mInstalledApps);
        mAppAdapter.update();

        loadApplications();
        setHasOptionsMenu(true);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        ListView listView = getListView();
        listView.setOnItemLongClickListener(new OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                ListView listView = (ListView) parent;
                ListAdapter listAdapter = listView.getAdapter();
                Object obj = listAdapter.getItem(position);
                if (obj != null && obj instanceof View.OnLongClickListener) {
                    View.OnLongClickListener longListener = (View.OnLongClickListener) obj;
                    return longListener.onLongClick(view);
                }
                return false;
            }
        });
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_ADD:
                if(getApplicationsStringArray().size() < TARGET_LIMIT) {
                    final ListView list = new ListView(mContext);
                    list.setAdapter(mAppAdapter);

                    AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
                    builder.setTitle(R.string.choose_app);
                    builder.setView(list);
                    final Dialog dialog = builder.create();

                    list.setOnItemClickListener(new OnItemClickListener() {
                        @Override
                        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                            AppItem info = (AppItem) parent.getItemAtPosition(position);
                            String packageName = info.packageName;
                            if(!getApplicationsStringArray().contains(packageName)) {
                                addPreference(info.title, packageName, info.icon);
                                addApplication(packageName);
                            }
                            dialog.cancel();
                        }
                    });
                    dialog.show();
                } else {
                    Toast.makeText(mContext, R.string.lock_screen_shortcuts_limit, Toast.LENGTH_SHORT).show();
                }
                break;
            case MENU_RESET:
                resetApplications();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.add(0, MENU_ADD, 0, R.string.lock_screen_shortcuts_add)
                .setIcon(R.drawable.ic_menu_add)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
        menu.add(0, MENU_RESET, 0, R.string.lock_screen_shortcuts_reset)
                .setIcon(R.drawable.ic_menu_delete_holo_dark)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
    }

    public class ShorcutPreference extends Preference implements View.OnLongClickListener {

        public ShorcutPreference(Context context) {
            super(context);
        }

        @Override
        public boolean onLongClick(View v) {
            final TextView tView;
            final ShorcutPreference pref = this;
            AlertDialog.Builder builder = new AlertDialog.Builder(mContext);

            if ((v != null) && ((tView = (TextView) v.findViewById(android.R.id.summary)) != null)) {
                builder.setTitle(R.string.dialog_delete_title);
                builder.setMessage(R.string.dialog_delete_message);
                builder.setIconAttribute(android.R.attr.alertDialogIcon);
                builder.setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                removeApplication(tView.getText().toString(), pref);
                            }
                        });
                builder.setNegativeButton(android.R.string.cancel, null);
                builder.create().show();
                return true;
            }

            return false;
        }
    }

    public ArrayList<String> getApplicationsStringArray() {
        String cluster =  Settings.System.getString(getContentResolver(),
                Settings.System.LOCKSCREEN_TARGETS);

        if (cluster == null) {
            return null;
        }

        String[] apps = cluster.split("\\|");

        return new ArrayList<String>(Arrays.asList(apps));
    }

    private void loadApplications() {
        ArrayList<String> apps = getApplicationsStringArray();
        if(apps != null) {
            for(String app: apps) {
                addApplicationPreference(app);
            }
        }
    }

    private void addApplication(String packageName) {
        String apps = Settings.System.getString(getContentResolver(),
                Settings.System.LOCKSCREEN_TARGETS);
        if(apps != null) {
            apps += "|" + packageName;
        } else {
            apps = packageName;
        }

        if(apps.startsWith("|")) {
            apps = apps.substring(1, apps.length());
        }

        Settings.System.putString(getContentResolver(),
            Settings.System.LOCKSCREEN_TARGETS, apps);
    }

    private void removeApplication(String packageName, Preference pref) {
        ArrayList<String> apps = getApplicationsStringArray();
        apps.remove(packageName);
        String newApps = "";

        if(!apps.isEmpty()) {
            for (int i = 0; i < apps.size(); i++) {
                newApps += apps.get(i);

                if(i + 1 < apps.size()) {
                    newApps += "|";
                }
            }
        }

        if(newApps.startsWith("|")) {
            newApps = newApps.substring(1, newApps.length());
        }

        Settings.System.putString(mContext.getContentResolver(),
                Settings.System.LOCKSCREEN_TARGETS, newApps);
        mPreferenceScreen.removePreference(pref);
    }

    private void resetApplications() {
        mPreferenceScreen.removeAll();
        Settings.System.putString(getContentResolver(),
            Settings.System.LOCKSCREEN_TARGETS, "");
    }

    private void addApplicationPreference(String packageName) {
        List<PackageInfo> packages = mPackageManager.getInstalledPackages(0);
        for(int i=0; packages != null && i<packages.size(); i++) {
            PackageInfo p = packages.get(i);
            ApplicationInfo appInfo = p.applicationInfo;
            if (appInfo != null && appInfo.packageName.equals(packageName)) {
                CharSequence label = mPackageManager.getApplicationLabel(appInfo);
                Drawable icon = null;
                try {
                    icon = mPackageManager.getApplicationIcon(packageName);
                } catch(NameNotFoundException e) {
                    // default icon
                }
                addPreference(label, packageName, icon);
            }
        }
    }

    private void addPreference(CharSequence title, String summary, Drawable icon) {
        ShorcutPreference pref = new ShorcutPreference(mContext);
        String packageName = summary;
        pref.setKey(packageName);
        pref.setTitle(title);
        pref.setSummary(packageName);
        if(icon != null) pref.setIcon(icon);
        mPreferenceScreen.addPreference(pref);
    }
}
