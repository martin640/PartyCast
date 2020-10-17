package sk.martin64.partycast;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.media.audiofx.AudioEffect;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.mikhaellopez.circularprogressbar.CircularProgressBar;
import com.sothree.slidinguppanel.SlidingUpPanelLayout;

import java.util.concurrent.Executors;

import butterknife.BindView;
import butterknife.ButterKnife;
import partycast.model.Lobby;
import partycast.model.LobbyEventListener;
import partycast.model.LobbyMember;
import partycast.model.RemoteMedia;
import sk.martin64.partycast.androidserver.AndroidServerLobby;
import sk.martin64.partycast.ui.UiHelper;
import sk.martin64.partycast.utils.Callback;
import sk.martin64.partycast.utils.LobbyCoordinatorService;

public class MainActivity extends AppCompatActivity implements LobbyEventListener {

    @BindView(R.id.toolbar)
    Toolbar toolbar;
    @BindView(R.id.nav_view)
    BottomNavigationView navView;

    LobbyCoordinatorService coordinatorService;
    @BindView(R.id.swipe_bar)
    ViewGroup swipeBar;
    @BindView(R.id.sliding_layout)
    SlidingUpPanelLayout slidingLayout;
    @BindView(R.id.tv_title)
    TextView tvTitle;
    @BindView(R.id.tv_artist)
    TextView tvArtist;
    @BindView(R.id.ib_control)
    ImageButton ibControl;
    @BindView(R.id.tv_requested_by)
    TextView tvRequestedBy;
    @BindView(R.id.circularProgressBar)
    CircularProgressBar circularProgressBar;
    @BindView(R.id.ib_skip)
    ImageButton ibSkip;
    @BindView(R.id.iv_artwork)
    ImageView ivArtwork;

    @BindView(R.id.ib_control_large)
    ImageButton ibControlLarge;
    @BindView(R.id.ib_equalizer_large)
    ImageButton ibEqualizerLarge;
    @BindView(R.id.ib_skip_large)
    ImageButton ibSkipLarge;
    @BindView(R.id.control_progress)
    ProgressBar controlProgress;
    @BindView(R.id.control_progress_left)
    TextView controlProgressLeft;
    @BindView(R.id.control_progress_right)
    TextView controlProgressRight;

    private int navViewMeasuredHeight = 0;
    private Lobby lobby;

    private int dp45, dp16, dpControlsOffset;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        setSupportActionBar(toolbar);

        dp45 = (int) UiHelper.convertDpToPixel(45, this);
        dp16 = (int) UiHelper.convertDpToPixel(16, this);
        dpControlsOffset = (int) -UiHelper.convertDpToPixel(78, this);

        slidingLayout.post(() -> {
            navViewMeasuredHeight = navView.getMeasuredHeight();
            slidingLayout.setPanelHeight(swipeBar.getMeasuredHeight() + navViewMeasuredHeight);
        });
        slidingLayout.addPanelSlideListener(new SlidingUpPanelLayout.PanelSlideListener() {
            @Override
            public void onPanelSlide(View panel, float slideOffset) {
                slideOffset = Math.max(slideOffset, 0);
                navView.setTranslationY((slideOffset / 0.5f) * navViewMeasuredHeight);

                ViewGroup.LayoutParams ivArtworkParams = ivArtwork.getLayoutParams();
                ivArtworkParams.width = ivArtworkParams.height = (int) (dp45 + (slideOffset * dp45));
                ivArtwork.requestLayout();

                swipeBar.setPadding(0, (int) (slideOffset * dp16), 0, 0);

                ViewGroup.MarginLayoutParams ibSkipParams = (ViewGroup.MarginLayoutParams) ibSkip.getLayoutParams();
                ibSkipParams.rightMargin = (int) (slideOffset * dpControlsOffset);
                ibSkip.requestLayout();

                tvTitle.setMaxLines(slideOffset == 1 ? 2 : 1);
            }

            @Override
            public void onPanelStateChanged(View panel, SlidingUpPanelLayout.PanelState previousState, SlidingUpPanelLayout.PanelState newState) {
            }
        });

        coordinatorService = LobbyCoordinatorService.getInstance();

        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home, R.id.navigation_queue, R.id.navigation_library, R.id.navigation_settings)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(navView, navController);

        lobby = coordinatorService.getActiveLobby();
        lobby.addEventListener(this);
        slidingLayout.post(this::updateMiniPlayer);

        circularProgressBar.setProgressMax(1);
        controlProgress.setMax(1);
        Executors.newSingleThreadExecutor().submit(() -> {
            while (!isDestroyed()) {
                Lobby lobbyCopy = lobby;
                if (lobbyCopy.getPlayerState() == Lobby.PLAYBACK_READY) {
                    runOnUiThread(() -> {
                        circularProgressBar.setProgress(0);
                        controlProgress.setProgress(0);
                    });
                } else {
                    RemoteMedia nowPlaying = lobby.getLooper().getCurrentQueue().getCurrentlyPlaying();
                    if (nowPlaying != null) {
                        long progressReal = nowPlaying.getProgressReal();
                        runOnUiThread(() -> {
                            circularProgressBar.setProgress(nowPlaying.getProgress());
                            controlProgress.setMax((int) (nowPlaying.getDuration() / 1000));
                            controlProgress.setProgress((int) (progressReal / 1000));
                            controlProgressLeft.setText(UiHelper.timeFormat(progressReal));
                            controlProgressRight.setText(UiHelper.timeFormat(nowPlaying.getDuration()));
                        });
                    }
                }

                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });

        coordinatorService.registerDisconnectHandler(lobbyCoordinatorService -> {
            if (active) {
                startActivity(new Intent(this, ConnectActivity.class));
                finish();
            }
        });

        ibEqualizerLarge.setOnClickListener((v) -> {
            if (lobby instanceof AndroidServerLobby) {
                AndroidServerLobby androidServerLobby = (AndroidServerLobby) lobby;
                int sessionId = androidServerLobby.getMediaSessionId();

                if (sessionId == AudioEffect.ERROR_BAD_VALUE) {
                    Toast.makeText(this, "Session ID is not available. Make sure playback has started.", Toast.LENGTH_SHORT).show();
                } else {
                    System.err.format("SESSION ID: %s\n", sessionId);
                    try {
                        final Intent effects = new Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL);
                        effects.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId);
                        effects.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC);
                        startActivityForResult(effects, 0);
                    } catch (ActivityNotFoundException notFound) {
                        Toast.makeText(this, "Your system doesn't have built-in equalizer", Toast.LENGTH_SHORT).show();
                    }
                }
            } else {
                Toast.makeText(this, "Equalizer is not available for current session", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateMiniPlayer() {
        if (lobby.getPlayerState() == Lobby.PLAYBACK_READY) {
            tvTitle.setVisibility(View.GONE);
            tvArtist.setVisibility(View.GONE);
            tvRequestedBy.setVisibility(View.GONE);

            if (slidingLayout.getPanelState() != SlidingUpPanelLayout.PanelState.HIDDEN) {
                slidingLayout.setPanelState(SlidingUpPanelLayout.PanelState.HIDDEN);
            }
        } else {
            RemoteMedia nowPlaying = lobby.getLooper().getNowPlaying();
            if (nowPlaying == null) {
                Log.w("MainActivity", "Playback state is paused/playing but nowPlaying object is null");
                return;
            }

            Glide.with(this)
                    .load(nowPlaying.getArtwork())
                    .error(R.drawable.ic_no_artwork)
                    .override(dp45 * 2)
                    .into(ivArtwork);

            tvTitle.setText(nowPlaying.getTitle());
            tvArtist.setText(nowPlaying.getArtist());
            tvRequestedBy.setText(String.format("Requested by %s", RemoteMedia.optRequester(nowPlaying)));

            tvTitle.setVisibility(View.VISIBLE);
            tvArtist.setVisibility(View.VISIBLE);
            tvRequestedBy.setVisibility(View.VISIBLE);

            if (slidingLayout.getPanelState() == SlidingUpPanelLayout.PanelState.HIDDEN) {
                slidingLayout.setPanelState(SlidingUpPanelLayout.PanelState.COLLAPSED);
            }
        }

        boolean hasPermissions = Lobby.checkPermission(lobby.getClient(), LobbyMember.PERMISSION_MANAGE_QUEUE);
        ibControl.setEnabled(hasPermissions);
        ibSkip.setEnabled(hasPermissions);
        ibControlLarge.setEnabled(hasPermissions);
        ibSkipLarge.setEnabled(hasPermissions);

        ibSkip.setOnClickListener(v -> lobby.getLooper().skip(null));
        ibSkipLarge.setOnClickListener(v -> lobby.getLooper().skip(null));

        if (lobby.getPlayerState() == Lobby.PLAYBACK_PLAYING) {
            ibControl.setOnClickListener((v) -> lobby.getLooper().pause(null));
            ibControl.setImageResource(R.drawable.ic_round_pause_24);

            ibControlLarge.setOnClickListener((v) -> lobby.getLooper().pause(null));
            ibControlLarge.setImageResource(R.drawable.ic_round_pause_24);
        } else {
            ibControl.setOnClickListener((v) -> lobby.getLooper().play(null));
            ibControl.setImageResource(R.drawable.ic_round_play_arrow_24);

            ibControlLarge.setOnClickListener((v) -> lobby.getLooper().play(null));
            ibControlLarge.setImageResource(R.drawable.ic_round_play_arrow_24);
        }
    }

    @Override
    public void onLobbyStateChanged(Lobby lobby) {
        runOnUiThread(this::updateMiniPlayer);
    }

    @Override
    public void onUserUpdated(Lobby lobby, LobbyMember member) {
        runOnUiThread(this::updateMiniPlayer);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    private boolean active = false;

    @Override
    public void onStart() {
        super.onStart();
        active = true;
    }

    @Override
    public void onStop() {
        super.onStop();
        active = false;
    }

    @Override
    protected void onDestroy() {
        lobby.removeEventListener(this);
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.item_exit:
                Toast.makeText(this, "Stopping connection...", Toast.LENGTH_SHORT).show();
                coordinatorService.awaitClose(this, new Callback<Void>() {
                    @Override
                    public void onError(Exception e) {
                        Toast.makeText(MainActivity.this, "Failed to close connection: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onSuccess(Void aVoid) {
                    }
                });
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}