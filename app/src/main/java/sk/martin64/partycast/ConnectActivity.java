package sk.martin64.partycast;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import butterknife.BindView;
import butterknife.ButterKnife;
import partycast.model.Lobby;
import sk.martin64.partycast.ui.UiHelper;
import sk.martin64.partycast.utils.Callback;
import sk.martin64.partycast.utils.LobbyCoordinatorService;

@SuppressLint("NonConstantResourceId")
public class ConnectActivity extends AppCompatActivity {

    @BindView(R.id.toolbar)
    public Toolbar toolbar;

    public LobbyCoordinatorService coordinatorService;
    public SharedPreferences savedInstance;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect);
        ButterKnife.bind(this);

        setSupportActionBar(toolbar);

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    150);
        }

        coordinatorService = LobbyCoordinatorService.getInstance();

        if (coordinatorService.getState() == LobbyCoordinatorService.STATE_OPEN) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        } else if (coordinatorService.getState() == LobbyCoordinatorService.STATE_CONNECTING_CLIENT ||
                coordinatorService.getState() == LobbyCoordinatorService.STATE_CREATING_SERVER) {

            ProgressDialog dialog = new ProgressDialog(this);
            dialog.setTitle("Pending task in background");
            dialog.setMessage("Waiting for connection to be established...");
            dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            dialog.setCancelable(false);
            dialog.show();

            coordinatorService.join(new Callback<Lobby>() {
                @Override
                public void onError(Exception e) {
                    UiHelper.runOnUiCompact(dialog::dismiss);
                }

                @Override
                public void onSuccess(Lobby lobby) {
                    UiHelper.runOnUiCompact(() -> {
                        dialog.dismiss();
                        startActivity(new Intent(ConnectActivity.this, MainActivity.class));
                        finish();
                    });
                }
            });
        }

        savedInstance = getSharedPreferences("si", MODE_PRIVATE);

        AppBarConfiguration appBarConfiguration =
                new AppBarConfiguration.Builder(R.id.navigation_connect_main).build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(toolbar, navController, appBarConfiguration);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 150) {// If request is cancelled, the result arrays are empty.
            if (grantResults.length <= 0 ||
                    grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(this, "Access to storage rejected", Toast.LENGTH_SHORT).show();
                    }
        }
    }

    public static class LocalPartyReference {
        private final String ip, name, ping;

        public LocalPartyReference(String ip, String name, String ping) {
            this.ip = ip;
            this.name = name;
            this.ping = ping;
        }

        public String getIp() {
            return ip;
        }

        public String getName() {
            return name;
        }

        public String getPing() {
            return ping;
        }
    }
}