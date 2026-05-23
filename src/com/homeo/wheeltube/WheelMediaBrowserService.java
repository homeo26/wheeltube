package com.homeo.wheeltube;

import android.app.PendingIntent;
import android.content.Intent;
import android.media.MediaDescription;
import android.media.browse.MediaBrowser;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.service.media.MediaBrowserService;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Minimal MediaBrowserService that satisfies Android Auto's "is this a real
 * media app?" check. AA inspects this service's existence + manifest entry
 * to decide whether to show our app icon in its launcher.
 *
 * The service exposes one browse item: "Open WheelTube". When the user taps
 * it, AA invokes the MediaSession callback which simply launches
 * {@link MainActivity}. AA then projects that activity to the head unit.
 */
public class WheelMediaBrowserService extends MediaBrowserService {

    private static final String TAG = "WheelTube";
    private static final String ROOT_ID = "wheeltube_root";
    private static final String OPEN_ID = "wheeltube_open";

    private MediaSession session;

    @Override
    public void onCreate() {
        super.onCreate();

        session = new MediaSession(this, "WheelTube");
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlayFromMediaId(String mediaId, Bundle extras) {
                Log.i(TAG, "onPlayFromMediaId: " + mediaId);
                openMainActivity();
            }

            @Override
            public void onPlay() {
                Log.i(TAG, "onPlay (default action)");
                openMainActivity();
            }
        });

        // A minimal "ready" playback state is required so AA renders the item
        // as tappable rather than greyed out.
        PlaybackState state = new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY
                        | PlaybackState.ACTION_PLAY_FROM_MEDIA_ID)
                .setState(PlaybackState.STATE_NONE, 0, 1.0f)
                .build();
        session.setPlaybackState(state);
        session.setActive(true);

        setSessionToken(session.getSessionToken());
    }

    @Override
    public void onDestroy() {
        if (session != null) {
            session.release();
            session = null;
        }
        super.onDestroy();
    }

    @Override
    public BrowserRoot onGetRoot(String clientPackageName, int clientUid, Bundle rootHints) {
        // Allow any caller; AA will pass its own package and UID.
        return new BrowserRoot(ROOT_ID, null);
    }

    @Override
    public void onLoadChildren(String parentMediaId,
                               Result<List<MediaBrowser.MediaItem>> result) {
        if (!ROOT_ID.equals(parentMediaId)) {
            result.sendResult(Collections.<MediaBrowser.MediaItem>emptyList());
            return;
        }

        List<MediaBrowser.MediaItem> items = new ArrayList<>();
        MediaDescription desc = new MediaDescription.Builder()
                .setMediaId(OPEN_ID)
                .setTitle(getString(R.string.open_in_wheeltube))
                .setSubtitle(getString(R.string.open_in_wheeltube_subtitle))
                .build();
        items.add(new MediaBrowser.MediaItem(desc, MediaBrowser.MediaItem.FLAG_PLAYABLE));
        result.sendResult(items);
    }

    private void openMainActivity() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            startActivity(i);
        } catch (Throwable t) {
            // On Android 10+ background-launching activities is restricted;
            // fall back to a notification PendingIntent if this ever fires.
            Log.w(TAG, "startActivity failed, falling back to PendingIntent", t);
            try {
                PendingIntent pi = PendingIntent.getActivity(this, 0, i,
                        PendingIntent.FLAG_UPDATE_CURRENT);
                pi.send();
            } catch (PendingIntent.CanceledException e) {
                Log.e(TAG, "PendingIntent.send failed", e);
            }
        }
    }
}
