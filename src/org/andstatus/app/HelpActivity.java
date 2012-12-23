/* 
 * Copyright (C) 2012 yvolk (Yuri Volkov), http://yurivolkov.com
 * Copyright (C) 2008 Torgny Bjers
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

package org.andstatus.app;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.util.Xslt;

import java.text.MessageFormat;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.view.animation.AlphaAnimation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ViewFlipper;

/**
 * @author Torgny & yvolk
 *
 */
public class HelpActivity extends Activity {

	// Constants
	public static final String TAG = "HelpActivity";

    private static final String packageName = MyService.class.getPackage().getName();
	
    /**
     * integer - Index of Help screen to show first
     */
    public static final String EXTRA_HELP_PAGE_ID = packageName + ".HELP_PAGE_ID";
    /**
     * boolean - If the activity is first than we should provide means to start {@link TimelineActivity}
     */
    public static final String EXTRA_IS_FIRST_ACTIVITY = packageName + ".IS_FIRST_ACTIVITY";

    /**
     * Change Log page index
     */
    public static final int HELP_PAGE_CHANGELOG = 6;
    
	// Local objects
	private ViewFlipper mFlipper;
	/**
	 * Stores state of {@link #EXTRA_IS_FIRST_ACTIVITY}
	 */
	private boolean mIsFirstActivity = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		requestWindowFeature(Window.FEATURE_NO_TITLE);

		setContentView(R.layout.help);

		mFlipper = ((ViewFlipper) this.findViewById(R.id.help_flipper));

        if (savedInstanceState != null) {
            mIsFirstActivity = savedInstanceState.getBoolean(EXTRA_IS_FIRST_ACTIVITY, false);
        }
        if (getIntent().hasExtra(EXTRA_IS_FIRST_ACTIVITY)) {
            mIsFirstActivity = getIntent().getBooleanExtra(EXTRA_IS_FIRST_ACTIVITY, mIsFirstActivity);
        }

        try {
            TextView version = (TextView) findViewById(R.id.splash_application_version);
            PackageManager pm = getPackageManager();
            PackageInfo pi = pm.getPackageInfo(getPackageName(), 0);
            version.setText(MessageFormat.format("{0} {1}", new Object[] {pi.packageName, pi.versionName}));
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Unable to obtain package information", e);
        }

        // Show the Change log
		Xslt.toWebView(this, R.id.help_changelog, R.raw.changes, R.raw.changesxsl);
		
		View splashContainer = (View) findViewById(R.id.splash_container);
        splashContainer.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("https://github.com/andstatus/andstatus/wiki"));
                startActivity(intent);
            }
        });
		
		final Button getStarted = (Button) findViewById(R.id.button_help_get_started);
		if (mIsFirstActivity) {
	        getStarted.setOnClickListener(new OnClickListener() {
	            public void onClick(View v) {
	                if (MyAccount.getCurrentMyAccount() == null) {
	                    startActivity(new Intent(HelpActivity.this, PreferencesActivity.class));
	                } else {
	                    startActivity(new Intent(HelpActivity.this, TimelineActivity.class));
	                }
	                finish();
	            }
	        });
		} else {
		    getStarted.setVisibility(View.GONE);
		}

		final Button learn_more = (Button) findViewById(R.id.button_help_learn_more);
		learn_more.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				mFlipper.showNext();
			}
		});
		
		if (getIntent().hasExtra(EXTRA_HELP_PAGE_ID)) {
	        int pageToStart = getIntent().getIntExtra(EXTRA_HELP_PAGE_ID, 0);
		    if (pageToStart > 0) {
		        mFlipper.setDisplayedChild(pageToStart);
		    }
		}
		
        
        AlphaAnimation anim = (AlphaAnimation) AnimationUtils.loadAnimation(HelpActivity.this, R.anim.fade_in);
        findViewById(R.id.help_container).startAnimation(anim);
		
	}

	@Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(EXTRA_IS_FIRST_ACTIVITY, mIsFirstActivity);
    }

    @Override
	protected void onResume() {
		super.onResume();
		// We assume that user pressed back after adding first account
        if ( mIsFirstActivity &&  MyAccount.getCurrentMyAccount() != null ) {
			Intent intent = new Intent(this, TimelineActivity.class);
			startActivity(intent);
			finish();
		}
	}
}
