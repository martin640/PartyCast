package sk.martin64.partycast.ui.connect;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.navigation.Navigation;

import java.io.PrintWriter;
import java.io.StringWriter;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import partycast.model.Lobby;
import sk.martin64.partycast.BuildConfig;
import sk.martin64.partycast.ConnectActivity;
import sk.martin64.partycast.MainActivity;
import sk.martin64.partycast.R;
import sk.martin64.partycast.utils.Callback;

@SuppressLint("NonConstantResourceId")
public class ConnectMainFragment extends Fragment {

    @BindView(R.id.action1)
    View action1;
    @BindView(R.id.action2)
    View action2;
    @BindView(R.id.action3)
    View action3;

    Unbinder unbinder;
    private ConnectActivity activity;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_connect_main, container, false);
        unbinder = ButterKnife.bind(this, v);
        activity = (ConnectActivity) getActivity();

        FragmentManager fm = getParentFragmentManager();
        action1.setOnClickListener(v1 -> {
            activity.coordinatorService.createServer(activity, new Callback<Lobby>() {
                @Override
                public void onError(Exception e) {
                    if (!activity.isDestroyed()) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                        builder.setTitle("Failed to create server");
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
        action2.setOnClickListener(v1 ->
                Navigation.findNavController(v1).navigate(R.id.navigation_connect_manually));
        action3.setOnClickListener(v1 ->
                Navigation.findNavController(v1).navigate(R.id.navigation_connect_scan));

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        activity.toolbar.setVisibility(View.GONE);
    }
    @Override
    public void onStop() {
        super.onStop();
        activity.toolbar.setVisibility(View.VISIBLE);
    }
}
