package sk.martin64.partycast.ui.main;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.List;

import partycast.model.ActionBoardItem;
import partycast.model.Lobby;
import partycast.model.LobbyEventListener;
import sk.martin64.partycast.R;
import sk.martin64.partycast.ui.UiHelper;
import sk.martin64.partycast.utils.LobbyCoordinatorService;

public class BoardFragment extends Fragment implements LobbyEventListener {

    private RecyclerView rv;
    private Lobby lobby;
    private BoardAdapter adapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        rv = (RecyclerView) inflater.inflate(R.layout.fragment_home, container, false);

        lobby = LobbyCoordinatorService.getInstance().getActiveLobby();
        lobby.addEventListener(this);

        adapter = new BoardAdapter(lobby);

        rv.setLayoutManager(new LinearLayoutManager(getContext(), RecyclerView.VERTICAL, false));
        rv.setItemAnimator(new DefaultItemAnimator());
        rv.setAdapter(adapter);

        return rv;
    }

    @Override
    public void onLobbyStateChanged(Lobby lobby) {
        if (rv != null) {
            UiHelper.runOnUiCompact(() -> {
                adapter.update();
                adapter.notifyDataSetChanged();
            });
        }
    }

    @Override
    public void onDestroy() {
        if (lobby != null) lobby.removeEventListener(this);
        super.onDestroy();
    }

    public static class BoardAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final Lobby lobby;
        private final List<ActionBoardItem> items;

        public BoardAdapter(Lobby lobby) {
            this.lobby = lobby;
            this.items = new ArrayList<>();
            update();
        }

        public void update() {
            this.items.clear();
            this.items.addAll(lobby.getBoard());
        }

        private static class HeaderHolder extends RecyclerView.ViewHolder {
            final TextView text;
            public HeaderHolder(@NonNull View itemView) {
                super(itemView);
                text = (TextView) itemView;
            }
        }
        private static class HtmlHolder extends RecyclerView.ViewHolder {
            final TextView text;
            public HtmlHolder(@NonNull View itemView) {
                super(itemView);
                text = (TextView) itemView;
            }
        }
        private static class ButtonHolder extends RecyclerView.ViewHolder {
            final Button button;
            public ButtonHolder(@NonNull View itemView) {
                super(itemView);
                button = itemView.findViewById(R.id.button);
            }
        }
        private static class OptionHolder extends RecyclerView.ViewHolder {
            final TextView title, body;
            final SwitchMaterial toggle;
            public OptionHolder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.textView);
                body = itemView.findViewById(R.id.textView6);
                toggle = itemView.findViewById(R.id.switch6);
            }
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position).getItemType();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater i = LayoutInflater.from(parent.getContext());
            if (viewType == ActionBoardItem.BOARD_ITEM_TYPE_SECTION_TITLE) {
                return new HeaderHolder(i.inflate(R.layout.item_board_title, parent, false));
            } else if (viewType == ActionBoardItem.BOARD_ITEM_TYPE_HTML) {
                return new HtmlHolder(i.inflate(R.layout.item_board_html, parent, false));
            } else if (viewType == ActionBoardItem.BOARD_ITEM_TYPE_BUTTON) {
                return new ButtonHolder(i.inflate(R.layout.item_board_button, parent, false));
            } else if (viewType == ActionBoardItem.BOARD_ITEM_TYPE_OPTION) {
                return new OptionHolder(i.inflate(R.layout.item_board_option, parent, false));
            }

            throw new IllegalStateException("Unknown view type");
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ActionBoardItem item = items.get(position);

            if (holder instanceof HeaderHolder) {
                HeaderHolder h = (HeaderHolder) holder;
                h.text.setText(item.getTitle());
            } else if (holder instanceof HtmlHolder) {
                HtmlHolder h = (HtmlHolder) holder;
                UiHelper.html(h.text, item.getBody(), (view, url) -> {
                    if (url.startsWith("partycast://"))
                        item.submit(url, null);
                    else {
                        Toast.makeText(view.getContext(), "Link blocked", Toast.LENGTH_SHORT).show();
                    }
                });
            } else if (holder instanceof ButtonHolder) {
                ButtonHolder h = (ButtonHolder) holder;
                h.button.setText(item.getBody());
                h.button.setEnabled(item.isClickable());
                h.button.setOnClickListener((v) -> item.submit(null, null));
            } else if (holder instanceof OptionHolder) {
                OptionHolder h = (OptionHolder) holder;
                h.title.setText(item.getTitle());
                h.body.setText(item.getBody());
                h.toggle.setVisibility(item.getInputType() == ActionBoardItem.BOARD_ITEM_INPUT_TOGGLE ? View.VISIBLE : View.GONE);
                h.itemView.setEnabled(item.isClickable());
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }
}