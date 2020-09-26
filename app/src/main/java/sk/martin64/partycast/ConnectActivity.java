package sk.martin64.partycast;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;
import sk.martin64.partycast.core.Lobby;
import sk.martin64.partycast.utils.Callback;
import sk.martin64.partycast.utils.LobbyCoordinatorService;
import sk.martin64.partycast.utils.NetworkScanner;

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
    @BindView(R.id.button4)
    Button buttonScan;
    @BindView(R.id.lan_devices)
    RecyclerView lanDevices;

    private LobbyCoordinatorService coordinatorService;
    private SharedPreferences savedInstance;
    private NetworkScanner.NetworkScanController scanController;
    private List<LocalPartyReference> lanData = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect);
        ButterKnife.bind(this);

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

        savedInstance = getSharedPreferences("si", MODE_PRIVATE);
        inputServer.getEditText().setText(savedInstance.getString("last_server", ""));
        inputName.getEditText().setText(savedInstance.getString("last_name", ServerLobbyService.pickName()));

        lanDevices.setItemAnimator(new DefaultItemAnimator());
        lanDevices.setLayoutManager(new LinearLayoutManager(this, RecyclerView.VERTICAL, false));
        LanDevicesAdapter adapter = new LanDevicesAdapter(lanData);
        lanDevices.setAdapter(adapter);

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

                            Toast.makeText(ConnectActivity.this, "Failed to connect to the server: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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

        buttonScan.setOnClickListener(v -> {
            v.setEnabled(false);
            lanData.clear();
            adapter.notifyDataSetChanged();

            NetworkScanner scanner = new NetworkScanner(ServerLobbyService.SERVER_PORT, "192.168.0.0/24");
            scanController = scanner.run(100, 1000, false, new NetworkScanner.ScannerHandler() {
                @Override
                public void onDiscoverActive(String address) {
                    System.out.format("Discovered running service on port 22 on device %s\n", address);

                    coordinatorService.head(ConnectActivity.this, address, new Callback<Lobby>() {
                        @Override
                        public void onError(Exception e) {

                        }

                        @Override
                        public void onSuccess(Lobby lobby) {
                            runOnUiThread(() -> {
                                LocalPartyReference ref = new LocalPartyReference(address, lobby.getTitle());
                                lanData.add(ref);
                                adapter.notifyItemInserted(lanData.indexOf(ref));
                            });
                        }
                    });
                }

                @Override
                public void onScanComplete(List<String> addresses, long length, float time) {
                    runOnUiThread(() -> {
                        if (BuildConfig.DEBUG) {
                            Toast.makeText(ConnectActivity.this,
                                    String.format(Locale.getDefault(), "Network (size %s) iterated in %.01f ms", length, time),
                                    Toast.LENGTH_SHORT).show();
                        }
                        v.setEnabled(true);
                    });
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        if (scanController != null)
            scanController.cancel();

        super.onDestroy();
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
                } else {
                    Toast.makeText(this, "Access to storage rejected", Toast.LENGTH_SHORT).show();
                }
                return;
        }
    }

    public static class LocalPartyReference {
        private String ip, name;

        public LocalPartyReference(String ip, String name) {
            this.ip = ip;
            this.name = name;
        }

        public String getIp() {
            return ip;
        }

        public String getName() {
            return name;
        }
    }

    public class LanDevicesAdapter extends RecyclerView.Adapter<LanDevicesAdapter.LanDevicesHolder> {
        public class LanDevicesHolder extends RecyclerView.ViewHolder {
            @BindView(R.id.textView)
            TextView title;
            @BindView(R.id.textView2)
            TextView address;
            @BindView(R.id.imageView2)
            ImageView icon;

            public LanDevicesHolder(@NonNull View itemView) {
                super(itemView);
                ButterKnife.bind(this, itemView);
            }
        }

        private List<LocalPartyReference> data;

        public LanDevicesAdapter(List<LocalPartyReference> data) {
            this.data = data;
        }

        @NonNull
        @Override
        public LanDevicesHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new LanDevicesHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_lan_device, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull LanDevicesHolder holder, int position) {
            LocalPartyReference item = data.get(position);

            holder.title.setText(item.getName());
            holder.address.setText(item.getIp());

            holder.itemView.setOnClickListener(v -> {
                inputServer.setEnabled(false);
                inputName.setEnabled(false);
                button.setEnabled(false);
                button2.setEnabled(false);
                progressBar.setVisibility(View.VISIBLE);

                savedInstance.edit()
                        .putString("last_server", item.getIp())
                        .putString("last_name", inputName.getEditText().getText().toString())
                        .apply();

                coordinatorService.connectClient(ConnectActivity.this,
                        item.getIp(),
                        inputName.getEditText().getText().toString(),
                        new Callback<Lobby>() {
                            @Override
                            public void onError(Exception e) {
                                inputServer.setEnabled(true);
                                inputName.setEnabled(true);
                                button.setEnabled(true);
                                button2.setEnabled(true);
                                progressBar.setVisibility(View.GONE);

                                Toast.makeText(ConnectActivity.this, "Failed to connect to the server: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
        public int getItemCount() {
            return data.size();
        }
    }
}