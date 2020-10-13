package sk.martin64.partycast;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputLayout;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
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
    @BindView(R.id.toolbar)
    Toolbar toolbar;
    @BindView(R.id.tabs)
    TabLayout tabs;
    @BindView(R.id.viewpager)
    ViewPager viewpager;

    @BindView(R.id.input_server)
    TextInputLayout inputServer;
    @BindView(R.id.input_name)
    TextInputLayout inputName;
    @BindView(R.id.progressBar)
    ProgressBar progressBar;
    @BindView(R.id.button)
    Button button;
    @BindView(R.id.lan_devices)
    RecyclerView lanDevices;
    @BindView(R.id.progressBar2)
    ProgressBar scanProgress;

    private LobbyCoordinatorService coordinatorService;
    private SharedPreferences savedInstance;
    private NetworkScanner.NetworkScanController scanController;
    private LanDevicesAdapter adapter;
    private List<LocalPartyReference> lanData = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect);
        ButterKnife.bind(this);

        setSupportActionBar(toolbar);
        tabs.setupWithViewPager(viewpager);
        viewpager.setAdapter(new MyPagerAdapter());
        viewpager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) { }

            @Override
            public void onPageSelected(int position) {
                itemHost.setVisible(position == 0);
                itemScan.setVisible(position == 1);
            }

            @Override
            public void onPageScrollStateChanged(int state) { }
        });

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
            itemHost.setEnabled(false);
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
        inputName.getEditText().setText(ServerLobbyService.pickName(savedInstance.getString("last_name", null)));

        lanDevices.setItemAnimator(new DefaultItemAnimator());
        lanDevices.setLayoutManager(new LinearLayoutManager(this, RecyclerView.VERTICAL, false));
        adapter = new LanDevicesAdapter(lanData);
        lanDevices.setAdapter(adapter);

        button.setOnClickListener(v -> {
            inputServer.setEnabled(false);
            inputName.setEnabled(false);
            button.setEnabled(false);
            itemHost.setEnabled(false);
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
                            itemHost.setEnabled(true);
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

    public String intToIp(int i) {
        return ((i & 0xFF) + "." +
                ((i >>>= 8) & 0xFF) + "." +
                ((i >>>= 8) & 0xFF) + "." +
                ((i >>>= 8) & 0xFF));
    }

    @Override
    protected void onDestroy() {
        if (scanController != null)
            scanController.cancel();

        super.onDestroy();
    }

    private Menu optionsMenu;
    private MenuItem itemHost, itemScan;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        this.optionsMenu = menu;
        getMenuInflater().inflate(R.menu.connect, menu);
        itemHost = optionsMenu.findItem(R.id.nav_host);
        itemScan = optionsMenu.findItem(R.id.nav_scan);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.nav_host: {
                inputServer.setEnabled(false);
                inputName.setEnabled(false);
                button.setEnabled(false);
                item.setEnabled(false);
                progressBar.setVisibility(View.VISIBLE);

                savedInstance.edit()
                        .putString("last_name", inputName.getEditText().getText().toString())
                        .apply();

                coordinatorService.createServer(this,
                        new Callback<Lobby>() {
                            @Override
                            public void onError(Exception e) {
                                inputServer.setEnabled(true);
                                inputName.setEnabled(true);
                                button.setEnabled(true);
                                item.setEnabled(true);
                                progressBar.setVisibility(View.GONE);

                                Toast.makeText(ConnectActivity.this, "Failed to create server: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onSuccess(Lobby lobby) {
                                startActivity(new Intent(ConnectActivity.this, MainActivity.class));
                                finish();
                            }
                        });
                return true;
            }
            case R.id.nav_scan: {
                item.setEnabled(false);
                lanData.clear();
                scanProgress.setVisibility(View.VISIBLE);
                scanProgress.setProgress(0);
                adapter.notifyDataSetChanged();

                String subnet = "192.168.0.1/24";

                WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wifiManager != null) {
                    try {
                        DhcpInfo d = wifiManager.getDhcpInfo();
                        InetAddress inetAddress = InetAddress.getByName(intToIp(d.ipAddress));
                        NetworkInterface networkInterface = NetworkInterface.getByInetAddress(inetAddress);
                        if (networkInterface != null) {
                            for (InterfaceAddress address : networkInterface.getInterfaceAddresses()) {
                                short netPrefix = address.getNetworkPrefixLength();
                                InetAddress add = address.getAddress();
                                if (add instanceof Inet4Address) {
                                    subnet = intToIp(d.gateway) + "/" + netPrefix;
                                    break;
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                Snackbar.make(toolbar, "Running scan on network " + subnet, Snackbar.LENGTH_LONG)
                        .show();

                NetworkScanner scanner = new NetworkScanner(ServerLobbyService.SERVER_PORT, subnet);
                scanController = scanner.run(500, 600, false, new NetworkScanner.ScannerHandler() {
                    @Override
                    public void onDiscoverActive(String address, float ping) {
                        System.out.format("Discovered running service on port 22 on device %s\n", address);

                        coordinatorService.head(ConnectActivity.this, address, new Callback<Lobby>() {
                            @Override
                            public void onError(Exception e) {

                            }

                            @Override
                            public void onSuccess(Lobby lobby) {
                                runOnUiThread(() -> {
                                    LocalPartyReference ref = new LocalPartyReference(address, lobby.getTitle(), String.format("%.00f ms", ping));
                                    lanData.add(ref);
                                    adapter.notifyItemInserted(lanData.indexOf(ref));
                                });
                            }
                        });
                    }

                    @Override
                    public void onStatusChange(long processed, long max) {
                        int progress = (int) (((float) processed / max) * 100f);
                        runOnUiThread(() -> scanProgress.setProgress(progress));
                    }

                    @Override
                    public void onScanComplete(List<String> addresses, long length, float time) {
                        runOnUiThread(() -> {
                            scanProgress.setVisibility(View.INVISIBLE);
                            Snackbar.make(toolbar,
                                    String.format(Locale.getDefault(),
                                            "Scan completed in %.01f s\nDiscovered %s device(s)",
                                            time / 1000f, addresses.size()),
                                    5000)
                                    .setAction("OK", v1 -> {
                                    })
                                    .show();
                            item.setEnabled(true);
                        });
                    }
                });
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
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
        private String ip, name, ping;

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

    public class LanDevicesAdapter extends RecyclerView.Adapter<LanDevicesAdapter.LanDevicesHolder> {
        public class LanDevicesHolder extends RecyclerView.ViewHolder {
            @BindView(R.id.textView)
            TextView title;
            @BindView(R.id.textView2)
            TextView address;
            @BindView(R.id.imageView2)
            ImageView icon;
            @BindView(R.id.textView3)
            TextView ping;

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
            holder.ping.setText(item.getPing());

            holder.itemView.setOnClickListener(v -> {
                inputServer.setEnabled(false);
                inputName.setEnabled(false);
                button.setEnabled(false);
                itemHost.setEnabled(false);
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
                                itemHost.setEnabled(true);
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

    private class MyPagerAdapter extends PagerAdapter {

        @Override
        @NonNull
        public Object instantiateItem(@NonNull ViewGroup collection, int position) {
            int resId = 0;
            switch (position) {
                case 0:
                    resId = R.id.page1;
                    break;
                case 1:
                    resId = R.id.page2;
                    break;
            }
            return findViewById(resId);
        }

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public boolean isViewFromObject(@NonNull View arg0, @NonNull Object arg1) {
            return arg0 == arg1;
        }

        @Nullable
        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0: return "Connect";
                case 1: return "Discover devices";
            }
            return super.getPageTitle(position);
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            // No super
        }
    }
}