package sk.martin64.partycast.ui.main;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.Executors;

import butterknife.BindView;
import butterknife.ButterKnife;
import partycast.model.Lobby;
import partycast.model.LobbyEventListener;
import partycast.model.LobbyMember;
import sk.martin64.partycast.R;
import sk.martin64.partycast.androidserver.AndroidServerLobby;
import sk.martin64.partycast.utils.LobbyCoordinatorService;

public class SettingsFragment extends Fragment implements LobbyEventListener {

    private RecyclerView rv;
    private Lobby lobby;
    private List<SettingsItem> settings;
    private SettingsAdapter adapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        rv = (RecyclerView) inflater.inflate(R.layout.fragment_home, container, false);

        lobby = LobbyCoordinatorService.getInstance().getActiveLobby();
        lobby.addEventListener(this);

        settings = new ArrayList<>();
        adapter = new SettingsAdapter(lobby, settings);

        settings.add(new SettingsItem("Lobby name", lobby.getTitle(), Lobby.checkPermission(lobby.getClient(), LobbyMember.PERMISSIONS_MOD), v -> {
            //lobby.changeTitle(inputLobbyName.getEditText().getText().toString(), null);
        }));

        if (lobby instanceof AndroidServerLobby) {
            settings.add(new SettingsItem("Server IP addresses", "Loading", false, v -> {}));

            Executors.newSingleThreadExecutor().submit(() -> {
                try {
                    StringBuilder builder = new StringBuilder();
                    Enumeration<NetworkInterface> netifenum = NetworkInterface.getNetworkInterfaces();
                    while (netifenum.hasMoreElements()) {
                        NetworkInterface netif = netifenum.nextElement();

                        Enumeration<InetAddress> addrenum = netif.getInetAddresses();
                        while (addrenum.hasMoreElements()) {
                            InetAddress addr = addrenum.nextElement();

                            if (addr.isLoopbackAddress()) continue;

                            builder.append(addr.getHostAddress()).append('\n');
                        }
                    }

                    rv.post(() -> {
                        settings.get(1).content = builder.toString();
                        adapter.notifyItemChanged(1);
                    });
                } catch (SocketException e) {
                    e.printStackTrace();
                }
            });
        }

        rv.setLayoutManager(new LinearLayoutManager(getContext(), RecyclerView.VERTICAL, false));
        rv.setItemAnimator(new DefaultItemAnimator());
        rv.setAdapter(adapter);

        return rv;
    }

    @Override
    public void onLobbyStateChanged(Lobby lobby) {
        if (rv != null) {
            settings.get(0).content = lobby.getTitle();
            adapter.notifyItemChanged(0);
        }
    }

    @Override
    public void onDestroy() {
        if (lobby != null) lobby.removeEventListener(this);
        super.onDestroy();
    }

    private static class SettingsItem {
        CharSequence title, content;
        boolean enabled;
        View.OnClickListener onClickListener;

        public SettingsItem(CharSequence title, CharSequence content, boolean enabled, View.OnClickListener onClickListener) {
            this.title = title;
            this.content = content;
            this.enabled = enabled;
            this.onClickListener = onClickListener;
        }
    }

    public static class SettingsAdapter extends RecyclerView.Adapter<SettingsAdapter.ViewHolder> {

        public static class ViewHolder extends RecyclerView.ViewHolder {
            @BindView(R.id.textView)
            TextView title;
            @BindView(R.id.textView6)
            TextView body;

            ViewHolder(View view) {
                super(view);
                ButterKnife.bind(this, view);
            }
        }

        private Lobby lobby;
        private List<SettingsItem> items;

        public SettingsAdapter(Lobby lobby, List<SettingsItem> items) {
            this.lobby = lobby;
            this.items = items;
        }

        @NonNull
        @Override
        public SettingsAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new SettingsAdapter.ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_setting, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull SettingsAdapter.ViewHolder holder, int position) {
            SettingsItem item = items.get(position);

            holder.title.setText(item.title);
            holder.body.setText(item.content);

            holder.itemView.setEnabled(item.enabled);
            holder.itemView.setOnClickListener(item.onClickListener);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }
}