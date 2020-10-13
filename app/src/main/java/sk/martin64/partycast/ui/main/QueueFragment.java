package sk.martin64.partycast.ui.main;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

import com.bumptech.glide.Glide;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import sk.martin64.partycast.R;
import partycast.model.Lobby;
import partycast.model.LobbyEventListener;
import partycast.model.Queue;
import partycast.model.QueueLooper;
import partycast.model.RemoteMedia;
import sk.martin64.partycast.utils.LobbyCoordinatorService;

public class QueueFragment extends Fragment implements LobbyEventListener {

    @BindView(R.id.clients)
    RecyclerView clients;

    private Unbinder unbinder;
    private Lobby lobby;
    private QueueAdapter adapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_home, container, false);
        unbinder = ButterKnife.bind(this, root);

        lobby = LobbyCoordinatorService.getInstance().getActiveLobby();
        lobby.addEventListener(this);

        adapter = new QueueAdapter();
        clients.setLayoutManager(new LinearLayoutManager(getContext(), RecyclerView.VERTICAL, false));
        clients.setItemAnimator(new DefaultItemAnimator());
        clients.setAdapter(adapter);

        return root;
    }

    @Override
    public void onLobbyStateChanged(Lobby lobby) {
        new Handler(Looper.getMainLooper()).post(() ->
                adapter.notifyDataSetChanged());
    }

    @Override
    public void onLooperUpdated(Lobby lobby, QueueLooper looper) {
        new Handler(Looper.getMainLooper()).post(() ->
                adapter.notifyDataSetChanged());
    }

    @Override
    public void onDestroy() {
        unbinder.unbind();
        super.onDestroy();
    }

    public class QueueAdapter extends RecyclerView.Adapter<QueueAdapter.ViewHolder> {

        public class ViewHolder extends RecyclerView.ViewHolder {
            @BindView(R.id.textView)
            TextView title;
            @BindView(R.id.textView2)
            TextView artist;
            @BindView(R.id.textView6)
            TextView about;
            @BindView(R.id.textView9)
            TextView start;
            @BindView(R.id.imageView3)
            ImageView artwork;
            @BindView(R.id.textView7)
            TextView round;
            @BindView(R.id.divider)
            View divider;

            ViewHolder(View view) {
                super(view);
                ButterKnife.bind(this, view);
            }
        }

        private List<RemoteMedia> all = new ArrayList<>();

        private void reload() {
            all.clear();
            for (Queue queue : lobby.getLooper().getPending()) {
                all.addAll(queue.getPending());
            }
        }

        @NonNull
        @Override
        public QueueAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new QueueAdapter.ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_media, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull QueueAdapter.ViewHolder holder, int position) {
            RemoteMedia media = all.get(position);

            holder.title.setText(media.getTitle());
            holder.artist.setText(media.getArtist());
            holder.about.setText(String.format("Requested by %s", media.getRequester().getName()));

            Glide.with(holder.artwork)
                    .load(media.getArtwork())
                    .error(R.drawable.ic_no_artwork)
                    .into(holder.artwork);

            if (media.getId() == 0 || position == 0) {
                holder.round.setText("Round " + media.getQueue().getId());
                holder.round.setVisibility(View.VISIBLE);
                holder.divider.setVisibility(View.VISIBLE);
            } else {
                holder.round.setVisibility(View.GONE);
                holder.divider.setVisibility(View.GONE);
            }

            long start = media.getStartTime();
            holder.start.setText(start > 0 ? SimpleDateFormat.getTimeInstance().format(new Date(start)) : "--:--");
        }

        @Override
        public int getItemCount() {
            reload();
            return all.size();
        }
    }
}