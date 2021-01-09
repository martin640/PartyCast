package sk.martin64.partycast;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.bumptech.glide.Glide;
import com.google.android.material.slider.Slider;
import com.google.android.material.tabs.TabLayout;
import com.mikhaellopez.circularprogressbar.CircularProgressBar;

import java.util.concurrent.Executors;

import butterknife.BindView;
import butterknife.ButterKnife;
import partycast.model.Lobby;
import partycast.model.LobbyEventListener;
import partycast.model.LobbyMember;
import partycast.model.RemoteMedia;
import partycast.model.VolumeControl;
import sk.martin64.partycast.ui.UiHelper;
import sk.martin64.partycast.ui.main.BoardFragment;
import sk.martin64.partycast.ui.main.HomeFragment;
import sk.martin64.partycast.ui.main.LibraryFragment;
import sk.martin64.partycast.ui.main.QueueFragment;
import sk.martin64.partycast.utils.Callback;
import sk.martin64.partycast.utils.LobbyCoordinatorService;

public class MainActivity extends AppCompatActivity implements LobbyEventListener {

    @BindView(R.id.toolbar)
    Toolbar toolbar;

    LobbyCoordinatorService coordinatorService;
    @BindView(R.id.viewPager)
    ViewPager viewPager;
    @BindView(R.id.tabLayout)
    TabLayout tabLayout;
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

    @BindView(R.id.bottom_bar)
    FrameLayout bottomBar;
    @BindView(R.id.control_progress_left)
    TextView controlProgressLeft;
    @BindView(R.id.control_progress_right)
    TextView controlProgressRight;
    @BindView(R.id.control_volume)
    Slider controlVolume;

    private int dp45;
    private Lobby lobby;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        setSupportActionBar(toolbar);

        dp45 = (int) UiHelper.convertDpToPixel(45, this);

        coordinatorService = LobbyCoordinatorService.getInstance();

        lobby = coordinatorService.getActiveLobby();
        lobby.addEventListener(this);

        viewPager.setAdapter(new DefaultViewPagerAdapter(getSupportFragmentManager()));
        tabLayout.setupWithViewPager(viewPager);
        bottomBar.post(this::updateMiniPlayer);

        circularProgressBar.setProgressMax(1);
        Executors.newSingleThreadExecutor().submit(() -> {
            while (!isDestroyed()) {
                Lobby lobbyCopy = lobby;
                if (lobbyCopy.getPlayerState() == Lobby.PLAYBACK_READY) {
                    runOnUiThread(() -> {
                        circularProgressBar.setProgress(0);
                    });
                } else {
                    RemoteMedia nowPlaying = lobby.getLooper().getCurrentQueue().getCurrentlyPlaying();
                    if (nowPlaying != null) {
                        long progressReal = nowPlaying.getProgressReal();
                        runOnUiThread(() -> {
                            circularProgressBar.setProgress(nowPlaying.getProgress());
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

        controlVolume.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                VolumeControl volumeControl = lobby.getVolumeControl();
                if (volumeControl != null) volumeControl.setLevel(value / 100f);
            }
        });
        controlVolume.setLabelFormatter(value -> Math.round(value) + "%");

        coordinatorService.registerDisconnectHandler(lobbyCoordinatorService -> {
            if (active) {
                startActivity(new Intent(this, ConnectActivity.class));
                finish();
            }
        });
    }

    private void updateMiniPlayer() {
        if (lobby.getPlayerState() == Lobby.PLAYBACK_READY) {
            tvTitle.setVisibility(View.INVISIBLE);
            tvArtist.setVisibility(View.INVISIBLE);
            tvRequestedBy.setVisibility(View.INVISIBLE);
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
                    .centerCrop()
                    .into(ivArtwork);

            tvTitle.setText(nowPlaying.getTitle());
            tvArtist.setText(nowPlaying.getArtist());
            tvRequestedBy.setText(String.format("Requested by %s", RemoteMedia.optRequester(nowPlaying)));

            tvTitle.setVisibility(View.VISIBLE);
            tvArtist.setVisibility(View.VISIBLE);
            tvRequestedBy.setVisibility(View.VISIBLE);
        }

        boolean hasPermissions = Lobby.checkPermission(lobby.getClient(), LobbyMember.PERMISSION_MANAGE_QUEUE);
        ibControl.setEnabled(hasPermissions);
        ibSkip.setEnabled(hasPermissions);
        controlVolume.setEnabled(hasPermissions);

        VolumeControl volumeControl = lobby.getVolumeControl();
        float level = volumeControl.getLevel() * 100f;
        controlVolume.setValue(5 * (Math.round(level / 5)));

        ibSkip.setOnClickListener(v -> lobby.getLooper().skip(null));

        if (lobby.getPlayerState() == Lobby.PLAYBACK_PLAYING) {
            ibControl.setOnClickListener((v) -> lobby.getLooper().pause(null));
            ibControl.setImageResource(R.drawable.ic_round_pause_24);
        } else {
            ibControl.setOnClickListener((v) -> lobby.getLooper().play(null));
            ibControl.setImageResource(R.drawable.ic_round_play_arrow_24);
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
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        VolumeControl volumeControl = lobby.getVolumeControl();
        if (volumeControl != null) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                volumeControl.setLevel(Math.max(volumeControl.getLevel() - 0.05f, 0f));
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                volumeControl.setLevel(Math.min(volumeControl.getLevel() + 0.05f, 1f));
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
                volumeControl.setLevel(0f);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
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

    private static class DefaultViewPagerAdapter extends FragmentPagerAdapter {
        public DefaultViewPagerAdapter(FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }
        public DefaultViewPagerAdapter(@NonNull FragmentManager fm, int behavior) {
            super(fm, behavior);
        }

        @Override
        @NonNull
        public Fragment getItem(int position) {
            switch (position) {
                case 1: return new HomeFragment();
                case 2: return new LibraryFragment();
                case 3: return new QueueFragment();
                case 0: default: return new BoardFragment();
            }
        }

        @Nullable
        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0: return "Board";
                case 1: return "Members";
                case 2: return "Library";
                case 3: return "Queue";
                default: return super.getPageTitle(position);
            }
        }

        @Override
        public int getCount() {
            return 4;
        }
    }
}