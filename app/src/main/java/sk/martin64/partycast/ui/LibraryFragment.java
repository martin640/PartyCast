package sk.martin64.partycast.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import sk.martin64.partycast.R;
import sk.martin64.partycast.core.LibraryItem;
import sk.martin64.partycast.core.LibraryProvider;
import sk.martin64.partycast.core.Lobby;
import sk.martin64.partycast.core.LobbyEventListener;
import sk.martin64.partycast.utils.Callback;
import sk.martin64.partycast.utils.LobbyCoordinatorService;

public class LibraryFragment extends Fragment implements LobbyEventListener {

    @BindView(R.id.clients)
    RecyclerView clients;

    private Unbinder unbinder;
    private Lobby lobby;
    private LibraryAdapter adapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_home, container, false);
        unbinder = ButterKnife.bind(this, root);

        lobby = LobbyCoordinatorService.getInstance().getActiveLobby();
        lobby.addEventListener(this);

        adapter = new LibraryAdapter(lobby);
        clients.setLayoutManager(new LinearLayoutManager(getContext(), RecyclerView.VERTICAL, false));
        clients.setItemAnimator(new DefaultItemAnimator());
        clients.setAdapter(adapter);

        return root;
    }

    @Override
    public void onLibraryUpdated(Lobby lobby, LibraryProvider libraryProvider) {
        new Handler(Looper.getMainLooper()).post(() ->
                adapter.notifyDataSetChanged());
    }

    @Override
    public void onDestroy() {
        lobby.removeEventListener(this);
        unbinder.unbind();
        super.onDestroy();
    }

    public static class LibraryAdapter extends RecyclerView.Adapter<LibraryAdapter.ViewHolder> {

        public static class ViewHolder extends RecyclerView.ViewHolder {
            @BindView(R.id.textView)
            TextView title;
            @BindView(R.id.textView2)
            TextView artist;
            @BindView(R.id.textView6)
            TextView about;
            @BindView(R.id.textView9)
            TextView start;
            @BindView(R.id.imageView3)
            ImageView imageView;

            ViewHolder(View view) {
                super(view);
                ButterKnife.bind(this, view);
            }
        }

        private Lobby lobby;

        public LibraryAdapter(Lobby lobby) {
            this.lobby = lobby;
        }

        @NonNull
        @Override
        public LibraryAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new LibraryAdapter.ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_library_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull LibraryAdapter.ViewHolder holder, int position) {
            LibraryItem media = lobby.getLibrary().getAll().get(position);

            holder.title.setText(media.getTitle());
            holder.artist.setText(media.getArtist());
            holder.about.setText(media.getAlbum());

            holder.start.setText("");

            Glide.with(holder.imageView)
                    .load(media.getImageUrl())
                    .error(android.R.drawable.ic_delete)
                    .into(holder.imageView);

            holder.itemView.setOnClickListener(v -> {
                lobby.getLooper().enqueue(media, new Callback<Void>() {
                    @Override
                    public void onError(Exception e) {
                        new Handler(Looper.getMainLooper()).post(() ->
                                Toast.makeText(v.getContext(), "Error: " + e.getMessage(),
                                        Toast.LENGTH_SHORT).show());
                    }

                    @Override
                    public void onSuccess(Void aVoid) {
                        new Handler(Looper.getMainLooper()).post(() ->
                                Toast.makeText(v.getContext(),
                                        media.getTitle() + " has been added to queue",
                                        Toast.LENGTH_SHORT).show());
                    }
                });
            });
        }

        @Override
        public int getItemCount() {
            return lobby.getLibrary().getAll().size();
        }
    }
}