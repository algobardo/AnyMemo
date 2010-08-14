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

import android.content.Intent;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.app.PendingIntent;
import android.app.AlarmManager;
import android.content.Context;
import android.util.Log;

public class SetAlarmReceiver extends BroadcastReceiver{
    private final static String TAG = "org.liberty.android.fantastischmemo.SetAlarmReceiver";
    private final static int ALARM_REQUEST_CODE = 1548345;

    @Override
    public void onReceive(Context context, Intent intent){
        Log.v(TAG, "RECIVE RECEIVE RECEIVE FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF");
    }

    public static void setAlarm(Context context){
        /* Set an alarm for the notification */
        Log.v(TAG, "Set ALARM here!");
        AlarmManager am = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        Intent myIntent = new Intent(context, NotificationReceiver.class);
        PendingIntent sender = PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE, myIntent, 0);
        am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 15000, sender);


    }
}
