package com.todoroo.astrid.gcal;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.google.common.base.Predicate;
import com.todoroo.andlib.utility.DateUtilities;
import com.todoroo.astrid.utility.Constants;

import org.tasks.AccountManager;
import org.tasks.R;
import org.tasks.calendars.AndroidCalendarEvent;
import org.tasks.calendars.AndroidCalendarEventAttendee;
import org.tasks.calendars.CalendarEventProvider;
import org.tasks.injection.InjectingBroadcastReceiver;
import org.tasks.preferences.Preferences;
import org.tasks.scheduling.CalendarNotificationIntentService;

import java.util.List;

import javax.inject.Inject;

import timber.log.Timber;

import static com.google.common.collect.Iterables.any;

public class CalendarAlarmReceiver extends InjectingBroadcastReceiver {

    public static final int REQUEST_CODE_CAL_REMINDER = 100;
    public static final String BROADCAST_CALENDAR_REMINDER = Constants.PACKAGE + ".CALENDAR_EVENT";

    @Inject Preferences preferences;
    @Inject CalendarEventProvider calendarEventProvider;
    @Inject AccountManager accountManager;

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        if (!preferences.getBoolean(R.string.p_calendar_reminders, true)) {
            return;
        }

        try {
            Uri data = intent.getData();
            if (data == null) {
                return;
            }

            String uriString = data.toString();
            int pathIndex = uriString.indexOf("://");
            if (pathIndex > 0) {
                pathIndex += 3;
            } else {
                return;
            }
            long eventId = Long.parseLong(uriString.substring(pathIndex));
            if (eventId > 0) {
                boolean fromPostpone = CalendarNotificationIntentService.URI_PREFIX_POSTPONE.equals(data.getScheme());
                showCalReminder(context, eventId, fromPostpone);
            }
        } catch (IllegalArgumentException e) {
            // Some cursor read failed, or badly formed uri
            Timber.e(e, e.getMessage());
        }
    }

    private void showCalReminder(Context context, final long eventId, final boolean fromPostpone) {
        final AndroidCalendarEvent event = calendarEventProvider.getEvent(eventId);
        if (event == null) {
            return;
        }

        boolean shouldShowReminder;
        if (fromPostpone) {
            long timeAfter = DateUtilities.now() - event.getEnd();
            shouldShowReminder = (timeAfter > DateUtilities.ONE_MINUTE * 2);
        } else {
            long timeUntil = event.getStart() - DateUtilities.now();
            shouldShowReminder = (timeUntil > 0 && timeUntil < DateUtilities.ONE_MINUTE * 20);
        }

        if (shouldShowReminder && isMeeting(event)) {
            context.startActivity(new Intent(context, CalendarReminderActivity.class) {{
                putExtra(CalendarReminderActivity.TOKEN_EVENT_ID, eventId);
                putExtra(CalendarReminderActivity.TOKEN_EVENT_NAME, event.getTitle());
                putExtra(CalendarReminderActivity.TOKEN_EVENT_END_TIME, event.getEnd());
                putExtra(CalendarReminderActivity.TOKEN_FROM_POSTPONE, fromPostpone);
                setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            }});
        }
    }

    private boolean isMeeting(AndroidCalendarEvent event) {
        List<AndroidCalendarEventAttendee> attendees = event.getAttendees();
        if (attendees.size() < 2) {
            return false;
        }
        final List<String> myAccounts = accountManager.getAccounts();
        return any(attendees, new Predicate<AndroidCalendarEventAttendee>() {
            @Override
            public boolean apply(AndroidCalendarEventAttendee attendee) {
                return myAccounts.contains(attendee.getEmail());
            }
        });
    }
}
