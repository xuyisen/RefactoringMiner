/*
 * Copyright (C) 2014 Dominik Schürmann <dominik@dominikschuermann.de>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.sufficientlysecure.keychain.ui;

import java.util.ArrayList;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.view.View;
import android.widget.Toast;

import org.sufficientlysecure.keychain.Constants;
import org.sufficientlysecure.keychain.R;
import org.sufficientlysecure.keychain.intents.OpenKeychainIntents;
import org.sufficientlysecure.keychain.ui.base.BaseActivity;


public class DecryptFilesActivity extends BaseActivity {

    /* Intents */
    public static final String ACTION_DECRYPT_DATA = OpenKeychainIntents.DECRYPT_DATA;

    // intern
    public static final String ACTION_DECRYPT_DATA_OPEN = Constants.INTENT_PREFIX + "DECRYPT_DATA_OPEN";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setFullScreenDialogClose(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setResult(Activity.RESULT_CANCELED);
                finish();
            }
        }, false);

        // Handle intent actions
        handleActions(savedInstanceState, getIntent());
    }

    @Override
    protected void initLayout() {
        setContentView(R.layout.decrypt_files_activity);
    }

    /**
     * Handles all actions with this intent
     */
    private void handleActions(Bundle savedInstanceState, Intent intent) {

        // No need to initialize fragments if we are just being restored
        if (savedInstanceState != null) {
            return;
        }

        ArrayList<Uri> uris = new ArrayList<>();

        String action = intent.getAction();

        switch (action) {
            case Intent.ACTION_SEND: {
                // When sending to Keychain Decrypt via share menu
                // Binary via content provider (could also be files)
                // override uri to get stream from send
                action = ACTION_DECRYPT_DATA;
                uris.add(intent.<Uri>getParcelableExtra(Intent.EXTRA_STREAM));
                break;
            }

            case Intent.ACTION_SEND_MULTIPLE: {
                action = ACTION_DECRYPT_DATA;
                uris.addAll(intent.<Uri>getParcelableArrayListExtra(Intent.EXTRA_STREAM));
                break;
            }

            case Intent.ACTION_VIEW:
                // Android's Action when opening file associated to Keychain (see AndroidManifest.xml)
                action = ACTION_DECRYPT_DATA;

                // fallthrough
            default:
                uris.add(intent.getData());

        }

        if (ACTION_DECRYPT_DATA.equals(action)) {
            // Definitely need a data uri with the decrypt_data intent
            if (uris.isEmpty()) {
                Toast.makeText(this, "No data to decrypt!", Toast.LENGTH_LONG).show();
                setResult(Activity.RESULT_CANCELED);
                finish();
            }
            displayListFragment(uris);
            return;
        }

        boolean showOpenDialog = ACTION_DECRYPT_DATA_OPEN.equals(action);
        displayInputFragment(showOpenDialog);

    }

    public void displayInputFragment(boolean showOpenDialog) {
        DecryptFilesInputFragment frag = DecryptFilesInputFragment.newInstance(showOpenDialog);

        // Add the fragment to the 'fragment_container' FrameLayout
        // NOTE: We use commitAllowingStateLoss() to prevent weird crashes!
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.decrypt_files_fragment_container, frag)
                .commit();
    }

    public void displayListFragment(ArrayList<Uri> inputUris) {

        DecryptFilesListFragment frag = DecryptFilesListFragment.newInstance(inputUris);

        FragmentManager fragMan = getSupportFragmentManager();

        FragmentTransaction trans = fragMan.beginTransaction();
        trans.replace(R.id.decrypt_files_fragment_container, frag);

        // if there already is a fragment, allow going back to that. otherwise, we're top level!
        if (fragMan.getFragments() != null && !fragMan.getFragments().isEmpty()) {
            trans.addToBackStack("list");
        }

        trans.commit();

    }

}
