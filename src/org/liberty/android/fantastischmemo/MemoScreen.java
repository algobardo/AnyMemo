/*
Copyright (C) 2010 Haowen Ning

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.

*/
package org.liberty.android.fantastischmemo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Date;

import android.graphics.Color;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.os.Bundle;
import android.os.Environment;
import android.content.Context;
import android.preference.PreferenceManager;
import android.text.Html;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Display;
import android.view.WindowManager;
import android.view.LayoutInflater;
import android.widget.Button;
import android.os.Handler;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Toast;
import android.util.Log;
import android.os.SystemClock;
import android.net.Uri;


public class MemoScreen extends MemoScreenBase implements View.OnClickListener, View.OnLongClickListener{
	private ArrayList<Item> learnQueue;
    /* prevItem is used to undo */
    private Item prevItem = null;
    private int prevScheduledItemCount;
    private int prevNewItemCount;
    /* How many words to learn at a time (rolling) */
	private final int WINDOW_SIZE = 10;
	private int maxNewId;
	private int maxRetId;
	private int scheduledItemCount;
	private int newItemCount;
	private TTS questionTTS;
	private TTS answerTTS;
    private Context mContext;
    private Handler mHandler;
    private SpeakWord mSpeakWord;
    private boolean learnAhead;
    /* Six grading buttons */
	private Button[] btns = {null, null, null, null, null, null}; 

	private boolean initFeed;

    public final static String TAG = "org.liberty.android.fantastischmemo.MemoScreen";

    @Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.memo_screen);
		
		Bundle extras = getIntent().getExtras();
		if (extras != null) {
            learnAhead = extras.getBoolean("learn_ahead");
		}
		initFeed = true;
		
        mHandler = new Handler();
        mContext = this;
        createButtons();
        for(Button btn : btns){
            btn.setOnClickListener(this);
            btn.setOnLongClickListener(this);
        }
        LinearLayout root = (LinearLayout)findViewById(R.id.memo_screen_root);

        root.setOnClickListener(this);
        root.setOnLongClickListener(this);
        /* This is a workaround for the unknown change of the 
         * behavior in Android 2.2. 
         * If this event is not set, it will not handle the event
         * of the root
         */
		TextView answerView = (TextView) findViewById(R.id.answer);
        answerView.setOnClickListener(this);


        mProgressDialog = ProgressDialog.show(this, getString(R.string.loading_please_wait), getString(R.string.loading_database), true);

		
            Thread loadingThread = new Thread(){
                public void run(){
                    /* Pre load cards (The number is specified in Window size varable) */
                    final boolean isPrepared = prepare();
                    mHandler.post(new Runnable(){
                        public void run(){
                            mProgressDialog.dismiss();
                            if(isPrepared == false){
                                new AlertDialog.Builder(mContext)
                                    .setTitle(getString(R.string.open_database_error_title))
                                    .setMessage(getString(R.string.open_database_error_message))
                                    .setPositiveButton(getString(R.string.back_menu_text), new OnClickListener() {
                                        public void onClick(DialogInterface arg0, int arg1) {
                                            finish();
                                        }
                                    })
                                    .setNegativeButton(getString(R.string.help_button_text), new OnClickListener() {
                                        public void onClick(DialogInterface arg0, int arg1) {
                                            Intent myIntent = new Intent();
                                            myIntent.setAction(Intent.ACTION_VIEW);
                                            myIntent.addCategory(Intent.CATEGORY_BROWSABLE);
                                            myIntent.setData(Uri.parse(getString(R.string.website_help_error_open)));
                                            startActivity(myIntent);
                                            finish();

                                        }
                                    })
                                    .create()
                                    .show();
                            }

                        }
                    });
                }
            };
            loadingThread.start();

	}


    @Override
	public void onDestroy(){
		super.onDestroy();
        try{
            dbHelper.close();
        }
        catch(Exception e){
        }
		if(questionTTS != null){
			questionTTS.shutdown();
		}
		if(answerTTS != null){
			answerTTS.shutdown();
		}
	}

    @Override
    protected void restartActivity(){
        /* restart the current activity */
        Intent myIntent = new Intent();
        myIntent.setClass(MemoScreen.this, MemoScreen.class);
        myIntent.putExtra("dbname", dbName);
        myIntent.putExtra("dbpath", dbPath);
        myIntent.putExtra("active_filter", activeFilter);
        finish();
        startActivity(myIntent);
    }

	
    @Override
	public boolean onCreateOptionsMenu(Menu menu){
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.memo_screen_menu, menu);
		return true;
	}
	
    @Override
	public boolean onOptionsItemSelected(MenuItem item) {
        Intent myIntent = new Intent();
	    switch (item.getItemId()) {
	    case R.id.menu_memo_help:
            myIntent.setAction(Intent.ACTION_VIEW);
            myIntent.addCategory(Intent.CATEGORY_BROWSABLE);
            myIntent.setData(Uri.parse(getString(R.string.website_help_memo)));
            startActivity(myIntent);
	        return true;
	    case R.id.menuspeakquestion:
	    	if(questionTTS != null){
	    		questionTTS.sayText(this.currentItem.getQuestion());
	    	}
	    	else if(questionUserAudio){
	    		mSpeakWord.speakWord(currentItem.getQuestion());
	    	}
	    	return true;
	    	
	    case R.id.menuspeakanswer:
	    	if(answerTTS != null){
	    		answerTTS.sayText(this.currentItem.getAnswer());
	    	}
	    	else if(answerUserAudio){
	    		mSpeakWord.speakWord(currentItem.getAnswer());
	    	}
	    	return true;
	    	
	    case R.id.menusettings:
    		myIntent.setClass(this, SettingsScreen.class);
    		myIntent.putExtra("dbname", this.dbName);
    		myIntent.putExtra("dbpath", this.dbPath);
    		startActivityForResult(myIntent, 1);
    		//finish();
    		return true;
	    	
	    case R.id.menudetail:
    		myIntent.setClass(this, DetailScreen.class);
    		myIntent.putExtra("dbname", this.dbName);
    		myIntent.putExtra("dbpath", this.dbPath);
    		myIntent.putExtra("itemid", currentItem.getId());
    		startActivityForResult(myIntent, 2);
    		return true;

        case R.id.menuundo:
            if(prevItem != null){
                try{
                    currentItem = (Item)prevItem.clone();
                }
                catch(CloneNotSupportedException e){
                    Log.e(TAG, "Can not clone", e);
                }
                prevItem = null;
                learnQueue.add(0, currentItem);
                if(learnQueue.size() >= WINDOW_SIZE){
                    learnQueue.remove(learnQueue.size() - 1);
                }
                newItemCount = prevNewItemCount;
                scheduledItemCount = prevScheduledItemCount;
                showAnswer = false;
                updateMemoScreen();
                autoSpeak();
            }
            else{
                new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.undo_fail_text))
                    .setMessage(getString(R.string.undo_fail_message))
                    .setNeutralButton(R.string.ok_text, null)
                    .create()
                    .show();
            }
            return true;

        case R.id.menu_memo_filter:
            doFilter();
            return true;

	    }
	    	
	    return false;
	}

    @Override
	protected boolean prepare() {
		/* Empty the queue, init the db */
        if(dbHelper == null){
            try{
                dbHelper = new DatabaseHelper(mContext, dbPath, dbName);
            }
            catch(Exception e){
                Log.e(TAG, "Error" + e.toString(), e);
                return false;
            }

        }
		learnQueue = new ArrayList<Item>();
        maxNewId = -1;
        maxRetId = -1;
		scheduledItemCount = dbHelper.getScheduledCount();
		newItemCount = dbHelper.getNewCount();
		loadSettings();
		/* Get question and answer locale */
		Locale ql;
		Locale al;
		if(questionLocale.equals("US")){
			ql = Locale.US;
		}
		else if(questionLocale.equals("DE")){
			ql = Locale.GERMAN;
		}
		else if(questionLocale.equals("UK")){
			ql = Locale.UK;
		}
		else if(questionLocale.equals("FR")){
			ql = Locale.FRANCE;
		}
		else if(questionLocale.equals("IT")){
			ql = Locale.ITALY;
		}
		else if(questionLocale.equals("ES")){
			ql = new Locale("es", "ES");
		}
		else if(questionLocale.equals("User Audio")){
			this.questionUserAudio= true;
			ql = null;
		}
		else{
			ql = null;
		}
		if(answerLocale.equals("US")){
			al = Locale.US;
		}
		else if(answerLocale.equals("DE")){
			al = Locale.GERMAN;
		}
		else if(answerLocale.equals("UK")){
			al = Locale.UK;
		}
		else if(answerLocale.equals("FR")){
			al = Locale.FRANCE;
		}
		else if(answerLocale.equals("IT")){
			al = Locale.ITALY;
		}
		else if(answerLocale.equals("ES")){
			al = new Locale("es", "ES");
		}
		else if(answerLocale.equals("User Audio")){
			this.answerUserAudio = true;
			al = null;
		}
		else{
			al = null;
		}
		if(ql != null){
			this.questionTTS = new TTS(this, ql);
		}
		else{
			this.questionTTS = null;
		}
		if(al != null){
			this.answerTTS = new TTS(this, al);
		}
		else{
			this.answerTTS = null;
		}
		if(questionUserAudio || answerUserAudio){
			mSpeakWord = new SpeakWord(Environment.getExternalStorageDirectory().getAbsolutePath() + getString(R.string.default_audio_dir));
		}
		
		if(this.feedData() == 2){ // The queue is still empty
            mHandler.post(new Runnable(){
                @Override
                public void run(){
                    new AlertDialog.Builder(mContext)
                        .setTitle(getString(R.string.memo_no_item_title))
                        .setMessage(getString(R.string.memo_no_item_message))
                        .setPositiveButton(getString(R.string.back_menu_text),new OnClickListener() {
                            /* Finish the current activity and go back to the last activity.
                             *It should be the main screen.
                             */
                            public void onClick(DialogInterface arg0, int arg1) {
                                finish();
                            }
                        })
                        .setNegativeButton(getString(R.string.learn_ahead), new OnClickListener(){
                        public void onClick(DialogInterface arg0, int arg1) {
                            finish();
                            Intent myIntent = new Intent();
                            myIntent.setClass(MemoScreen.this, MemoScreen.class);
                            myIntent.putExtra("dbname", dbName);
                            myIntent.putExtra("dbpath", dbPath);
                            myIntent.putExtra("learn_ahead", true);
                            startActivity(myIntent);
                        }
                    })
                        .create()
                        .show();
                    
                }
            });
		}
		else{
            // When feeding is done, update the screen

			
            mHandler.post(new Runnable(){
                @Override
                public void run(){
			        updateMemoScreen();
                    autoSpeak();
                }
            });

		}
        return true;
		
	}
	

    @Override
	protected int feedData() {
		if(initFeed){
			initFeed = false;
			
            boolean feedResult;
            if(learnAhead){
                feedResult = dbHelper.getListItems(-1, WINDOW_SIZE, learnQueue, 3, activeFilter);
            }
            else{
                feedResult = dbHelper.getListItems(-1, WINDOW_SIZE, learnQueue, 4, activeFilter);
            }
			if(feedResult == true){
                for(int i = 0; i < learnQueue.size(); i++){
                    Item qItem = learnQueue.get(i);
                    if(qItem.isScheduled()){
                        if(maxRetId < qItem.getId()){
                            maxRetId = qItem.getId();
                        }
                    }
                    else{
                        if(maxNewId < qItem.getId()){
                            maxNewId = qItem.getId();
                        }
                    }

                }
				return 0;
			}
			else{
                return 2;
			}
			
		}
		else{
            Item item;
            if(!learnAhead){
                setTitle(getString(R.string.stat_scheduled) + scheduledItemCount + " / " + getString(R.string.stat_new) + newItemCount);
            }
            else{
                setTitle(getString(R.string.learn_ahead));
            }
            for(int i = learnQueue.size(); i < WINDOW_SIZE; i++){
                if(learnAhead){
                    /* Flag = 3 for randomly choose item from future */
                    item = dbHelper.getItemById(0, 3, activeFilter);
                    learnQueue.add(item);
                }
                else{
                    for(int j = 0; j < learnQueue.size(); j++){
                        Item qItem = learnQueue.get(j);
                        if(qItem.isScheduled()){
                            if(maxRetId < qItem.getId()){
                                maxRetId = qItem.getId();
                            }
                        }
                        else{
                            if(maxNewId < qItem.getId()){
                                maxNewId = qItem.getId();
                            }
                        }

                    }
                    item = dbHelper.getItemById(maxRetId + 1, 2, activeFilter); // Revision first
                    if(item != null){
                        Log.v(TAG, "GET GET 1111: " + item.getId());
                        maxRetId = item.getId();
                    }
                    else{
                        item = dbHelper.getItemById(maxNewId + 1, 1, activeFilter); // Then learn new if no revision.
                        if(item != null){
                            Log.v(TAG, "GET GET 2222: " + item.getId());
                            maxNewId = item.getId();
                        }
                    }
                    if(item != null){
                        Log.v(TAG, "GET GET 3333: " + item.getId());
                        learnQueue.add(item);
                    }
                    else{
                        break;
                    }
                }
            }
            for (int i = 0; i < learnQueue.size(); i++){
                    Log.v(TAG, "Queue " + i + " id " + learnQueue.get(i).getId());
            }
            Log.v(TAG, "maxRetId " + maxRetId);
            Log.v(TAG, "maxNewId " + maxNewId);
            //if(learnQueue.size() == 0 && !learnAhead){
            //    /* Workaround for the db inconsistent by the filter function
            //     * It refill the items from the beginning
            //     */
            //    scheduledItemCount = dbHelper.getScheduledCount();
            //    newItemCount = dbHelper.getNewCount();
            //    if((scheduledItemCount + newItemCount) != 0){
            //        dbHelper.getListItems(-1, WINDOW_SIZE, learnQueue, 4, activeFilter);
            //    }
            //    if(learnQueue.size() != 0){
            //        idMaxSeen = learnQueue.get(0).getId();
            //    }

            //}
            switch(learnQueue.size()){
            case 0: // No item in queue
                return 2;
            case WINDOW_SIZE: // Queue full
                return 0;
            default: // There are some items in the queue
                return 1;
                    
            }
		}
	}
			
			
    @Override
    public void onClick(View v){
        if(v == (LinearLayout)findViewById(R.id.memo_screen_root) || v== (TextView) findViewById(R.id.answer)){
            /* Handle the short click of the whole screen */
			if(this.showAnswer == false){
				this.showAnswer ^= true;
				updateMemoScreen();
                autoSpeak();
			}
        }

        for(int i = 0; i < btns.length; i++){
            if(v == btns[i]){
                /* i is also the grade for the button */
                int grade = i;
                /* When user click on the button of grade, it will update the item information
                 * according to the grade.
                 * If the return value is success, the user will not need to see this item today.
                 * If the return value is failure, the item will be appended to the tail of the queue. 
                 * */

                prevScheduledItemCount = scheduledItemCount;
                prevNewItemCount = newItemCount;

                try{
                    prevItem = (Item)currentItem.clone();
                }
                catch(CloneNotSupportedException e){
                    Log.e(TAG, "Can not clone", e);
                }


                boolean scheduled = currentItem.isScheduled();
                /* The processAnswer will return the interval
                 * if it is 0, it means failure.
                 */
                boolean success = currentItem.processAnswer(grade, false) > 0 ? true : false;
                if (success == true) {
                    learnQueue.remove(0);
                    if(learnQueue.size() != 0){
                        dbHelper.updateItem(currentItem);
                    }
                    if(scheduled){
                        this.scheduledItemCount -= 1;
                    }
                    else{
                        this.newItemCount -= 1;
                    }
                } else {
                    learnQueue.remove(0);
                    learnQueue.add(currentItem);
                    dbHelper.updateItem(currentItem);
                    if(!scheduled){
                        this.scheduledItemCount += 1;
                        this.newItemCount -= 1;
                    }
                    
                }

                this.showAnswer = false;
                /* Now the currentItem is the next item, so we need to udpate the screen. */
                updateMemoScreen();
                autoSpeak();
                break;
            }
        }

    }

    @Override
    public boolean onLongClick(View v){
        if(v == (LinearLayout)findViewById(R.id.memo_screen_root)){
            showEditDialog();
            return true;
        }
        String[] helpText = {getString(R.string.memo_btn0_help_text),getString(R.string.memo_btn1_help_text),getString(R.string.memo_btn2_help_text),getString(R.string.memo_btn3_help_text),getString(R.string.memo_btn4_help_text),getString(R.string.memo_btn5_help_text)};
        for(int i = 0; i < 6; i++){
            if(v == btns[i]){
                Toast.makeText(this, helpText[i], Toast.LENGTH_SHORT).show();
            }
        }


        return false;
    }
        

    @Override
	protected void buttonBinding() {
		/* This function will bind the button event and show/hide button
         * according to the showAnswer varible.
         * */
		TextView answer = (TextView) findViewById(R.id.answer);
		if (showAnswer == false) {
            for(Button btn : btns){
                btn.setVisibility(View.INVISIBLE);
            }
			answer.setText(new StringBuilder().append(this.getString(R.string.memo_show_answer)));
			answer.setGravity(Gravity.CENTER);
			LinearLayout layoutAnswer = (LinearLayout)findViewById(R.id.layout_answer);
			layoutAnswer.setGravity(Gravity.CENTER);

		} else {

            for(Button btn : btns){
			    btn.setVisibility(View.VISIBLE);
            }
            if(btnOneRow){
                /* Do we still keep the 0 button? */
                //btns[0].setVisibility(View.GONE);
                String[] btnsText = {getString(R.string.memo_btn0_brief_text),getString(R.string.memo_btn1_brief_text),getString(R.string.memo_btn2_brief_text),getString(R.string.memo_btn3_brief_text),getString(R.string.memo_btn4_brief_text),getString(R.string.memo_btn5_brief_text)};
                for(int i = 0; i < btns.length; i++){
                    btns[i].setText(btnsText[i]);
                }
            }
            else{
            // This is only for two line mode
            // Show all buttons when user has clicked the screen.
                String[] btnsText = {getString(R.string.memo_btn0_text),getString(R.string.memo_btn1_text),getString(R.string.memo_btn2_text),getString(R.string.memo_btn3_text),getString(R.string.memo_btn4_text),getString(R.string.memo_btn5_text)};
                for(int i = 0; i < btns.length; i++){
                // This part will display the days to review
                    if(!learnAhead){
                        btns[i].setText(btnsText[i] + "\n+" + currentItem.processAnswer(i, true));
                    }
                    else{
                        /* In cram review mode, we do not estimate the 
                         * days to learn 
                         */
                        btns[i].setText(btnsText[i]);
                    }
                }
            }
        }
	}
    

    @Override
    protected void createButtons(){
        /* First load the settings */
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        btnOneRow = settings.getBoolean("btnonerow", false);
        /* Dynamically create button depending on the button settings
         * One Line or Two Lines for now.
         * The buttons are dynamically created.
         */
        RelativeLayout layout = (RelativeLayout)findViewById(R.id.memo_screen_button_layout);
        int id = 0;
        /* Make up an id using this base */
        int base = 0x21212;
        Display display = ((WindowManager)getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        int width = display.getWidth(); 
        if(btnOneRow){
            for(int i = 0; i < 6; i++){
                RelativeLayout.LayoutParams p = new RelativeLayout.LayoutParams(
                        width / 6,
                        RelativeLayout.LayoutParams.WRAP_CONTENT
                ); 
                if(i != 0){
                    p.addRule(RelativeLayout.RIGHT_OF, id);
                }
                btns[i] = new Button(this);
                btns[i].setId(base + i);
                layout.addView(btns[i], p);
                id = btns[i].getId();
            }
        }
        else{
            for(int i = 0; i < 6; i++){
                RelativeLayout.LayoutParams p = new RelativeLayout.LayoutParams(
                        width / 3,
                        RelativeLayout.LayoutParams.WRAP_CONTENT
                ); 
                if(i != 0 && i != 3){
                    p.addRule(RelativeLayout.RIGHT_OF, id);
                }
                else if(i == 3){
                    p.addRule(RelativeLayout.BELOW, base);
                }
                if(i > 3){
                    p.addRule(RelativeLayout.ALIGN_TOP, base + 3);
                }
                btns[i] = new Button(this);
                btns[i].setId(base + i);
                layout.addView(btns[i], p);
                id = btns[i].getId();
            }
        }

    }

    @Override
    protected boolean fetchCurrentItem(){
        if(learnQueue.size() != 0){
			currentItem = learnQueue.get(0);
            /* Successfully fetch */
            return true;
        }
        else{
            return false;
        }
    }

    private void autoSpeak(){

        if(autoaudioSetting){
            if(this.showAnswer == false){
                if(questionTTS != null){
                    questionTTS.sayText(currentItem.getQuestion());
                }
                else if(questionUserAudio){
                    mSpeakWord.speakWord(currentItem.getQuestion());

                }
            }
            else{
                if(answerTTS != null){
                    answerTTS.sayText(currentItem.getAnswer());
                }
                else if(answerUserAudio){
                    mSpeakWord.speakWord(currentItem.getAnswer());

                }
            }
        }
    }

    @Override protected void refreshAfterEditItem(){
    }

    @Override
    protected void refreshAfterDeleteItem(){
        restartActivity();
    }

}
