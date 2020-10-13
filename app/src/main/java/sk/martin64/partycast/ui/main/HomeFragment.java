package sk.martin64.partycast.ui.main;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import sk.martin64.partycast.R;
import sk.martin64.partycast.client.ClientLobbyMember;
import sk.martin64.partycast.core.Lobby;
import sk.martin64.partycast.core.LobbyEventListener;
import sk.martin64.partycast.core.LobbyMember;
import sk.martin64.partycast.utils.LobbyCoordinatorService;

public class HomeFragment extends Fragment implements LobbyEventListener {

    @BindView(R.id.clients)
    RecyclerView clients;

    private Unbinder unbinder;
    private LobbyClientsAdapter adapter;

    private Lobby lobby;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_home, container, false);
        unbinder = ButterKnife.bind(this, root);

        lobby = LobbyCoordinatorService.getInstance().getActiveLobby();
        lobby.addEventListener(this);

        adapter = new LobbyClientsAdapter();
        clients.setLayoutManager(new LinearLayoutManager(getContext(), RecyclerView.VERTICAL, false));
        clients.setItemAnimator(new DefaultItemAnimator());
        clients.setAdapter(adapter);

        return root;
    }

    @Override
    public void onUserJoined(Lobby lobby, LobbyMember member) {
        new Handler(Looper.getMainLooper()).post(() ->
                adapter.notifyDataSetChanged());
    }

    @Override
    public void onUserLeft(Lobby lobby, LobbyMember member) {
        new Handler(Looper.getMainLooper()).post(() ->
                adapter.notifyDataSetChanged());
    }

    @Override
    public void onUserUpdated(Lobby lobby, LobbyMember member) {
        new Handler(Looper.getMainLooper()).post(() ->
                adapter.notifyDataSetChanged());
    }

    @Override
    public void onDestroy() {
        if (lobby != null)
            lobby.removeEventListener(this);
        unbinder.unbind();
        super.onDestroy();
    }

    public class LobbyClientsAdapter extends RecyclerView.Adapter<LobbyClientsAdapter.ViewHolder> {

        public class ViewHolder extends RecyclerView.ViewHolder {
            @BindView(R.id.textView)
            TextView name;
            @BindView(R.id.textView2)
            TextView info;
            @BindView(R.id.textView3)
            TextView address;
            @BindView(R.id.imageView2)
            ImageView icon;

            ViewHolder(View view) {
                super(view);
                ButterKnife.bind(this, view);
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_client, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            LobbyMember member = lobby.getMembers().get(position);

            holder.name.setText(member.getName());
            String add = member.getAddressString();
            holder.address.setText(!TextUtils.isEmpty(add) ? add : "");

            holder.icon.setImageResource(ClientLobbyMember.getAgentIcon(member));

            if (Lobby.isHost(member)) {
                holder.info.setText("Host");
                holder.icon.setImageResource(R.drawable.ic_round_cast_connected_24);
            } else if (Lobby.isSelf(member)) {
                holder.info.setText("You");
            } else {
                holder.info.setText(ClientLobbyMember.getAgentName(member));
            }

            if (Lobby.checkPermission(member, LobbyMember.PERMISSIONS_MOD)) {
                holder.info.append(" • Moderator");
            }

            /*PopupMenu menu = new PopupMenu(holder.itemView.getContext(), holder.itemView);
            MenuItem changeNameItem = menu.getMenu().add("Change name");
            changeNameItem.setEnabled(Lobby.isSelf(member) || Lobby.checkPermission(member.getContext().getClient(), LobbyMember.PERMISSION_MANAGE_USERS));
            changeNameItem.setOnMenuItemClickListener(item -> {
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setTitle("Change username");

                final EditText input = new EditText(getContext());
                input.setInputType(InputType.TYPE_CLASS_TEXT);
                input.setText(member.getName());
                builder.setView(input);

                builder.setPositiveButton("OK", (dialog, which) -> member.changeName(input.getText().toString(), null));
                builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

                builder.show();
                return true;
            });*/

            holder.itemView.setOnClickListener(v -> {
                /*menu.show();*/
                LobbyMemberDialog dialog = new LobbyMemberDialog(member);
                dialog.show(getParentFragmentManager(), "LobbyMember");
            });
        }

        @Override
        public int getItemCount() {
            return lobby != null ? lobby.getMembers().size() : 0;
        }
    }
}