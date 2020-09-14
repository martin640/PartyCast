package sk.martin64.partycast;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.textfield.TextInputLayout;

import butterknife.BindView;
import butterknife.ButterKnife;
import sk.martin64.partycast.core.Lobby;
import sk.martin64.partycast.utils.Callback;
import sk.martin64.partycast.utils.LobbyCoordinatorService;

public class ConnectActivity extends AppCompatActivity {

    @BindView(R.id.input_server)
    TextInputLayout inputServer;
    @BindView(R.id.input_name)
    TextInputLayout inputName;
    @BindView(R.id.progressBar)
    ProgressBar progressBar;
    @BindView(R.id.button)
    Button button;
    @BindView(R.id.button2)
    Button button2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect);
        ButterKnife.bind(this);


        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { Manifest.permission.READ_EXTERNAL_STORAGE },
                    150);
        }


        LobbyCoordinatorService coordinatorService = LobbyCoordinatorService.getInstance();

        if (coordinatorService.getState() == LobbyCoordinatorService.STATE_OPEN) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        } else if (coordinatorService.getState() == LobbyCoordinatorService.STATE_CONNECTING_CLIENT ||
                coordinatorService.getState() == LobbyCoordinatorService.STATE_CREATING_SERVER) {
            inputServer.setEnabled(false);
            inputName.setEnabled(false);
            button.setEnabled(false);
            button2.setEnabled(false);
            progressBar.setVisibility(View.VISIBLE);

            coordinatorService.join(new Callback<Lobby>() {
                @Override
                public void onError(Exception e) {
                }

                @Override
                public void onSuccess(Lobby lobby) {
                    startActivity(new Intent(ConnectActivity.this, MainActivity.class));
                    finish();
                }
            });
        }

        SharedPreferences savedInstance = getSharedPreferences("si", MODE_PRIVATE);
        inputServer.getEditText().setText(savedInstance.getString("last_server", ""));
        inputName.getEditText().setText(savedInstance.getString("last_name", ServerLobbyService.pickName()));

        button.setOnClickListener(v -> {
            inputServer.setEnabled(false);
            inputName.setEnabled(false);
            button.setEnabled(false);
            button2.setEnabled(false);
            progressBar.setVisibility(View.VISIBLE);

            savedInstance.edit()
                    .putString("last_server", inputServer.getEditText().getText().toString())
                    .putString("last_name", inputName.getEditText().getText().toString())
                    .apply();

            coordinatorService.connectClient(this,
                    inputServer.getEditText().getText().toString(),
                    inputName.getEditText().getText().toString(),
                    new Callback<Lobby>() {
                        @Override
                        public void onError(Exception e) {
                            inputServer.setEnabled(true);
                            inputName.setEnabled(true);
                            button.setEnabled(true);
                            button2.setEnabled(true);
                            progressBar.setVisibility(View.GONE);

                            Toast.makeText(ConnectActivity.this, "Failed to create server: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onSuccess(Lobby lobby) {
                            startActivity(new Intent(ConnectActivity.this, MainActivity.class));
                            finish();
                        }
                    });
        });

        button2.setOnClickListener(v -> {
            inputServer.setEnabled(false);
            inputName.setEnabled(false);
            button.setEnabled(false);
            button2.setEnabled(false);
            progressBar.setVisibility(View.VISIBLE);

            coordinatorService.createServer(this,
                    new Callback<Lobby>() {
                        @Override
                        public void onError(Exception e) {
                            inputServer.setEnabled(true);
                            inputName.setEnabled(true);
                            button.setEnabled(true);
                            button2.setEnabled(true);
                            progressBar.setVisibility(View.GONE);

                            Toast.makeText(ConnectActivity.this, "Failed to create server: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onSuccess(Lobby lobby) {
                            startActivity(new Intent(ConnectActivity.this, MainActivity.class));
                            finish();
                        }
                    });
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case 150:
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 &&
                        grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission is granted. Continue the action or workflow
                    // in your app.
                }  else {
                    Toast.makeText(this, "Access to storage rejected", Toast.LENGTH_SHORT).show();
                }
                return;
        }
    }
}