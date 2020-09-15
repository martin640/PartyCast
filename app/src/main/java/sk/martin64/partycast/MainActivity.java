package sk.martin64.partycast;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.mikhaellopez.circularprogressbar.CircularProgressBar;
import com.sothree.slidinguppanel.SlidingUpPanelLayout;

import java.util.concurrent.Executors;

import butterknife.BindView;
import butterknife.ButterKnife;
import sk.martin64.partycast.core.Lobby;
import sk.martin64.partycast.core.LobbyEventListener;
import sk.martin64.partycast.core.LobbyMember;
import sk.martin64.partycast.core.RemoteMedia;
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

    private int navViewMeasuredHeight = 0;
    private Lobby lobby;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        setSupportActionBar(toolbar);

        slidingLayout.post(() -> {
            navViewMeasuredHeight = navView.getMeasuredHeight();
            slidingLayout.setPanelHeight(swipeBar.getMeasuredHeight() + navViewMeasuredHeight);
        });
        slidingLayout.addPanelSlideListener(new SlidingUpPanelLayout.PanelSlideListener() {
            @Override
            public void onPanelSlide(View panel, float slideOffset) {
                slideOffset = Math.max(slideOffset, 0);
                navView.setTranslationY((slideOffset / 0.5f) * navViewMeasuredHeight);
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
        Executors.newSingleThreadExecutor().submit(() -> {
            while (!isDestroyed()) {
                Lobby lobbyCopy = lobby;
                if (lobbyCopy.getPlayerState() == Lobby.PLAYBACK_READY) {
                    runOnUiThread(() -> circularProgressBar.setProgress(0));
                } else {
                    RemoteMedia nowPlaying = lobby.getLooper().getCurrentQueue().getCurrentlyPlaying();
                    runOnUiThread(() -> circularProgressBar.setProgress(nowPlaying.getProgress()));
                }

                try { Thread.sleep(500); } catch (InterruptedException e) { break; }
            }
        });

        coordinatorService.registerDisconnectHandler(lobbyCoordinatorService -> {
            if (active) {
                startActivity(new Intent(this, ConnectActivity.class));
                finish();
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
            RemoteMedia nowPlaying = lobby.getLooper().getCurrentQueue().getCurrentlyPlaying();

            tvTitle.setText(nowPlaying.getTitle());
            tvArtist.setText(nowPlaying.getArtist());
            tvRequestedBy.setText(String.format("Requested by %s", nowPlaying.getRequester().getName()));

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