/*
 * SPDX-FileCopyrightText: 2022 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 * Notice: Portions of this file are reproduced from work created and shared by Google and used
 *         according to terms described in the Creative Commons 4.0 Attribution License.
 *         See https://developers.google.com/readme/policies for details.
 */

package com.google.android.gms.oss.licenses;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.AsyncTaskLoader;
import androidx.loader.content.Loader;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import org.microg.gms.common.PublicApi;
import org.microg.gms.oss.licenses.LicenseUtil;
import org.microg.gms.oss.licenses.R;

import java.util.List;

/**
 * An Activity used to display a list of all third party licenses in res/raw/third_party_license_metadata generated by
 * oss licenses gradle plugin. Click on each item of the list would invoke {@link OssLicensesActivity} to show the
 * actual content of the license.
 */
@PublicApi
public class OssLicensesMenuActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<List<License>> {
    private static final String TAG = "OssLicensesMenuActivity";
    private static final String EXTRA_TITLE = "title";
    private static final int LOADER_ID = 54321;
    private static String TITLE;

    private ListView listView;
    private boolean destroyed;
    private ArrayAdapter<License> licensesAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        destroyed = false;
        if (TITLE == null) {
            Intent intent = getIntent();
            if (intent != null && intent.hasExtra(EXTRA_TITLE)) {
                TITLE = intent.getStringExtra(EXTRA_TITLE);
                Log.w(TAG, "The intent based title is deprecated. Use OssLicensesMenuActivity.setActivityTitle(title) instead.");
            }
        }
        if (TITLE != null) {
            setTitle(TITLE);
        }
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        if (LicenseUtil.hasLicenses(this)) {
            OssLicensesServiceImpl service = new OssLicensesServiceImpl(this);
            service.getListLayoutPackage(getPackageName()).addOnCompleteListener((layoutPackageTask) -> {
                if (destroyed || isFinishing()) return;

                // Layout
                String layoutPackage = getPackageName();
                if (layoutPackageTask.isSuccessful()) {
                    layoutPackage = layoutPackageTask.getResult();
                }
                Resources resources;
                try {
                    resources = getPackageManager().getResourcesForApplication(layoutPackage);
                } catch (Exception e) {
                    layoutPackage = getPackageName();
                    resources = getResources();
                }
                setContentView(getLayoutInflater().inflate(resources.getXml(resources.getIdentifier("libraries_social_licenses_license_menu_activity", "layout", layoutPackage)), null, false));
                licensesAdapter = new LicensesAdapter(this, getLayoutInflater(), resources, layoutPackage);
                listView = findViewById(resources.getIdentifier("license_list", "id", layoutPackage));
                listView.setAdapter(licensesAdapter);
                listView.setOnItemClickListener((parent, view, position, id) -> {
                    License license = (License) parent.getItemAtPosition(position);
                    Intent intent = new Intent(this, OssLicensesActivity.class);
                    intent.putExtra("license", license);
                    startActivity(intent);
                });
            });
            LoaderManager.getInstance(this).initLoader(LOADER_ID, null, this);
        } else {
            setContentView(R.layout.license_menu_activity_no_licenses);
        }
    }

    private static class LicensesAdapter extends ArrayAdapter<License> {
        private final LayoutInflater layoutInflater;
        private final Resources resources;
        private final String layoutPackage;

        public LicensesAdapter(@NonNull Context context, @NonNull LayoutInflater layoutInflater, @NonNull Resources resources, @NonNull String layoutPackage) {
            super(context, 0);
            this.layoutInflater = layoutInflater;
            this.resources = resources;
            this.layoutPackage = layoutPackage;
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View view, @NonNull ViewGroup parent) {
            if (view == null) {
                view = layoutInflater.inflate(resources.getXml(resources.getIdentifier("libraries_social_licenses_license", "layout", layoutPackage)), null, false);
            }
            TextView textView = view.findViewById(resources.getIdentifier("license", "id", layoutPackage));
            textView.setText(getItem(position).toString());
            return view;
        }
    }

    @NonNull
    @Override
    public Loader<List<License>> onCreateLoader(int id, @Nullable Bundle args) {
        return new AsyncTaskLoader<List<License>>(getApplicationContext()) {
            private List<License> storedData;

            @Nullable
            @Override
            public List<License> loadInBackground() {
                List<License> licenses = LicenseUtil.getLicensesFromMetadata(getContext());
                try {
                    OssLicensesServiceImpl service = new OssLicensesServiceImpl(getContext());
                    Task<List<License>> licensesTask = service.getLicenseList(licenses);
                    return Tasks.await(licensesTask);
                } catch (Exception e) {
                    Log.w(TAG, "Error getting license list from service.", e);
                }
                return licenses;
            }

            @Override
            public void deliverResult(@Nullable List<License> data) {
                this.storedData = data;
                super.deliverResult(data);
            }

            @Override
            protected void onStartLoading() {
                if (storedData != null) {
                    deliverResult(storedData);
                } else {
                    forceLoad();
                }
            }

            @Override
            protected void onStopLoading() {
                cancelLoad();
            }
        };
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        LoaderManager.getInstance(this).destroyLoader(LOADER_ID);
        super.onDestroy();
    }

    @Override
    public void onLoadFinished(@NonNull Loader<List<License>> loader, List<License> data) {
        licensesAdapter.clear();
        licensesAdapter.addAll(data);
        licensesAdapter.notifyDataSetChanged();
    }

    @Override
    public void onLoaderReset(@NonNull Loader<List<License>> loader) {
        licensesAdapter.clear();
        licensesAdapter.notifyDataSetChanged();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Sets the title for {@link OssLicensesMenuActivity}.
     *
     * @param title the title for this activity
     */
    public static void setActivityTitle(String title) {
        OssLicensesMenuActivity.TITLE = title;
    }
}
