package sk.martin64.partycast.ui.connect;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Intent;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

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
import sk.martin64.partycast.utils.NetworkDiscovery;
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
            v1.setVisibility(View.GONE);
            lanData.clear();
            adapter.notifyDataSetChanged();

            NetworkDiscovery networkDiscovery = NetworkDiscovery.create(ServerLobbyService.SERVER_PORT, 5000);
            networkDiscovery.run(new byte[0], 50, new NetworkDiscovery.Listener() {
                @Override
                public void onEndpointDiscovered(NetworkDiscovery.Endpoint endpoint, long time) {
                    String address = endpoint.address.getHostAddress();
                    String response = new String(endpoint.dataBuffer, StandardCharsets.UTF_8);

                    UiHelper.runOnUiCompact(() -> {
                        ConnectActivity.LocalPartyReference ref =
                                new ConnectActivity.LocalPartyReference(address, response, String.format("%s ms", time));
                        lanData.add(ref);
                        adapter.notifyItemInserted(lanData.indexOf(ref));
                    });
                }

                @Override
                public void onError(Exception e) {
                    UiHelper.runOnUiCompact(() -> {
                        v1.setVisibility(View.VISIBLE);
                        Snackbar.make(fab, "Discovery failed: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
                    });
                }

                @Override
                public void onTimeout(List<NetworkDiscovery.Endpoint> discovered) {
                    UiHelper.runOnUiCompact(() -> v1.setVisibility(View.VISIBLE));
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