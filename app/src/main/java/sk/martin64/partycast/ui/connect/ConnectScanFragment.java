package sk.martin64.partycast.ui.connect;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import partycast.model.Lobby;
import sk.martin64.partycast.BuildConfig;
import sk.martin64.partycast.ConnectActivity;
import sk.martin64.partycast.MainActivity;
import sk.martin64.partycast.R;
import sk.martin64.partycast.ServerLobbyService;
import sk.martin64.partycast.ui.UiHelper;
import sk.martin64.partycast.utils.Callback;
import sk.martin64.partycast.utils.NetworkScanner;

@SuppressLint("NonConstantResourceId")
public class ConnectScanFragment extends Fragment {

    @BindView(R.id.lan_devices)
    RecyclerView lanDevices;
    @BindView(R.id.floatingActionButton)
    FloatingActionButton fab;

    Unbinder unbinder;
    private ConnectActivity activity;
    private NetworkScanner.NetworkScanController scanController;
    private LanDevicesAdapter adapter;
    private final List<ConnectActivity.LocalPartyReference> lanData = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_connect_scan, container, false);
        unbinder = ButterKnife.bind(this, v);
        activity = (ConnectActivity) getActivity();

        lanDevices.setItemAnimator(new DefaultItemAnimator());
        lanDevices.setLayoutManager(new LinearLayoutManager(getContext(), RecyclerView.VERTICAL, false));
        adapter = new LanDevicesAdapter(lanData);
        lanDevices.setAdapter(adapter);

        fab.setOnClickListener((v1) -> {
            lanData.clear();
            adapter.notifyDataSetChanged();

            String subnet = "192.168.0.1/24";

            WifiManager wifiManager = (WifiManager) activity.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
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

            Snackbar.make(fab, "Running scan on network " + subnet, Snackbar.LENGTH_LONG).show();

            NetworkScanner scanner = new NetworkScanner(ServerLobbyService.SERVER_PORT, subnet);

            ProgressDialog dialog = new ProgressDialog(activity);
            dialog.setTitle("Scanning network...");
            dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            dialog.setMax(100);
            dialog.setCancelable(false);
            dialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", (dialog1, which) -> {
                scanController.cancel();
            });
            dialog.show();

            scanController = scanner.run(500, 300, false, new NetworkScanner.ScannerHandler() {
                @Override
                public void onDiscoverActive(String address, float ping) {
                    System.out.format("Discovered running service on port 22 on device %s\n", address);

                    activity.coordinatorService.head(address, new Callback<Lobby>() {
                        @Override
                        public void onError(Exception e) { }

                        @Override
                        public void onSuccess(Lobby lobby) {
                            UiHelper.runOnUiCompact(() -> {
                                ConnectActivity.LocalPartyReference ref = new ConnectActivity.LocalPartyReference(address, lobby.getTitle(), String.format("%.00f ms", ping));
                                lanData.add(ref);
                                adapter.notifyItemInserted(lanData.indexOf(ref));
                            });
                        }
                    });
                }

                private int lastProgress = 0;

                @Override
                public void onStatusChange(long processed, long max) {
                    int progress = (int) (((float) processed / max) * 100f);
                    if (progress > lastProgress) {
                        lastProgress = progress;
                        UiHelper.runOnUiCompact(() -> dialog.setProgress(progress));
                    }
                }

                @Override
                public void onScanComplete(List<String> addresses, long length, float time) {
                    UiHelper.runOnUiCompact(() -> {
                        dialog.dismiss();
                        Snackbar.make(fab,
                                String.format(Locale.getDefault(),
                                        "Scan completed in %.01f s\nDiscovered %s device(s)",
                                        time / 1000f, addresses.size()),
                                5000)
                                .setAction("OK", v1 -> { })
                                .show();
                    });
                }
            });
        });

        return v;
    }

    @Override
    public void onDestroy() {
        if (unbinder != null)
            unbinder.unbind();
        if (scanController != null)
            scanController.cancel();
        super.onDestroy();
    }

    public String intToIp(int i) {
        return ((i & 0xFF) + "." +
                ((i >>>= 8) & 0xFF) + "." +
                ((i >>>= 8) & 0xFF) + "." +
                ((i >>>= 8) & 0xFF));
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

        private final List<ConnectActivity.LocalPartyReference> data;

        public LanDevicesAdapter(List<ConnectActivity.LocalPartyReference> data) {
            this.data = data;
        }

        @NonNull
        @Override
        public LanDevicesHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new LanDevicesHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_lan_device, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull LanDevicesHolder holder, int position) {
            ConnectActivity.LocalPartyReference item = data.get(position);

            holder.title.setText(item.getName());
            holder.address.setText(item.getIp());
            holder.ping.setText(item.getPing());

            holder.itemView.setOnClickListener(v -> {
                ProgressDialog dialog = new ProgressDialog(v.getContext());
                dialog.setTitle("Pending task in background");
                dialog.setMessage("Connecting to the server...");
                dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                dialog.setCancelable(false);
                dialog.show();

                activity.savedInstance.edit().putString("last_server", item.getIp())
                        .apply();

                activity.coordinatorService.connectClient(activity,
                        item.getIp(),
                        ServerLobbyService.pickName(activity.savedInstance.getString("last_name", null)),
                        new Callback<Lobby>() {
                            @Override
                            public void onError(Exception e) {
                                if (!activity.isDestroyed()) {
                                    dialog.dismiss();

                                    AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                                    builder.setTitle("Failed to connect to the server");
                                    if (BuildConfig.DEBUG) {
                                        StringWriter errors = new StringWriter();
                                        e.printStackTrace(new PrintWriter(errors));
                                        builder.setMessage(errors.toString());
                                    } else builder.setMessage(e.getMessage());
                                    builder.setPositiveButton("OK", (dialog, which) -> dialog.dismiss());
                                    builder.show();
                                }
                            }

                            @Override
                            public void onSuccess(Lobby lobby) {
                                startActivity(new Intent(activity, MainActivity.class));
                                activity.finish();
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