package sk.martin64.partycast.ui.main;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.material.textfield.TextInputLayout;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.concurrent.Executors;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import sk.martin64.partycast.R;
import sk.martin64.partycast.core.Lobby;
import sk.martin64.partycast.core.LobbyEventListener;
import sk.martin64.partycast.utils.LobbyCoordinatorService;

public class SettingsFragment extends Fragment implements LobbyEventListener {

    @BindView(R.id.input_lobby_name)
    TextInputLayout inputLobbyName;
    @BindView(R.id.button3)
    Button button3;
    @BindView(R.id.tv_ip_addresses)
    TextView tvIpAddresses;

    private Unbinder unbinder;
    private Lobby lobby;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_settings, container, false);
        unbinder = ButterKnife.bind(this, root);

        lobby = LobbyCoordinatorService.getInstance().getActiveLobby();
        lobby.addEventListener(this);

        inputLobbyName.getEditText().setText(lobby.getTitle());

        button3.setOnClickListener((v) -> {
            lobby.changeTitle(inputLobbyName.getEditText().getText().toString(), null);
        });

        tvIpAddresses.setText("Loading IP address list...");
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

                tvIpAddresses.post(() -> tvIpAddresses.setText("Device IP addresses:\n\n" + builder.toString()));
            } catch (SocketException e) {
                e.printStackTrace();
            }
        });

        return root;
    }

    @Override
    public void onLobbyStateChanged(Lobby lobby) {
        if (inputLobbyName != null) {
            inputLobbyName.getEditText().setText(lobby.getTitle());
        }
    }

    @Override
    public void onDestroy() {
        unbinder.unbind();
        lobby.removeEventListener(this);
        super.onDestroy();
    }
}