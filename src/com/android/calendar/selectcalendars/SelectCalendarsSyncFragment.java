/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.calendar.selectcalendars;

import com.android.calendar.AsyncQueryService;
import com.android.calendar.R;
import com.android.calendar.selectcalendars.SelectCalendarsSyncAdapter.CalendarRow;

import android.accounts.Account;
import android.app.Activity;
import android.app.ListFragment;
import android.app.LoaderManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Calendar;
import android.provider.Calendar.Calendars;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import java.util.HashMap;

public class SelectCalendarsSyncFragment extends ListFragment
        implements View.OnClickListener, LoaderManager.LoaderCallbacks<Cursor> {
    private static final String TAG = "SelectCalendarSync";
    private static final boolean DEBUG = false;

    private static final String COLLATE_NOCASE = " COLLATE NOCASE";
    private static final String SELECTION = Calendars._SYNC_ACCOUNT + "=? AND "
            + Calendars._SYNC_ACCOUNT_TYPE + "=?";
    // is primary lets us sort the user's main calendar to the top of the list
    private static final String IS_PRIMARY = "\"primary\"";
    private static final String SORT_ORDER = IS_PRIMARY + " DESC," + Calendars.DISPLAY_NAME
            + COLLATE_NOCASE;

    private static final String[] PROJECTION = new String[] { Calendars._ID,
            Calendars._SYNC_ACCOUNT, Calendars.OWNER_ACCOUNT, Calendars.DISPLAY_NAME,
            Calendars.COLOR, Calendars.SELECTED, Calendars.SYNC_EVENTS,
            "(" + Calendars._SYNC_ACCOUNT + "=" + Calendars.OWNER_ACCOUNT + ") AS " + IS_PRIMARY, };
    private static final int COLUMN_ID = 0;
    private static final int COLUMN_SYNC_ACCOUNT = 1;
    private static final int COLUMN_OWNER_ACCOUNT = 2;
    private static final int COLUMN_DISPLAY_NAME = 3;
    private static final int COLUMN_COLOR = 4;
    private static final int COLUMN_SELECTED = 5;
    private static final int COLUMN_SYNC_EVENTS = 6;

    private static int mUpdateToken;
    private static int mQueryToken;

    private TextView mSyncStatus;
    private Button mAccountsButton;
    private Account mAccount;
    private String[] mArgs = new String[2];
    private AsyncQueryService mService;

    public SelectCalendarsSyncFragment() {
        Log.d(TAG, "Without bundle was created");
    }

    public SelectCalendarsSyncFragment(Bundle bundle) {
        Log.d(TAG, "With bundle was created");
        mAccount = new Account(
                bundle.getString(Calendars.ACCOUNT_NAME), bundle.getString(Calendars.ACCOUNT_TYPE));
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.account_calendars, null);
        mSyncStatus = (TextView) v.findViewById(R.id.account_status);
        mSyncStatus.setVisibility(View.GONE);

        mAccountsButton = (Button) v.findViewById(R.id.sync_settings);
        mAccountsButton.setVisibility(View.GONE);
        mAccountsButton.setOnClickListener(this);

        return v;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        // Give some text to display if there is no data. In a real
        // application this would come from a resource.
        setEmptyText(getActivity().getText(R.string.no_syncable_calendars));
        // Prepare the loader. Either re-connect with an existing one,
        // or start a new one.
        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!ContentResolver.getMasterSyncAutomatically()
                || !ContentResolver.getSyncAutomatically(mAccount, Calendar.AUTHORITY)) {
            Resources res = getActivity().getResources();
            mSyncStatus.setText(res.getString(R.string.acct_not_synced));
            mSyncStatus.setVisibility(View.VISIBLE);
            mAccountsButton.setText(res.getString(R.string.accounts));
            mAccountsButton.setVisibility(View.VISIBLE);
        } else {
            mSyncStatus.setVisibility(View.GONE);
            mAccountsButton.setVisibility(View.GONE);
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mService = new AsyncQueryService(activity);

        Bundle bundle = getArguments();
        if (bundle != null && bundle.containsKey(Calendars.ACCOUNT_NAME)
                && bundle.containsKey(Calendars.ACCOUNT_TYPE)) {
            mAccount = new Account(bundle.getString(Calendars.ACCOUNT_NAME),
                    bundle.getString(Calendars.ACCOUNT_TYPE));
        }
    }

    @Override
    public void onPause() {
        HashMap<Integer, CalendarRow> changes = ((SelectCalendarsSyncAdapter) getListAdapter())
                .getChanges();
        if (changes != null && changes.size() > 0) {
            for (CalendarRow row : changes.values()) {
                int id = (int) row.id;
                mService.cancelOperation(id);
                // Use the full long id in case it makes a difference
                Uri uri = ContentUris.withAppendedId(Calendars.CONTENT_URI, row.id);
                ContentValues values = new ContentValues();
                // Toggle the current setting
                int synced = row.synced ? 1 : 0;
                values.put(Calendars.SYNC_EVENTS, synced);
                mService.startUpdate(id, null, uri, values, null, null, 0);
            }
            changes.clear();
        }
        super.onPause();
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        mArgs[0] = mAccount.name;
        mArgs[1] = mAccount.type;
        return new CursorLoader(
                getActivity(), Calendars.CONTENT_URI, PROJECTION, SELECTION, mArgs, SORT_ORDER);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        SelectCalendarsSyncAdapter adapter = (SelectCalendarsSyncAdapter) getListAdapter();
        if (adapter == null) {
            adapter = new SelectCalendarsSyncAdapter(getActivity(), data);
        } else {
            adapter.changeCursor(data);
        }
        setListAdapter(adapter);
    }

    // Called when the Accounts button is pressed. Takes the user to the
    // Accounts and Sync settings page.
    @Override
    public void onClick(View v) {
        Intent intent = new Intent();
        intent.setAction("android.settings.SYNC_SETTINGS");
        getActivity().startActivity(intent);
    }
}
