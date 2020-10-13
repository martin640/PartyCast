package sk.martin64.partycast.ui.main;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import sk.martin64.partycast.R;
import sk.martin64.partycast.core.LibraryItem;
import sk.martin64.partycast.core.LibraryProvider;
import sk.martin64.partycast.core.Lobby;
import sk.martin64.partycast.core.LobbyEventListener;
import sk.martin64.partycast.server.LocalLibraryItem;
import sk.martin64.partycast.ui.UiHelper;
import sk.martin64.partycast.utils.Callback;
import sk.martin64.partycast.utils.LobbyCoordinatorService;

public class LibraryFragment extends Fragment implements LobbyEventListener {

    @BindView(R.id.clients)
    RecyclerView clients;

    private Unbinder unbinder;
    private Lobby lobby;
    private LibraryAdapter adapter;

    private List<LibraryItem> libraryItems;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_home, container, false);
        unbinder = ButterKnife.bind(this, root);

        lobby = LobbyCoordinatorService.getInstance().getActiveLobby();
        lobby.addEventListener(this);

        adapter = new LibraryAdapter(lobby, libraryItems = new ArrayList<>());
        clients.setLayoutManager(new LinearLayoutManager(getContext(), RecyclerView.VERTICAL, false));
        clients.setItemAnimator(new DefaultItemAnimator());
        clients.setAdapter(adapter);

        Executors.newSingleThreadExecutor().submit(() -> onLibraryUpdated(lobby, lobby.getLibrary()));

        return root;
    }

    @Override
    public void onLibraryUpdated(Lobby lobby, LibraryProvider libraryProvider) {
        // obtain copy of library on background thread because LibraryProvider usually holds a lock which may block UI thread
        List<LibraryItem> data = libraryProvider.getAll();
        new Handler(Looper.getMainLooper()).post(() -> {
            libraryItems.clear();
            libraryItems.addAll(data);
            adapter.notifyDataSetChanged();
        });
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
            @BindView(R.id.imageView3)
            ImageView imageView;

            ViewHolder(View view) {
                super(view);
                ButterKnife.bind(this, view);
            }
        }

        private Lobby lobby;
        private List<LibraryItem> libraryItems;

        public LibraryAdapter(Lobby lobby, List<LibraryItem> libraryItems) {
            this.lobby = lobby;
            this.libraryItems = libraryItems;
        }

        @NonNull
        @Override
        public LibraryAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new LibraryAdapter.ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_library_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull LibraryAdapter.ViewHolder holder, int position) {
            LibraryItem media = libraryItems.get(position);

            holder.title.setText(media.getTitle());
            holder.artist.setText(media.getArtist());
            holder.about.setText(media.getAlbum());

            Glide.with(holder.imageView)
                    .load(media.getImageUrl())
                    .error(R.drawable.ic_no_artwork)
                    .into(holder.imageView);

            holder.itemView.setOnClickListener(v -> {
                lobby.getLooper().enqueue(media, new Callback<Void>() {
                    @Override
                    public void onError(Exception e) {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        e.printStackTrace(new PrintStream(baos));
                        String stackTrace = new String(baos.toByteArray(), StandardCharsets.UTF_8);

                        String uri = "N/A", path = "N/A";
                        boolean fileExists = false;
                        if (media instanceof LocalLibraryItem) {
                            LocalLibraryItem i = (LocalLibraryItem) media;
                            path = i.getPath();
                            fileExists = new File(path).exists();
                            if (i.getUri() != null)
                                uri = i.getUri().toString();
                        }

                        String finalPath = path;
                        String finalUri = uri;
                        boolean finalFileExists = fileExists;
                        new Handler(Looper.getMainLooper()).post(() -> {
                            AlertDialog.Builder builder = new AlertDialog.Builder(holder.imageView.getContext());
                            builder.setTitle("Exception thrown while requesting media");
                            builder.setMessage(TextUtils.concat(
                                    "LibraryItem path: ", UiHelper.span(finalPath, new ForegroundColorSpan(0x66000000)),
                                    "\nLibraryItem path exists: ", UiHelper.span(Boolean.toString(finalFileExists), new ForegroundColorSpan(0x66000000)),
                                    "\nLibraryItem image uri: ", UiHelper.span(finalUri, new ForegroundColorSpan(0x66000000)),
                                    "\n\nException:\n\n", stackTrace));
                            builder.setPositiveButton("OK", (dialog, which) -> dialog.dismiss());
                            builder.show();
                        });
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
            return libraryItems.size();
        }
    }
}